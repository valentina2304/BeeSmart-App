package com.example.beesmart.network

import android.util.Log
import com.example.beesmart.BuildConfig
import com.example.beesmart.utils.NetworkConfig
import com.example.beesmart.utils.SessionManager
import com.example.beesmart.network.models.RefreshRequest
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Interceptor for automatic token management.
 *
 * Responsibilities:
 * 1. Adds the Authorization header to all requests
 * 2. Checks whether the token has expired before sending the request
 * 3. Automatically refreshes the token if needed
 * 4. Retries the request with the new token
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
    private val reachability: BackendReachability
) : Interceptor {

    // Mutex to prevent concurrent token refresh attempts
    private val refreshMutex = Mutex()

    // Circuit breaker: after a failed refresh, skip subsequent refresh attempts
    // for REFRESH_BACKOFF_MS so the app fails fast and falls back to Room cache.
    @Volatile private var lastRefreshFailureMs: Long = 0L

    // Lazy initialization of the refresh API client to avoid circular dependency
    private val refreshAuthApi: AuthApi by lazy {
        createRefreshAuthApi()
    }

    companion object {
        private const val TAG = "AuthInterceptor"
        private const val REFRESH_BACKOFF_MS = 30_000L

        private val UNAUTHENTICATED_AUTH_PATHS = listOf(
            "/auth/login",
            "/auth/register",
            "/auth/refresh",
            "/auth/forgot-password",
            "/auth/reset-password",
            "/auth/confirm-email",
            "/auth/resend-confirmation"
        )
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        // Skip only the auth endpoints that do NOT use Authorization
        if (UNAUTHENTICATED_AUTH_PATHS.any { path.endsWith(it) || path.contains(it) }) {
            return chain.proceed(request)
        }

        return runBlocking {
            try {
                // Get the current token
                var accessToken = sessionManager.getAccessToken()

                // Check whether the token has expired
                if (sessionManager.isTokenExpired()) {
                    val sinceFailure = System.currentTimeMillis() - lastRefreshFailureMs
                    if (sinceFailure < REFRESH_BACKOFF_MS) {
                        Log.d(TAG, "Skipping refresh — recent failure ${sinceFailure}ms ago; proceeding with expired token")
                    } else {
                        Log.d(TAG, "Token expired, attempting refresh...")

                        // Use mutex to ensure only one thread refreshes at a time
                        refreshMutex.withLock {
                            // Double-check if token is still expired (another thread might have refreshed it)
                            if (sessionManager.isTokenExpired()) {
                                // Attempt to refresh the token
                                val refreshed = refreshTokenSync()

                                if (refreshed) {
                                    // Token refreshed successfully - get the new token
                                    accessToken = sessionManager.getAccessToken()
                                    lastRefreshFailureMs = 0L
                                    Log.d(TAG, "Token refreshed successfully")
                                } else {
                                    // Refresh failed - the token will be null or expired
                                    lastRefreshFailureMs = System.currentTimeMillis()
                                    Log.w(TAG, "Token refresh failed — backing off for ${REFRESH_BACKOFF_MS}ms")
                                }
                            } else {
                                // Token already refreshed by another thread
                                accessToken = sessionManager.getAccessToken()
                                Log.d(TAG, "Token already refreshed by another thread")
                            }
                        }
                    }
                }

                // Build the request with the token (new or old)
                val authenticatedRequest = if (!accessToken.isNullOrEmpty()) {
                    request.newBuilder()
                        .header("Authorization", "Bearer $accessToken")
                        .build()
                } else {
                    request
                }

                // Execute the request
                val response = chain.proceed(authenticatedRequest)

                // A 401 means the token is invalid
                if (response.code == 401) {
                    Log.w(TAG, "Received 401 - token invalid or expired")

                    // Attempt a refresh and retry the request
                    response.close() // Close the previous response

                    refreshMutex.withLock {
                        // Double-check if another thread already refreshed the token
                        val currentToken = sessionManager.getAccessToken()
                        if (currentToken != accessToken && !currentToken.isNullOrEmpty() && !sessionManager.isTokenExpired()) {
                            // Token was already refreshed by another thread
                            Log.d(TAG, "Token already refreshed by another thread, retrying with new token")
                            val retryRequest = request.newBuilder()
                                .header("Authorization", "Bearer $currentToken")
                                .build()
                            return@runBlocking chain.proceed(retryRequest)
                        }

                        // Need to refresh the token
                        if (refreshTokenSync()) {
                            val newAccessToken = sessionManager.getAccessToken()

                            if (!newAccessToken.isNullOrEmpty()) {
                                val retryRequest = request.newBuilder()
                                    .header("Authorization", "Bearer $newAccessToken")
                                    .build()

                                Log.d(TAG, "Retrying request with new token")
                                return@runBlocking chain.proceed(retryRequest)
                            }
                        }

                        // If the refresh failed, clear the tokens
                        Log.e(TAG, "Cannot refresh token - clearing session")
                        sessionManager.clearTokens()
                    }
                }

                response
            } catch (e: Exception) {
                Log.e(TAG, "Error in AuthInterceptor", e)
                throw e
            }
        }
    }

    /**
     * Refreshes the token synchronously.
     * @return true if the refresh succeeded, false otherwise
     */
    private suspend fun refreshTokenSync(): Boolean {
        return try {
            val refreshToken = sessionManager.getRefreshToken()

            if (refreshToken.isNullOrEmpty()) {
                Log.w(TAG, "No refresh token available")
                return false
            }

            Log.d(TAG, "Calling refresh endpoint...")
            val response = refreshAuthApi.refresh(RefreshRequest(refreshToken))

            if (response.isSuccessful) {
                val body = response.body()

                if (body?.accessToken != null && body.refreshToken != null) {
                    // Save the new tokens
                    sessionManager.saveTokens(
                        body.accessToken,
                        body.refreshToken,
                        body.expiresIn?.toLong() ?: 900L
                    )

                    Log.d(TAG, "Tokens refreshed and saved")
                    true
                } else {
                    Log.w(TAG, "Refresh response missing tokens")
                    false
                }
            } else {
                Log.w(TAG, "Refresh failed with code: ${response.code()}")

                // A 401 on refresh means the refresh token is invalid
                if (response.code() == 401) {
                    sessionManager.clearTokens()
                }

                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception during token refresh", e)
            false
        }
    }

    /**
     * Creates a dedicated AuthApi client for token refresh operations.
     * This client does NOT include the AuthInterceptor to avoid circular dependency.
     */
    private fun createRefreshAuthApi(): AuthApi {
        Log.d(TAG, "Creating refresh AuthApi with baseUrl: ${NetworkConfig.baseUrl}")
        Log.d(TAG, "NetworkConfig debug info:\n${NetworkConfig.getDebugInfo()}")

        val moshi = Moshi.Builder()
            .add(KotlinJsonAdapterFactory())
            .build()

        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        }

        val okHttpClient = createUnsafeOkHttpClient(logging)

        val retrofit = Retrofit.Builder()
            .baseUrl(NetworkConfig.baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

        return retrofit.create(AuthApi::class.java)
    }

    /**
     * Creates an OkHttpClient that trusts self-signed certificates.
     * ⚠️ WARNING: This is for DEVELOPMENT only!
     */
    private fun createUnsafeOkHttpClient(loggingInterceptor: HttpLoggingInterceptor): OkHttpClient {
        try {
            val builder = OkHttpClient.Builder()
                .addInterceptor(ReachabilityInterceptor(reachability))
                .addInterceptor(loggingInterceptor)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .protocols(listOf(Protocol.HTTP_1_1))

            if (BuildConfig.DEBUG) {
                val trustAllCerts = arrayOf<TrustManager>(
                    object : X509TrustManager {
                        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                    }
                )

                val sslContext = SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, SecureRandom())
                builder.sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                builder.hostnameVerifier { _, _ -> true }
            }

            return builder.build()
        } catch (e: Exception) {
            throw RuntimeException("Failed to create dev SSL client", e)
        }
    }
}

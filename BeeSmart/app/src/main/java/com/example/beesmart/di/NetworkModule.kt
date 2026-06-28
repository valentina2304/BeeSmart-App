package com.example.beesmart.di

import com.example.beesmart.BuildConfig
import com.example.beesmart.network.*
import com.example.beesmart.network.BackendReachability
import com.example.beesmart.network.ReachabilityInterceptor
import com.example.beesmart.utils.NetworkConfig
import com.example.beesmart.utils.SessionManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi {
        return Moshi.Builder()
            .add(HiveTypeAdapter())
            .add(HiveStatusAdapter())
            .add(TaskPriorityAdapter())
            .add(TaskStatusAdapter())
            .add(TreatmentTypeAdapter())
            .add(ExtractionTypeAdapter())
            .add(KotlinJsonAdapterFactory())
            .build()
    }

    @Provides
    @Singleton
    fun provideHttpLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.HEADERS
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
            redactHeader("Cookie")
            redactHeader("Set-Cookie")
        }
    }

    @Provides
    @Singleton
    @UnauthenticatedClient
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor
    ): OkHttpClient {
        return createOkHttpClient(loggingInterceptor, null, null)
    }

    @Provides
    @Singleton
    @AuthenticatedClient
    fun provideAuthenticatedOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        authInterceptor: AuthInterceptor,
        reachabilityInterceptor: ReachabilityInterceptor
    ): OkHttpClient {
        return createOkHttpClient(loggingInterceptor, authInterceptor, reachabilityInterceptor)
    }

    @Provides
    @Singleton
    @UnauthenticatedClient
    fun provideRetrofit(
        @UnauthenticatedClient okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(NetworkConfig.baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    @AuthenticatedClient
    fun provideAuthenticatedRetrofit(
        @AuthenticatedClient okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(NetworkConfig.baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides
    @Singleton
    @UnauthenticatedClient
    fun provideAuthApi(@UnauthenticatedClient retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    @AuthenticatedClient
    fun provideAuthenticatedAuthApi(@AuthenticatedClient retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        sessionManager: SessionManager,
        reachability: BackendReachability
    ): AuthInterceptor {
        return AuthInterceptor(sessionManager, reachability)
    }

    @Provides @Singleton
    fun provideApiaryApi(@AuthenticatedClient retrofit: Retrofit): ApiaryApi =
        retrofit.create(ApiaryApi::class.java)

    @Provides @Singleton
    fun provideHiveApi(@AuthenticatedClient retrofit: Retrofit): HiveApi =
        retrofit.create(HiveApi::class.java)

    @Provides @Singleton
    fun provideTaskApi(@AuthenticatedClient retrofit: Retrofit): TaskApi =
        retrofit.create(TaskApi::class.java)

    @Provides @Singleton
    fun provideInspectionApi(@AuthenticatedClient retrofit: Retrofit): InspectionApi =
        retrofit.create(InspectionApi::class.java)

    @Provides @Singleton
    fun provideTreatmentApi(@AuthenticatedClient retrofit: Retrofit): TreatmentApi =
        retrofit.create(TreatmentApi::class.java)

    @Provides @Singleton
    fun provideExtractionApi(@AuthenticatedClient retrofit: Retrofit): ExtractionApi =
        retrofit.create(ExtractionApi::class.java)

    // ==================== OpenWeatherMap ====================
    // Public REST API at api.openweathermap.org. Auth is via the `appid` query
    // parameter — no AuthInterceptor needed. We reuse the unauthenticated
    // OkHttp client so a weather request can't trip the BeeSmart-backend
    // ReachabilityInterceptor circuit breaker.

    @Provides
    @Singleton
    @OpenWeatherClient
    fun provideOpenWeatherRetrofit(
        @UnauthenticatedClient okHttpClient: OkHttpClient,
        moshi: Moshi
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
    }

    @Provides @Singleton
    fun provideWeatherApi(@OpenWeatherClient retrofit: Retrofit): WeatherApi =
        retrofit.create(WeatherApi::class.java)
}

private fun createOkHttpClient(
    loggingInterceptor: HttpLoggingInterceptor,
    authInterceptor: AuthInterceptor?,
    reachabilityInterceptor: ReachabilityInterceptor?
): OkHttpClient {
    val builder = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .protocols(listOf(Protocol.HTTP_1_1))

    builder.addInterceptor(AiAnalysisTimeoutInterceptor())

    if (BuildConfig.DEBUG) {
        // Dev only: trust self-signed certs against the local API.
        try {
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
        } catch (e: Exception) {
            throw RuntimeException("Failed to create dev SSL client", e)
        }
    }

    // Reachability check runs first: short-circuits when backend recently failed.
    // Not added to the unauthenticated client so login/register always attempt the network.
    reachabilityInterceptor?.let { builder.addInterceptor(it) }

    // Add auth interceptor (application interceptor)
    authInterceptor?.let { builder.addInterceptor(it) }

    // Add logging as network interceptor (sees final request/response after all transformations)
    if (BuildConfig.DEBUG) {
        builder.addNetworkInterceptor(loggingInterceptor)
    }

    return builder.build()
}

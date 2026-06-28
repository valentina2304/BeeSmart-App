package com.example.beesmart.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Short-circuits API calls while [BackendReachability] is in the
 * unreachable window. Throwing an IOException lets repositories catch
 * and fall through to Room immediately, without paying connect timeouts.
 */
@Singleton
class ReachabilityInterceptor @Inject constructor(
    private val reachability: BackendReachability
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        if (reachability.isLikelyUnreachable()) {
            throw IOException("Backend marked unreachable; failing fast for offline cache")
        }
        return try {
            val response = chain.proceed(chain.request())
            if (response.isSuccessful) reachability.markReachable()
            response
        } catch (e: IOException) {
            if (!chain.request().url.encodedPath.endsWith("/inspections/analyze-cells")) {
                reachability.markUnreachable()
            }
            throw e
        }
    }
}

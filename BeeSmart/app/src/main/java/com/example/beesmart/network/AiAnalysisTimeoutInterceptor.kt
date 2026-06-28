package com.example.beesmart.network

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/**
 * AI image analysis can take longer in hosted environments because the model may
 * cold-start or run on limited CPU. Keep the regular API snappy, but give this
 * endpoint enough time to finish.
 */
class AiAnalysisTimeoutInterceptor : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        if (!path.endsWith("/inspections/analyze-cells")) {
            return chain.proceed(request)
        }

        return chain
            .withConnectTimeout(AI_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .withReadTimeout(AI_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .withWriteTimeout(AI_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .proceed(request)
    }

    private companion object {
        private const val AI_TIMEOUT_SECONDS = 180
    }
}

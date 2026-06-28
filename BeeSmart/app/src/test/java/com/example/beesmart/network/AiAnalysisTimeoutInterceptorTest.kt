package com.example.beesmart.network

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class AiAnalysisTimeoutInterceptorTest {

    @Test
    fun `non ai requests proceed without changing timeouts`() {
        val request = request("/api/apiaries")
        val response = response(request)
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } returns response

        val result = AiAnalysisTimeoutInterceptor().intercept(chain)

        assertEquals(200, result.code)
        verify(exactly = 0) { chain.withReadTimeout(any(), any()) }
        verify(exactly = 0) { chain.withConnectTimeout(any(), any()) }
        verify(exactly = 0) { chain.withWriteTimeout(any(), any()) }
    }

    @Test
    fun `ai analysis requests get extended timeouts`() {
        val request = request("/api/inspections/analyze-cells")
        val response = response(request)
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.withConnectTimeout(180, TimeUnit.SECONDS) } returns chain
        every { chain.withReadTimeout(180, TimeUnit.SECONDS) } returns chain
        every { chain.withWriteTimeout(180, TimeUnit.SECONDS) } returns chain
        every { chain.proceed(request) } returns response

        val result = AiAnalysisTimeoutInterceptor().intercept(chain)

        assertEquals(200, result.code)
        verify { chain.withConnectTimeout(180, TimeUnit.SECONDS) }
        verify { chain.withReadTimeout(180, TimeUnit.SECONDS) }
        verify { chain.withWriteTimeout(180, TimeUnit.SECONDS) }
    }

    private fun request(path: String): Request =
        Request.Builder()
            .url("https://example.test$path")
            .build()

    private fun response(request: Request): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .build()
}

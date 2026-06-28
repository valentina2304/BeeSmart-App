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
import java.io.IOException

class ReachabilityInterceptorTest {

    @Test(expected = IOException::class)
    fun `short circuits when backend is already marked unreachable`() {
        val reachability = mockk<BackendReachability>()
        val chain = mockk<Interceptor.Chain>()
        every { reachability.isLikelyUnreachable() } returns true

        ReachabilityInterceptor(reachability).intercept(chain)
    }

    @Test
    fun `successful response marks backend reachable`() {
        val request = request("/api/apiaries")
        val response = response(request, 200)
        val reachability = mockk<BackendReachability>(relaxed = true)
        val chain = mockk<Interceptor.Chain>()
        every { reachability.isLikelyUnreachable() } returns false
        every { chain.request() } returns request
        every { chain.proceed(request) } returns response

        val result = ReachabilityInterceptor(reachability).intercept(chain)

        assertEquals(200, result.code)
        verify { reachability.markReachable() }
    }

    @Test(expected = IOException::class)
    fun `io exception on normal endpoint marks backend unreachable`() {
        val request = request("/api/apiaries")
        val reachability = mockk<BackendReachability>(relaxed = true)
        val chain = mockk<Interceptor.Chain>()
        every { reachability.isLikelyUnreachable() } returns false
        every { chain.request() } returns request
        every { chain.proceed(request) } throws IOException("boom")

        try {
            ReachabilityInterceptor(reachability).intercept(chain)
        } finally {
            verify { reachability.markUnreachable() }
        }
    }

    @Test(expected = IOException::class)
    fun `io exception on ai analysis endpoint does not mark backend unreachable`() {
        val request = request("/api/inspections/analyze-cells")
        val reachability = mockk<BackendReachability>(relaxed = true)
        val chain = mockk<Interceptor.Chain>()
        every { reachability.isLikelyUnreachable() } returns false
        every { chain.request() } returns request
        every { chain.proceed(request) } throws IOException("ai timeout")

        try {
            ReachabilityInterceptor(reachability).intercept(chain)
        } finally {
            verify(exactly = 0) { reachability.markUnreachable() }
        }
    }

    private fun request(path: String): Request =
        Request.Builder()
            .url("https://example.test$path")
            .build()

    private fun response(request: Request, code: Int): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("OK")
            .build()
}

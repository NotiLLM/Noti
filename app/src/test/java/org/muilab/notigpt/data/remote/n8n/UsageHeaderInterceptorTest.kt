package org.muilab.notigpt.data.remote.n8n

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageHeaderInterceptorTest {

    private val interceptor = UsageHeaderInterceptor()

    @Test
    fun `strips the leading slash and webhook prefix`() {
        assertEquals("extract-a-scan", interceptor.stageFromPath("/webhook/extract-a-scan"))
    }

    @Test
    fun `leaves a path with no webhook prefix untouched aside from the leading slash`() {
        assertEquals("some-other-path", interceptor.stageFromPath("/some-other-path"))
    }
}

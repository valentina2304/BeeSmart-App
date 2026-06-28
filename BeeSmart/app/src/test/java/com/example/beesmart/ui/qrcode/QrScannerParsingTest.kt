package com.example.beesmart.ui.qrcode

import com.example.beesmart.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class QrScannerParsingTest {

    @Test
    fun `extracts hive id from configured https deep link`() {
        val raw = "${BuildConfig.DEEP_LINK_SCHEME}://${BuildConfig.DEEP_LINK_HOST}/hive/server-123"

        assertEquals("server-123", extractHiveId(raw))
    }

    @Test
    fun `extracts hive id from custom scheme link`() {
        val raw = "${BuildConfig.CUSTOM_SCHEME}://hive/local-123"

        assertEquals("local-123", extractHiveId(raw))
    }

    @Test
    fun `extracts hive id from plain text payload containing hive path`() {
        assertEquals("abc-123", extractHiveId("qr:hive/abc-123"))
    }

    @Test
    fun `trims payload before parsing`() {
        val raw = "  ${BuildConfig.CUSTOM_SCHEME}://hive/trimmed-id  "

        assertEquals("trimmed-id", extractHiveId(raw))
    }

    @Test
    fun `rejects links for other hosts or missing ids`() {
        assertNull(extractHiveId("https://example.com/hive/server-123"))
        assertNull(extractHiveId("${BuildConfig.DEEP_LINK_SCHEME}://${BuildConfig.DEEP_LINK_HOST}/hive"))
        assertNull(extractHiveId("${BuildConfig.CUSTOM_SCHEME}://apiary/local-123"))
        assertNull(extractHiveId("not a BeeSmart payload"))
    }
}

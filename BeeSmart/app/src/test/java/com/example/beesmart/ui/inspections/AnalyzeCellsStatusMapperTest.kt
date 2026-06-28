package com.example.beesmart.ui.inspections

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzeCellsStatusMapperTest {

    @Test
    fun `uses service message when present`() {
        val message = analyzeCellsErrorMessage("low_quality", "Poza este prea neclara")

        assertEquals("Poza este prea neclara", message)
    }

    @Test
    fun `maps low quality status to photo guidance`() {
        val message = analyzeCellsErrorMessage("low_quality", null)

        assertTrue(message.contains("Fotografia"))
        assertTrue(message.contains("clara"))
    }

    @Test
    fun `maps non comb status to framing guidance`() {
        val message = analyzeCellsErrorMessage("not_comb_image", null)

        assertTrue(message.contains("rama"))
        assertTrue(message.contains("fagurele"))
    }

    @Test
    fun `maps uncertain analysis status to retry with clearer frame`() {
        val message = analyzeCellsErrorMessage("uncertain_analysis", null)

        assertTrue(message.contains("sigura"))
        assertTrue(message.contains("fotografie"))
    }
}

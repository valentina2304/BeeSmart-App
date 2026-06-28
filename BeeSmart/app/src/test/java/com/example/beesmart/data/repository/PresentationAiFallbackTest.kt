package com.example.beesmart.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationAiFallbackTest {

    @Test
    fun `fallback response is internally consistent`() {
        val response = PresentationAiFallback.response("timeout")

        assertEquals(PresentationAiFallback.STATUS, response.status)
        assertTrue(response.message.orEmpty().contains("demonstrativ"))

        val countedCells = response.results.values.sum()
        assertEquals(countedCells, response.cellDetections.size)
        assertEquals(countedCells, response.quality?.detectedCells)
        assertEquals(countedCells, response.quality?.classifiedCells)

        response.cellDetections.forEach { cell ->
            assertTrue(cell.normalizedX in 0.0..1.0)
            assertTrue(cell.normalizedY in 0.0..1.0)
            assertTrue(cell.normalizedRadius in 0.0..1.0)
            assertTrue(cell.confidence in 0.0..1.0)
        }
    }
}

package com.example.beesmart.data.repository

import com.example.beesmart.network.models.CellDetection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BroodAnalyzerTest {

    @Test
    fun `aggregate aliases into stable brood and stores categories`() {
        val report = BroodAnalyzer.analyze(
            mapOf(
                "capped_brood" to 20,
                "puiet capacit" to 5,
                "puiet căpăcit" to 5,
                "larvae" to 10,
                "puiet deschis" to 3,
                "eggs" to 4,
                "ouă" to 2,
                "miere" to 8,
                "Nectar" to 3,
                "polen" to 7,
                "empty" to 6,
                "mystery" to 2,
                "ignored-negative" to -5
            )
        )

        val totals = report.metrics.totals
        assertEquals(30, totals[BroodAnalyzer.Category.CAPPED_BROOD])
        assertEquals(13, totals[BroodAnalyzer.Category.LARVAE])
        assertEquals(6, totals[BroodAnalyzer.Category.EGGS])
        assertEquals(11, totals[BroodAnalyzer.Category.HONEY])
        assertEquals(7, totals[BroodAnalyzer.Category.POLLEN])
        assertEquals(6, totals[BroodAnalyzer.Category.EMPTY])
        assertEquals(2, totals[BroodAnalyzer.Category.OTHER])
        assertEquals(75, report.metrics.total)
        assertEquals(49, report.metrics.broodTotal)
    }

    @Test
    fun `healthy verdict when all brood stages and stores are present`() {
        val report = BroodAnalyzer.analyze(
            mapOf(
                "capped" to 60,
                "larvae" to 30,
                "eggs" to 10,
                "honey" to 20,
                "pollen" to 10,
                "empty" to 5
            )
        )

        assertEquals(BroodAnalyzer.Level.HEALTHY, report.verdict.level)
        assertTrue(report.verdict.highlights.any { it.contains("ouă, larve") })
        assertTrue(report.verdict.recommendations.any { it.contains("monitorizarea") })
    }

    @Test
    fun `missing larvae on one frame asks for comparison without diagnosis`() {
        val report = BroodAnalyzer.analyze(
            mapOf(
                "capped_brood" to 80,
                "eggs" to 2,
                "honey" to 8,
                "empty" to 10
            )
        )

        assertEquals(BroodAnalyzer.Level.ATTENTION, report.verdict.level)
        assertTrue(report.verdict.concerns.any { it.contains("Nu s-au identificat larve") })
        assertTrue(report.verdict.recommendations.any { it.contains("ramele vecine") })
        assertFalse(report.verdict.concerns.any { it.contains("bezmetic", ignoreCase = true) })
        assertFalse(report.verdict.recommendations.any { it.contains("regina", ignoreCase = true) })
    }

    @Test
    fun `no brood on detected frame raises brood absence concern`() {
        val report = BroodAnalyzer.analyze(
            mapOf(
                "honey" to 25,
                "pollen" to 10,
                "empty" to 20
            )
        )

        assertEquals(0, report.metrics.broodTotal)
        assertEquals(BroodAnalyzer.Level.ATTENTION, report.verdict.level)
        assertTrue(report.verdict.concerns.any { it.contains("Nu s-a detectat puiet") })
        assertTrue(report.verdict.concerns.any { it.contains("puiet") })
    }

    @Test
    fun `empty input keeps ratios NaN and reports insufficient data`() {
        val report = BroodAnalyzer.analyze(emptyMap())

        assertEquals(0, report.metrics.total)
        assertTrue(report.metrics.emptyRatio.isNaN())
        assertTrue(report.metrics.broodDensity.isNaN())
        assertEquals(BroodAnalyzer.Level.ATTENTION, report.verdict.level)
        assertTrue(report.verdict.highlights.isEmpty())
        assertTrue(report.verdict.concerns.any { it.contains("celule suficiente") })
    }

    @Test
    fun `many empty cells require physical distribution check`() {
        val report = BroodAnalyzer.analyze(
            mapOf(
                "capped_brood" to 30,
                "larvae" to 15,
                "empty" to 60
            )
        )

        assertEquals(BroodAnalyzer.Level.ATTENTION, report.verdict.level)
        assertTrue(report.verdict.concerns.any { it.contains("verificată fizic") })
        assertTrue(report.verdict.recommendations.any { it.contains("fără coordonate") })
        assertFalse(report.verdict.concerns.any { it.contains("boal", ignoreCase = true) })
    }

    @Test
    fun `compact brood coordinates enrich verdict with spatial highlight`() {
        val broodCells = (0 until 16).map { index ->
            val x = 0.42 + (index % 4) * 0.025
            val y = 0.42 + (index / 4) * 0.025
            cell(if (index % 3 == 0) "Eggs" else "Capped", x, y)
        }
        val stores = (0 until 10).map { index ->
            cell(if (index % 2 == 0) "Honey" else "Pollen", 0.05 + index * 0.01, 0.08)
        }

        val report = BroodAnalyzer.analyze(
            rawCounts = mapOf("Capped" to 10, "Eggs" to 6, "Honey" to 5, "Pollen" to 5),
            cellDetections = broodCells + stores
        )

        assertTrue(report.spatial.hasCoordinates)
        assertTrue(report.spatial.broodCompactness > 0.8)
        assertTrue(report.verdict.highlights.any { it.contains("puiet relativ compact") })
        assertTrue(report.verdict.highlights.any { it.contains("marginea ramei") })
    }

    @Test
    fun `dispersed brood coordinates raise spatial concern`() {
        val brood = listOf(
            cell("Capped", 0.15, 0.15),
            cell("Capped", 0.18, 0.15),
            cell("Eggs", 0.15, 0.18),
            cell("Capped", 0.80, 0.18),
            cell("Larves", 0.83, 0.18),
            cell("Eggs", 0.80, 0.21),
            cell("Capped", 0.18, 0.78),
            cell("Larves", 0.21, 0.78),
            cell("Eggs", 0.18, 0.81),
            cell("Capped", 0.78, 0.78),
            cell("Larves", 0.81, 0.78),
            cell("Eggs", 0.78, 0.81)
        )
        val nonBroodInside = (0 until 24).map { index ->
            val x = 0.30 + (index % 6) * 0.07
            val y = 0.30 + (index / 6) * 0.10
            cell("Other", x, y)
        }

        val report = BroodAnalyzer.analyze(
            rawCounts = mapOf("Capped" to 4, "Larves" to 4, "Eggs" to 4, "Other" to 24),
            cellDetections = brood + nonBroodInside
        )

        assertTrue(report.spatial.broodGapRatio > 0.55)
        assertEquals(BroodAnalyzer.Level.ATTENTION, report.verdict.level)
        assertTrue(report.verdict.concerns.any { it.contains("dispersat") })
        assertTrue(report.verdict.recommendations.any { it.contains("coordonatele") })
    }

    private fun cell(className: String, normalizedX: Double, normalizedY: Double) = CellDetection(
        x = (normalizedX * 1000).toInt(),
        y = (normalizedY * 1000).toInt(),
        radius = 12,
        normalizedX = normalizedX,
        normalizedY = normalizedY,
        normalizedRadius = 0.012,
        className = className,
        confidence = 0.95
    )

}

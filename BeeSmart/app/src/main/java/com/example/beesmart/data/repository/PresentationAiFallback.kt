package com.example.beesmart.data.repository

import com.example.beesmart.network.models.AnalyzeCellsQuality
import com.example.beesmart.network.models.AnalyzeCellsResponse
import com.example.beesmart.network.models.CellDetection
import kotlin.math.roundToInt

/**
 * Deterministic DeepBee-style result used only when the presentation fallback is
 * explicitly enabled and the real AI endpoint is unavailable or too slow.
 */
object PresentationAiFallback {
    const val STATUS = "demo_fallback"

    private const val IMAGE_WIDTH = 1536
    private const val IMAGE_HEIGHT = 1024
    private const val NORMALIZED_RADIUS = 0.016

    private val counts = linkedMapOf(
        "capped_brood" to 34,
        "larvae" to 16,
        "eggs" to 8,
        "honey" to 22,
        "pollen" to 10,
        "empty" to 18,
        "other" to 4
    )

    fun response(reason: String? = null): AnalyzeCellsResponse {
        val detections = detections()
        return AnalyzeCellsResponse(
            status = STATUS,
            results = counts,
            message = demoMessage(reason),
            quality = AnalyzeCellsQuality(
                width = IMAGE_WIDTH,
                height = IMAGE_HEIGHT,
                blurVariance = 420.0,
                brightness = 0.58,
                contrast = 0.46,
                segmentationEnabled = true,
                detectedCells = detections.size,
                classifiedCells = detections.size,
                lowConfidenceCells = 4,
                lowConfidenceRatio = 4.0 / detections.size,
                meanConfidence = detections.map { it.confidence }.average(),
                combMaskCoverage = 0.78
            ),
            cellDetections = detections
        )
    }

    private fun demoMessage(reason: String?): String = buildString {
        append("Rezultat demonstrativ pentru prezentare.")
        if (!reason.isNullOrBlank()) {
            append(" Analiza reala nu a raspuns stabil: ")
            append(reason.take(110))
        }
        append(" Dezactiveaza fallback-ul pentru date reale.")
    }

    private fun detections(): List<CellDetection> =
        cluster("capped_brood", 34, 0.34, 0.68, 0.34, 0.58, columns = 7, confidence = 0.91) +
            cluster("larvae", 16, 0.39, 0.64, 0.54, 0.70, columns = 4, confidence = 0.86) +
            cluster("eggs", 8, 0.43, 0.58, 0.42, 0.52, columns = 4, confidence = 0.79) +
            cluster("honey", 12, 0.18, 0.82, 0.10, 0.18, columns = 6, confidence = 0.89) +
            cluster("honey", 10, 0.08, 0.92, 0.27, 0.76, columns = 2, confidence = 0.86) +
            cluster("pollen", 10, 0.27, 0.76, 0.31, 0.69, columns = 5, confidence = 0.82) +
            cluster("empty", 18, 0.22, 0.80, 0.72, 0.88, columns = 6, confidence = 0.84) +
            cluster("other", 4, 0.18, 0.82, 0.25, 0.75, columns = 2, confidence = 0.68)

    private fun cluster(
        className: String,
        count: Int,
        xStart: Double,
        xEnd: Double,
        yStart: Double,
        yEnd: Double,
        columns: Int,
        confidence: Double
    ): List<CellDetection> {
        val safeColumns = columns.coerceAtLeast(1)
        val rows = ((count + safeColumns - 1) / safeColumns).coerceAtLeast(1)
        return (0 until count).map { index ->
            val column = index % safeColumns
            val row = index / safeColumns
            val x = interpolate(xStart, xEnd, column, safeColumns)
            val y = interpolate(yStart, yEnd, row, rows)
            cell(className, x, y, confidence - ((index % 5) * 0.015))
        }
    }

    private fun interpolate(start: Double, end: Double, index: Int, count: Int): Double =
        if (count <= 1) (start + end) / 2.0 else start + ((end - start) * index / (count - 1))

    private fun cell(className: String, x: Double, y: Double, confidence: Double): CellDetection {
        val safeX = x.coerceIn(0.0, 1.0)
        val safeY = y.coerceIn(0.0, 1.0)
        return CellDetection(
            x = (safeX * IMAGE_WIDTH).roundToInt(),
            y = (safeY * IMAGE_HEIGHT).roundToInt(),
            radius = (NORMALIZED_RADIUS * IMAGE_WIDTH).roundToInt(),
            normalizedX = safeX,
            normalizedY = safeY,
            normalizedRadius = NORMALIZED_RADIUS,
            className = className,
            confidence = confidence.coerceIn(0.50, 0.99)
        )
    }
}

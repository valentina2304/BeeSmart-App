package com.example.beesmart.data.repository

import com.example.beesmart.network.models.CellDetection
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Translates the raw cell-count map returned by the DeepBee/Gemini endpoint into
 * derived metrics and an explainable frame-level verdict.
 *
 * The AI service exposes per-cell-type counts under variable key names depending
 * on model version. [categorize] maps aliases onto a fixed set of [Category]
 * buckets so the downstream calculations remain stable.
 *
 * Counts alone cannot establish spatial brood uniformity, Varroa load or a
 * colony-level diagnosis. Recommendations therefore ask for physical checks
 * whenever the analyzed frame is not sufficient evidence.
 */
object BroodAnalyzer {
    private const val SPATIAL_GRID_SIZE = 8
    private const val MIN_SPATIAL_BROOD_CELLS = 12
    private const val MIN_SPATIAL_STORE_CELLS = 10

    enum class Category {
        CAPPED_BROOD,
        LARVAE,
        EGGS,
        HONEY,
        POLLEN,
        EMPTY,
        OTHER
    }

    enum class Level { HEALTHY, ATTENTION, WARNING }

    data class Metrics(
        val totals: Map<Category, Int>,
        val total: Int,
        val broodTotal: Int,
        val nonEmpty: Int,
        // Ratios in 0..1 (NaN if denominator is 0).
        val cappedRatio: Double,
        val larvaeRatio: Double,
        val eggsRatio: Double,
        val emptyRatio: Double,
        val storesRatio: Double,
        val broodDensity: Double,
        val larvaeDensity: Double,
        val larvaeToCappedRatio: Double,
        // Share of occupied cells that contain brood. This is not a spatial metric.
        val broodOccupancyRatio: Double
    )

    data class SpatialMetrics(
        val analyzedCells: Int,
        val broodCells: Int,
        val storesCells: Int,
        val pollenCells: Int,
        val broodCompactness: Double,
        val broodGapRatio: Double,
        val storesEdgeRatio: Double,
        val pollenNearBroodRatio: Double,
        val broodCenterX: Double,
        val broodCenterY: Double,
        val storesCenterX: Double,
        val storesCenterY: Double
    ) {
        val hasCoordinates: Boolean = analyzedCells > 0
    }

    data class Verdict(
        val level: Level,
        val headline: String,
        val highlights: List<String>,
        val concerns: List<String>,
        val recommendations: List<String>
    )

    data class Report(
        val metrics: Metrics,
        val verdict: Verdict,
        val spatial: SpatialMetrics
    )

    fun analyze(rawCounts: Map<String, Int>, cellDetections: List<CellDetection> = emptyList()): Report {
        val totals = aggregateByCategory(rawCounts)
        val total = totals.values.sum()
        val capped = totals[Category.CAPPED_BROOD] ?: 0
        val larvae = totals[Category.LARVAE] ?: 0
        val eggs = totals[Category.EGGS] ?: 0
        val honey = totals[Category.HONEY] ?: 0
        val pollen = totals[Category.POLLEN] ?: 0
        val empty = totals[Category.EMPTY] ?: 0
        val broodTotal = capped + larvae + eggs
        val stores = honey + pollen
        val nonEmpty = total - empty

        val metrics = Metrics(
            totals = totals,
            total = total,
            broodTotal = broodTotal,
            nonEmpty = nonEmpty,
            cappedRatio = safeDiv(capped, broodTotal),
            larvaeRatio = safeDiv(larvae, broodTotal),
            eggsRatio = safeDiv(eggs, broodTotal),
            emptyRatio = safeDiv(empty, total),
            storesRatio = safeDiv(stores, total),
            broodDensity = safeDiv(broodTotal, nonEmpty),
            larvaeDensity = safeDiv(larvae, total),
            larvaeToCappedRatio = safeDiv(larvae, capped),
            broodOccupancyRatio = safeDiv(broodTotal, nonEmpty)
        )

        val spatial = analyzeSpatial(cellDetections)
        return Report(metrics, buildVerdict(metrics, spatial), spatial)
    }

    private fun aggregateByCategory(raw: Map<String, Int>): Map<Category, Int> {
        val acc = linkedMapOf<Category, Int>()
        for ((key, count) in raw) {
            if (count <= 0) continue
            val category = categorize(key)
            acc[category] = (acc[category] ?: 0) + count
        }
        return acc
    }

    private fun categorize(key: String): Category {
        val normalized = Normalizer.normalize(
            key.trim().lowercase(Locale.ROOT),
            Normalizer.Form.NFD
        ).replace("\\p{M}+".toRegex(), "")
        return when {
            normalized.contains("capped") && normalized.contains("brood") -> Category.CAPPED_BROOD
            normalized == "capped" || normalized == "capped_brood" || normalized == "cappedbrood" -> Category.CAPPED_BROOD
            normalized.contains("capac") || normalized.contains("operculat") -> Category.CAPPED_BROOD

            normalized.startsWith("larva") || normalized.startsWith("larvae") -> Category.LARVAE
            normalized.contains("larv") -> Category.LARVAE
            normalized.contains("puiet") && normalized.contains("deschis") -> Category.LARVAE

            normalized == "egg" || normalized == "eggs" -> Category.EGGS
            normalized.contains("oua") || normalized == "ou" -> Category.EGGS

            normalized.contains("honey") || normalized.contains("nectar") -> Category.HONEY
            normalized.contains("miere") -> Category.HONEY

            normalized.contains("pollen") || normalized.contains("polen") -> Category.POLLEN

            normalized.contains("empty") || normalized.contains("vacant") || normalized.contains("free") -> Category.EMPTY
            normalized.contains("goala") || normalized.contains("goale") -> Category.EMPTY

            else -> Category.OTHER
        }
    }

    private fun safeDiv(numerator: Int, denominator: Int): Double =
        if (denominator == 0) Double.NaN else numerator.toDouble() / denominator.toDouble()

    private data class SpatialCell(
        val x: Double,
        val y: Double,
        val category: Category
    )

    private fun analyzeSpatial(cellDetections: List<CellDetection>): SpatialMetrics {
        val cells = cellDetections.mapNotNull { detection ->
            val x = detection.normalizedX
            val y = detection.normalizedY
            if (!x.isFinite() || !y.isFinite() || x !in 0.0..1.0 || y !in 0.0..1.0) {
                null
            } else {
                SpatialCell(x, y, categorize(detection.className))
            }
        }

        if (cells.isEmpty()) {
            return emptySpatialMetrics()
        }

        val brood = cells.filter { it.category.isBrood }
        val stores = cells.filter { it.category.isStore }
        val pollen = cells.filter { it.category == Category.POLLEN }
        val broodCenter = centroid(brood)
        val storesCenter = centroid(stores)

        return SpatialMetrics(
            analyzedCells = cells.size,
            broodCells = brood.size,
            storesCells = stores.size,
            pollenCells = pollen.size,
            broodCompactness = broodCompactness(brood),
            broodGapRatio = broodGapRatio(cells, brood),
            storesEdgeRatio = storesEdgeRatio(cells, stores),
            pollenNearBroodRatio = pollenNearBroodRatio(pollen, brood),
            broodCenterX = broodCenter.first,
            broodCenterY = broodCenter.second,
            storesCenterX = storesCenter.first,
            storesCenterY = storesCenter.second
        )
    }

    private fun emptySpatialMetrics() = SpatialMetrics(
        analyzedCells = 0,
        broodCells = 0,
        storesCells = 0,
        pollenCells = 0,
        broodCompactness = Double.NaN,
        broodGapRatio = Double.NaN,
        storesEdgeRatio = Double.NaN,
        pollenNearBroodRatio = Double.NaN,
        broodCenterX = Double.NaN,
        broodCenterY = Double.NaN,
        storesCenterX = Double.NaN,
        storesCenterY = Double.NaN
    )

    private fun broodCompactness(brood: List<SpatialCell>): Double {
        if (brood.size < MIN_SPATIAL_BROOD_CELLS) return Double.NaN

        val weightedGrid = brood.groupingBy { cell ->
            gridIndex(cell.x) to gridIndex(cell.y)
        }.eachCount()
        val unvisited = weightedGrid.keys.toMutableSet()
        var largestComponent = 0

        while (unvisited.isNotEmpty()) {
            val start = unvisited.first()
            val queue = ArrayDeque<Pair<Int, Int>>()
            queue.add(start)
            unvisited.remove(start)
            var componentWeight = 0

            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                componentWeight += weightedGrid[current] ?: 0
                for (dx in -1..1) {
                    for (dy in -1..1) {
                        if (dx == 0 && dy == 0) continue
                        val next = current.first + dx to current.second + dy
                        if (next in unvisited) {
                            unvisited.remove(next)
                            queue.add(next)
                        }
                    }
                }
            }

            largestComponent = maxOf(largestComponent, componentWeight)
        }

        return safeDiv(largestComponent, brood.size)
    }

    private fun broodGapRatio(cells: List<SpatialCell>, brood: List<SpatialCell>): Double {
        if (brood.size < MIN_SPATIAL_BROOD_CELLS) return Double.NaN

        val minX = brood.minOf { it.x }
        val maxX = brood.maxOf { it.x }
        val minY = brood.minOf { it.y }
        val maxY = brood.maxOf { it.y }
        val padX = ((maxX - minX) * 0.08).coerceAtLeast(0.015)
        val padY = ((maxY - minY) * 0.08).coerceAtLeast(0.015)
        val insideBroodArea = cells.filter {
            it.x in (minX - padX)..(maxX + padX) &&
                it.y in (minY - padY)..(maxY + padY)
        }
        if (insideBroodArea.isEmpty()) return Double.NaN

        val nonBroodInside = insideBroodArea.count { !it.category.isBrood }
        return safeDiv(nonBroodInside, insideBroodArea.size)
    }

    private fun storesEdgeRatio(cells: List<SpatialCell>, stores: List<SpatialCell>): Double {
        if (stores.size < MIN_SPATIAL_STORE_CELLS || cells.isEmpty()) return Double.NaN

        val minX = cells.minOf { it.x }
        val maxX = cells.maxOf { it.x }
        val minY = cells.minOf { it.y }
        val maxY = cells.maxOf { it.y }
        val marginX = ((maxX - minX) * 0.18).coerceAtLeast(0.04)
        val marginY = ((maxY - minY) * 0.18).coerceAtLeast(0.04)
        val edgeStores = stores.count {
            it.x <= minX + marginX ||
                it.x >= maxX - marginX ||
                it.y <= minY + marginY ||
                it.y >= maxY - marginY
        }
        return safeDiv(edgeStores, stores.size)
    }

    private fun pollenNearBroodRatio(pollen: List<SpatialCell>, brood: List<SpatialCell>): Double {
        if (pollen.isEmpty() || brood.size < MIN_SPATIAL_BROOD_CELLS) return Double.NaN

        val minX = brood.minOf { it.x }
        val maxX = brood.maxOf { it.x }
        val minY = brood.minOf { it.y }
        val maxY = brood.maxOf { it.y }
        val pad = 0.08
        val nearBrood = pollen.count {
            it.x in (minX - pad)..(maxX + pad) &&
                it.y in (minY - pad)..(maxY + pad)
        }
        return safeDiv(nearBrood, pollen.size)
    }

    private fun centroid(cells: List<SpatialCell>): Pair<Double, Double> {
        if (cells.isEmpty()) return Double.NaN to Double.NaN
        return cells.map { it.x }.average() to cells.map { it.y }.average()
    }

    private fun gridIndex(value: Double): Int =
        (value.coerceIn(0.0, 0.999999) * SPATIAL_GRID_SIZE).toInt()

    private val Category.isBrood: Boolean
        get() = this == Category.CAPPED_BROOD || this == Category.LARVAE || this == Category.EGGS

    private val Category.isStore: Boolean
        get() = this == Category.HONEY || this == Category.POLLEN

    private fun buildVerdict(metrics: Metrics, spatial: SpatialMetrics): Verdict {
        val highlights = mutableListOf<String>()
        val concerns = mutableListOf<String>()
        val recommendations = mutableListOf<String>()

        val capped = metrics.totals[Category.CAPPED_BROOD] ?: 0
        val larvae = metrics.totals[Category.LARVAE] ?: 0
        val eggs = metrics.totals[Category.EGGS] ?: 0

        if (metrics.total == 0) {
            concerns += "Analiza nu a identificat celule suficiente pentru interpretare"
        } else {
            if (metrics.broodTotal == 0) {
                concerns += "Nu s-a detectat puiet pe rama analizată; aceasta poate să nu fie o ramă din cuib"
            }
            if (capped > 0 && larvae > 0 && eggs > 0) {
                highlights += "Sunt prezente ouă, larve și puiet căpăcit; continuitatea pontei este susținută pe rama analizată"
            }
            if (metrics.broodOccupancyRatio.finiteOr(0.0) >= 0.55) {
                highlights += "Pondere ridicată de puiet (${pct(metrics.broodOccupancyRatio)} din celulele ocupate); uniformitatea se confirmă vizual"
            }
            if (metrics.storesRatio.finiteOr(0.0) >= 0.20) {
                highlights += "Rama analizată conține rezerve de hrană (${pct(metrics.storesRatio)})"
            }
            if (spatial.hasCoordinates) {
                addSpatialHighlightsAndConcerns(spatial, highlights, concerns)
            }

            if (capped == 0 && larvae > 0) {
                concerns += "Nu s-au identificat celule cu puiet căpăcit; compară cu ramele vecine și cu inspecțiile anterioare"
            }
            if (larvae == 0 && capped > 0) {
                concerns += "Nu s-au identificat larve pe rama analizată; o singură ramă nu confirmă ritmul actual al pontei"
            }
            if (metrics.emptyRatio.finiteOr(0.0) > 0.45 && metrics.broodTotal > 0) {
                concerns += if (spatial.hasCoordinates) {
                    "Multe celule goale (${pct(metrics.emptyRatio)}); coordonatele ajută la verificarea golurilor din zona de puiet"
                } else {
                    "Multe celule goale (${pct(metrics.emptyRatio)}); distribuția puietului trebuie verificată fizic"
                }
            }
            if (metrics.larvaeToCappedRatio.finiteOr(Double.MAX_VALUE) < 0.20 && capped >= 20) {
                concerns += "Raport larve/puiet căpăcit scăzut (${ratio(metrics.larvaeToCappedRatio)}); urmărește evoluția pontei"
            }
            if (metrics.storesRatio.finiteOr(0.0) < 0.05 && metrics.total > 30) {
                concerns += "Rama analizată are foarte puține celule cu miere sau polen"
            }
        }

        if (concerns.any { it.contains("distribuția puietului") || it.contains("dispersat") || it.contains("goluri") }) {
            recommendations += if (spatial.hasCoordinates) {
                "Verifică vizual zona indicată de puiet și compară cu ramele vecine; coordonatele sugerează unde pot exista goluri, dar concluzia rămâne de confirmat în stupină."
            } else {
                "Verifică fizic distribuția puietului pe ramă și compară cu ramele vecine; modelul nu poate stabili uniformitatea fără coordonate."
            }
        }
        if (concerns.any { it.contains("central") || it.contains("amestecate") }) {
            recommendations += "Verifică dacă rezervele sunt pe coroană/margini sau pătrund în zona de cuib; distribuția ajută la decizia privind spațiul și hrănirea."
        }
        if (concerns.any { it.contains("Nu s-au identificat larve") }) {
            recommendations += "Compară cu ramele vecine și repetă inspecția; absența larvelor pe o singură ramă nu confirmă starea pontei."
        }
        if (concerns.any { it.contains("miere sau polen") }) {
            recommendations += "Verifică rezervele pe ramele alăturate, sezonul și vremea înainte de a decide dacă este necesară hrănirea."
        }
        if (concerns.any { it.contains("evoluția pontei") }) {
            recommendations += "Compară cu inspecțiile anterioare și repetă controlul; o tendință persistentă necesită evaluare."
        }
        if (recommendations.isEmpty() && highlights.isNotEmpty()) {
            recommendations += "Continuă monitorizarea de rutină și compară rezultatul cu următoarea inspecție."
        }

        // A single analyzed frame can raise an attention signal, but it cannot
        // establish a colony-level warning without physical cross-checks.
        val level = if (concerns.isNotEmpty()) Level.ATTENTION else Level.HEALTHY
        val headline = when (level) {
            Level.HEALTHY -> "Indicatori favorabili pe rama analizată"
            Level.ATTENTION -> "Necesită verificare în stupină"
            Level.WARNING -> "Semnal de avertizare"
        }
        return Verdict(level, headline, highlights, concerns, recommendations)
    }

    private fun addSpatialHighlightsAndConcerns(
        spatial: SpatialMetrics,
        highlights: MutableList<String>,
        concerns: MutableList<String>
    ) {
        if (spatial.broodCells >= MIN_SPATIAL_BROOD_CELLS) {
            when {
                spatial.broodCompactness.finiteOr(0.0) >= 0.65 &&
                    spatial.broodGapRatio.finiteOr(1.0) <= 0.35 -> {
                    highlights += "Coordonatele indică puiet relativ compact (${pct(spatial.broodCompactness)} în cea mai mare zonă conectată)"
                }
                spatial.broodCompactness.finiteOr(1.0) < 0.45 ||
                    spatial.broodGapRatio.finiteOr(0.0) > 0.55 -> {
                    concerns += "Coordonatele sugerează puiet dispersat sau goluri non-puiet în zona cuibului (${pct(spatial.broodGapRatio)})"
                }
            }
        }

        if (spatial.storesCells >= MIN_SPATIAL_STORE_CELLS) {
            when {
                spatial.storesEdgeRatio.finiteOr(0.0) >= 0.55 -> {
                    highlights += "Mierea și polenul sunt predominant pe marginea ramei (${pct(spatial.storesEdgeRatio)})"
                }
                spatial.storesEdgeRatio.finiteOr(1.0) < 0.25 -> {
                    concerns += "Rezervele par mai centrale sau amestecate cu zona de cuib (${pct(spatial.storesEdgeRatio)} pe margini)"
                }
            }
        }

        if (spatial.pollenCells >= 5 && spatial.broodCells >= MIN_SPATIAL_BROOD_CELLS) {
            when {
                spatial.pollenNearBroodRatio.finiteOr(0.0) >= 0.45 -> {
                    highlights += "Polenul detectat este aproape de zona de puiet (${pct(spatial.pollenNearBroodRatio)})"
                }
                spatial.pollenNearBroodRatio.finiteOr(1.0) < 0.25 -> {
                    concerns += "Polenul detectat pare departe de zona de puiet (${pct(spatial.pollenNearBroodRatio)} aproape de cuib)"
                }
            }
        }
    }

    private fun Double.finiteOr(default: Double): Double = if (isFinite()) this else default

    private fun pct(value: Double): String =
        if (value.isFinite()) "${(value * 100).roundToInt()}%" else "-"

    private fun ratio(value: Double): String =
        if (value.isFinite()) String.format(Locale.US, "%.2f", value) else "-"
}

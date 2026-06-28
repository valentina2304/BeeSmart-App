package com.example.beesmart.data.repository

import com.example.beesmart.network.models.ExtractionType
import com.example.beesmart.network.models.HiveExtraction
import com.example.beesmart.network.models.HiveResponse
import com.example.beesmart.network.models.HiveStatus
import com.example.beesmart.network.models.HiveTreatment
import com.example.beesmart.network.models.HiveType
import com.example.beesmart.network.models.InspectionResponse
import com.example.beesmart.network.models.TaskResponse
import com.example.beesmart.network.models.TaskStatus
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.math.roundToInt

data class DashboardStats(
    val hivesCount: Int = 0,
    val activeHivesCount: Int = 0,
    val pendingTasksCount: Int = 0,
    val overdueTasksCount: Int = 0,
    val inspectionsCount: Int = 0,
    val honeyAnalytics: HoneyAnalytics = HoneyAnalytics(),
    val apiaryRadar: ApiaryRadar = ApiaryRadar(),
    val deepBeeAdvice: ApiaryAdviceDigest = ApiaryAdviceDigest()
)

data class HoneyAnalytics(
    val currentYearKg: Double = 0.0,
    val lifetimeKg: Double = 0.0,
    val nextHarvestPotentialKg: Double = 0.0,
    val nextHarvestPotentialMinKg: Double = 0.0,
    val nextHarvestPotentialMaxKg: Double = 0.0,
    val seasonForecastKg: Double = 0.0,
    val seasonForecastMinKg: Double = 0.0,
    val seasonForecastMaxKg: Double = 0.0,
    val kgPerActiveHive: Double = 0.0,
    val honeyFrames: Int = 0,
    val monthlyProduction: List<MonthlyHoneyProduction> = emptyList(),
    val trendPercent: Int? = null,
    val confidence: ForecastConfidence = ForecastConfidence.LOW,
    val ignoredHoneyEntries: Int = 0,
    val usesFallbackFrameCapacity: Boolean = false,
    val usesSeasonalHistory: Boolean = false,
    val seasonalReferenceYears: Int = 0,
    val seasonalForecastRemainingKg: Double = 0.0
)

data class MonthlyHoneyProduction(
    val month: YearMonth,
    val kilograms: Double
)

enum class ForecastConfidence {
    LOW,
    MEDIUM,
    HIGH
}

/**
 * Computes dashboard analytics from the Room-backed models already available in
 * the app. The forecast is intentionally explainable: harvested honey this year
 * plus the estimated reserve in the honey frames of active hives.
 */
object DashboardAnalyticsCalculator {
    private const val DADANT_FULL_CAPPED_FRAME_KG = 3.6
    private const val MULTI_LEVEL_FULL_CAPPED_FRAME_KG = 2.3
    private const val ESTIMATED_MIN_FILL_RATIO = 0.50
    private const val ESTIMATED_MID_FILL_RATIO = 0.75

    fun calculate(
        hives: List<HiveResponse>,
        tasks: List<TaskResponse>,
        inspections: List<InspectionResponse>,
        extractions: List<HiveExtraction>,
        treatments: List<HiveTreatment> = emptyList(),
        aiSnapshots: List<AiHiveSnapshot> = emptyList(),
        today: LocalDate = LocalDate.now()
    ): DashboardStats {
        val activeHives = hives.filter { it.status == HiveStatus.Active }
        val honeyFrames = activeHives.sumOf { it.rameMiere.coerceAtLeast(0) }
        val validHoneyRecords = extractions.mapNotNull { it.toHoneyRecordOrNull() }
        val currentYearRecords = validHoneyRecords.filter { it.date.year == today.year }
        val currentYearKg = currentYearRecords.sumOf { it.kilograms }
        val fullCappedEquivalentKg = activeHives.sumOf { it.fullCappedHoneyCapacityKg() }
        val frameForecast = ForecastRange(
            min = fullCappedEquivalentKg * ESTIMATED_MIN_FILL_RATIO,
            mid = fullCappedEquivalentKg * ESTIMATED_MID_FILL_RATIO,
            max = fullCappedEquivalentKg
        )
        val seasonalForecast = seasonalForecast(validHoneyRecords, today)
        val nextHarvestForecast = seasonalForecast?.nextHarvest ?: frameForecast
        val remainingSeasonForecast = seasonalForecast?.remainingSeason ?: frameForecast
        val monthlyProduction = monthlyProduction(validHoneyRecords, today)
        val pendingTasks = tasks.filter {
            it.status == TaskStatus.Pending || it.status == TaskStatus.InProgress
        }

        return DashboardStats(
            hivesCount = hives.size,
            activeHivesCount = activeHives.size,
            pendingTasksCount = pendingTasks.size,
            overdueTasksCount = pendingTasks.count { it.dueDate.toLocalDateOrNull()?.isBefore(today) == true },
            inspectionsCount = inspections.size,
            apiaryRadar = ApiaryIntelligenceCalculator.calculate(
                hives = hives,
                tasks = tasks,
                inspections = inspections,
                aiSnapshots = aiSnapshots,
                today = today
            ),
            deepBeeAdvice = DeepBeeContextAdvisor.digest(
                hives = hives,
                tasks = tasks,
                inspections = inspections,
                treatments = treatments,
                extractions = extractions,
                aiSnapshots = aiSnapshots,
                today = today
            ),
            honeyAnalytics = HoneyAnalytics(
                currentYearKg = currentYearKg,
                lifetimeKg = validHoneyRecords.sumOf { it.kilograms },
                nextHarvestPotentialKg = nextHarvestForecast.mid,
                nextHarvestPotentialMinKg = nextHarvestForecast.min,
                nextHarvestPotentialMaxKg = nextHarvestForecast.max,
                seasonForecastKg = currentYearKg + remainingSeasonForecast.mid,
                seasonForecastMinKg = currentYearKg + remainingSeasonForecast.min,
                seasonForecastMaxKg = currentYearKg + remainingSeasonForecast.max,
                kgPerActiveHive = if (activeHives.isEmpty()) 0.0 else currentYearKg / activeHives.size,
                honeyFrames = honeyFrames,
                monthlyProduction = monthlyProduction,
                trendPercent = trendPercent(validHoneyRecords, today),
                confidence = confidence(
                    honeyRecordsCount = validHoneyRecords.size,
                    activeHives = activeHives,
                    honeyFrames = honeyFrames,
                    seasonalReferenceYears = seasonalForecast?.referenceYears ?: 0
                ),
                ignoredHoneyEntries = extractions.count { it.type == ExtractionType.Honey } - validHoneyRecords.size,
                usesFallbackFrameCapacity = activeHives.any {
                    it.rameMiere > 0 && it.type != HiveType.Dadant && it.type != HiveType.Langstroth
                },
                usesSeasonalHistory = seasonalForecast != null,
                seasonalReferenceYears = seasonalForecast?.referenceYears ?: 0,
                seasonalForecastRemainingKg = seasonalForecast?.remainingSeason?.mid ?: 0.0
            )
        )
    }

    private fun monthlyProduction(
        records: List<HoneyRecord>,
        today: LocalDate
    ): List<MonthlyHoneyProduction> {
        val currentMonth = YearMonth.from(today)
        return (5 downTo 0).map { monthsAgo ->
            val month = currentMonth.minusMonths(monthsAgo.toLong())
            MonthlyHoneyProduction(
                month = month,
                kilograms = records.filter { YearMonth.from(it.date) == month }.sumOf { it.kilograms }
            )
        }
    }

    private fun trendPercent(
        records: List<HoneyRecord>,
        today: LocalDate
    ): Int? {
        val currentMonths = recentThreeMonthWindow(today)
        val previousYearMonths = currentMonths.map { it.minusYears(1) }.toSet()
        val previousYearKg = records.sumForMonths(previousYearMonths)
        if (previousYearKg <= 0.0) return null

        val currentYearKg = records.sumForMonths(currentMonths.toSet())
        return (((currentYearKg - previousYearKg) / previousYearKg) * 100).roundToInt()
    }

    private fun recentThreeMonthWindow(today: LocalDate): List<YearMonth> {
        val currentMonth = YearMonth.from(today)
        return (2 downTo 0).map { monthsAgo -> currentMonth.minusMonths(monthsAgo.toLong()) }
    }

    private fun seasonalForecast(
        records: List<HoneyRecord>,
        today: LocalDate
    ): SeasonalForecast? {
        val currentYear = today.year
        val currentMonth = today.monthValue
        val historicalSameMonth = records
            .filter { it.date.year < currentYear && it.date.monthValue == currentMonth }
            .groupBy { it.date.year }
            .mapValues { (_, yearRecords) -> yearRecords.sumOf { it.kilograms } }
        val historicalRemainingSeason = records
            .filter { it.date.year < currentYear && it.date.monthValue >= currentMonth }
            .groupBy { it.date.year }
            .mapValues { (_, yearRecords) -> yearRecords.sumOf { it.kilograms } }

        val currentMonthKg = records
            .filter { it.date.year == currentYear && it.date.monthValue == currentMonth }
            .sumOf { it.kilograms }
        val currentRemainingSeasonKg = records
            .filter { it.date.year == currentYear && it.date.monthValue >= currentMonth }
            .sumOf { it.kilograms }

        val nextHarvest = historicalSameMonth.values
            .toForecastRange(alreadyProducedKg = currentMonthKg)
            ?: historicalRemainingSeason.values.toForecastRange(alreadyProducedKg = currentRemainingSeasonKg)
            ?: return null
        val remainingSeason = historicalRemainingSeason.values
            .toForecastRange(alreadyProducedKg = currentRemainingSeasonKg)
            ?: nextHarvest
        val referenceYears = (historicalSameMonth.keys + historicalRemainingSeason.keys).size

        return SeasonalForecast(
            nextHarvest = nextHarvest,
            remainingSeason = remainingSeason,
            referenceYears = referenceYears
        )
    }

    private fun confidence(
        honeyRecordsCount: Int,
        activeHives: List<HiveResponse>,
        honeyFrames: Int,
        seasonalReferenceYears: Int
    ): ForecastConfidence {
        val hivesWithHoneyFrames = activeHives.count { it.rameMiere > 0 }
        return when {
            seasonalReferenceYears >= 2 -> ForecastConfidence.HIGH
            seasonalReferenceYears == 1 -> ForecastConfidence.MEDIUM
            honeyRecordsCount >= 6 && hivesWithHoneyFrames >= 3 -> ForecastConfidence.HIGH
            honeyRecordsCount >= 2 || honeyFrames > 0 -> ForecastConfidence.MEDIUM
            else -> ForecastConfidence.LOW
        }
    }

    private fun HiveExtraction.toHoneyRecordOrNull(): HoneyRecord? {
        if (type != ExtractionType.Honey || quantity < 0.0 || !quantity.isFinite()) return null
        val date = extractionDate.toLocalDateOrNull() ?: return null
        val kilograms = quantity.toKilogramsOrNull(unit) ?: return null
        return HoneyRecord(date, kilograms)
    }

    private fun HiveResponse.fullCappedHoneyCapacityKg(): Double {
        val kilogramsPerFrame = when (type) {
            HiveType.Dadant -> DADANT_FULL_CAPPED_FRAME_KG
            HiveType.Langstroth -> MULTI_LEVEL_FULL_CAPPED_FRAME_KG
            HiveType.Warre,
            HiveType.TopBar,
            HiveType.Other -> MULTI_LEVEL_FULL_CAPPED_FRAME_KG
        }
        return rameMiere.coerceAtLeast(0) * kilogramsPerFrame
    }

    private fun Double.toKilogramsOrNull(unit: String): Double? =
        when (unit.trim().lowercase(Locale.ROOT).replace(".", "")) {
            "kg", "kilogram", "kilograms", "kilograme" -> this
            "g", "gram", "grams", "grame" -> this / 1_000
            else -> null
        }

    private fun String?.toLocalDateOrNull(): LocalDate? {
        if (this == null || length < 10) return null
        return runCatching { LocalDate.parse(take(10)) }.getOrNull()
    }

    private fun List<HoneyRecord>.sumForMonths(months: Set<YearMonth>): Double =
        filter { YearMonth.from(it.date) in months }.sumOf { it.kilograms }

    private fun Collection<Double>.toForecastRange(alreadyProducedKg: Double): ForecastRange? {
        if (isEmpty()) return null
        val historicalMin = minOrNull() ?: return null
        val historicalMax = maxOrNull() ?: return null
        val historicalMid = average()
        return ForecastRange(
            min = (historicalMin - alreadyProducedKg).coerceAtLeast(0.0),
            mid = (historicalMid - alreadyProducedKg).coerceAtLeast(0.0),
            max = (historicalMax - alreadyProducedKg).coerceAtLeast(0.0)
        )
    }

    private data class ForecastRange(
        val min: Double,
        val mid: Double,
        val max: Double
    )

    private data class SeasonalForecast(
        val nextHarvest: ForecastRange,
        val remainingSeason: ForecastRange,
        val referenceYears: Int
    )

    private data class HoneyRecord(
        val date: LocalDate,
        val kilograms: Double
    )
}

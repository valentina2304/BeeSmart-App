package com.example.beesmart.data.repository

import com.example.beesmart.network.models.ExtractionType
import com.example.beesmart.network.models.HiveExtraction
import com.example.beesmart.network.models.HiveResponse
import com.example.beesmart.network.models.HiveStatus
import com.example.beesmart.network.models.HiveType
import com.example.beesmart.network.models.TaskPriority
import com.example.beesmart.network.models.TaskResponse
import com.example.beesmart.network.models.TaskStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DashboardAnalyticsCalculatorTest {
    private val today = LocalDate.of(2026, 6, 1)

    @Test
    fun `normalizes honey quantities and ignores unsupported records`() {
        val stats = calculate(
            extractions = listOf(
                extraction("2026-05-10", 12.0, "kg"),
                extraction("2026-05-11", 750.0, "g"),
                extraction("2026-05-12", 3.0, "litri"),
                extraction("2026-05-13", 2.0, "kg", ExtractionType.Wax)
            )
        )

        assertEquals(12.75, stats.honeyAnalytics.currentYearKg, 0.001)
        assertEquals(1, stats.honeyAnalytics.ignoredHoneyEntries)
    }

    @Test
    fun `forecasts season from harvested honey and active hive frames`() {
        val stats = calculate(
            hives = listOf(
                hive("active-1", HiveStatus.Active, honeyFrames = 4),
                hive("active-2", HiveStatus.Active, honeyFrames = 6, type = HiveType.Langstroth),
                hive("inactive", HiveStatus.Inactive, honeyFrames = 20)
            ),
            extractions = listOf(extraction("2026-04-10", 20.0, "kg"))
        )

        assertEquals(14.1, stats.honeyAnalytics.nextHarvestPotentialMinKg, 0.001)
        assertEquals(21.15, stats.honeyAnalytics.nextHarvestPotentialKg, 0.001)
        assertEquals(28.2, stats.honeyAnalytics.nextHarvestPotentialMaxKg, 0.001)
        assertEquals(34.1, stats.honeyAnalytics.seasonForecastMinKg, 0.001)
        assertEquals(41.15, stats.honeyAnalytics.seasonForecastKg, 0.001)
        assertEquals(48.2, stats.honeyAnalytics.seasonForecastMaxKg, 0.001)
        assertEquals(10.0, stats.honeyAnalytics.kgPerActiveHive, 0.001)
    }

    @Test
    fun `uses previous year seasonality before frame fallback`() {
        val stats = calculate(
            hives = listOf(hive("active", HiveStatus.Active, honeyFrames = 10)),
            extractions = listOf(
                extraction("2026-04-10", 10.0, "kg"),
                extraction("2026-06-01", 5.0, "kg"),
                extraction("2025-06-10", 30.0, "kg"),
                extraction("2025-07-10", 20.0, "kg"),
                extraction("2025-09-10", 10.0, "kg")
            )
        )

        assertEquals(true, stats.honeyAnalytics.usesSeasonalHistory)
        assertEquals(1, stats.honeyAnalytics.seasonalReferenceYears)
        assertEquals(25.0, stats.honeyAnalytics.nextHarvestPotentialMinKg, 0.001)
        assertEquals(25.0, stats.honeyAnalytics.nextHarvestPotentialKg, 0.001)
        assertEquals(25.0, stats.honeyAnalytics.nextHarvestPotentialMaxKg, 0.001)
        assertEquals(55.0, stats.honeyAnalytics.seasonalForecastRemainingKg, 0.001)
        assertEquals(70.0, stats.honeyAnalytics.seasonForecastMinKg, 0.001)
        assertEquals(70.0, stats.honeyAnalytics.seasonForecastKg, 0.001)
        assertEquals(70.0, stats.honeyAnalytics.seasonForecastMaxKg, 0.001)
    }

    @Test
    fun `marks fallback capacity for hive types without course reference`() {
        val stats = calculate(
            hives = listOf(hive("top-bar", HiveStatus.Active, honeyFrames = 2, type = HiveType.TopBar))
        )

        assertEquals(2.3, stats.honeyAnalytics.nextHarvestPotentialMinKg, 0.001)
        assertEquals(true, stats.honeyAnalytics.usesFallbackFrameCapacity)
    }

    @Test
    fun `creates six month chart and compares recent quarter year over year`() {
        val stats = calculate(
            extractions = listOf(
                extraction("2026-01-10", 10.0, "kg"),
                extraction("2026-02-10", 10.0, "kg"),
                extraction("2026-03-10", 10.0, "kg"),
                extraction("2026-04-10", 20.0, "kg"),
                extraction("2026-05-10", 20.0, "kg"),
                extraction("2026-06-01", 20.0, "kg"),
                extraction("2025-04-10", 10.0, "kg"),
                extraction("2025-05-10", 10.0, "kg"),
                extraction("2025-06-01", 10.0, "kg")
            )
        )

        assertEquals(6, stats.honeyAnalytics.monthlyProduction.size)
        assertEquals(100, stats.honeyAnalytics.trendPercent)
    }

    @Test
    fun `keeps trend unavailable without comparison history`() {
        val stats = calculate(extractions = listOf(extraction("2026-05-10", 12.0, "kg")))

        assertNull(stats.honeyAnalytics.trendPercent)
    }

    @Test
    fun `counts actionable and overdue tasks`() {
        val stats = calculate(
            tasks = listOf(
                task("pending", TaskStatus.Pending, "2026-05-31T12:00:00Z"),
                task("working", TaskStatus.InProgress, "2026-06-10T12:00:00Z"),
                task("done", TaskStatus.Completed, "2026-05-20T12:00:00Z")
            )
        )

        assertEquals(2, stats.pendingTasksCount)
        assertEquals(1, stats.overdueTasksCount)
    }

    private fun calculate(
        hives: List<HiveResponse> = emptyList(),
        tasks: List<TaskResponse> = emptyList(),
        extractions: List<HiveExtraction> = emptyList()
    ) = DashboardAnalyticsCalculator.calculate(
        hives = hives,
        tasks = tasks,
        inspections = emptyList(),
        extractions = extractions,
        today = today
    )

    private fun hive(
        id: String,
        status: HiveStatus,
        honeyFrames: Int,
        type: HiveType = HiveType.Dadant
    ) = HiveResponse(
        id = id,
        apiaryId = "apiary",
        apiaryName = "Stupina",
        name = id,
        type = type,
        status = status,
        notes = null,
        createdAt = "",
        updatedAt = "",
        rameMiere = honeyFrames
    )

    private fun extraction(
        date: String,
        quantity: Double,
        unit: String,
        type: ExtractionType = ExtractionType.Honey
    ) = HiveExtraction(
        id = "$date-$quantity",
        hiveId = "hive",
        apiaryId = "apiary",
        extractionDate = date,
        type = type,
        quantity = quantity,
        unit = unit,
        notes = null,
        createdAt = "",
        updatedAt = ""
    )

    private fun task(id: String, status: TaskStatus, dueDate: String) = TaskResponse(
        id = id,
        userId = "user",
        apiaryId = null,
        apiaryName = null,
        hiveId = null,
        hiveName = null,
        title = id,
        description = null,
        priority = TaskPriority.Normal,
        status = status,
        dueDate = dueDate,
        completedAt = null,
        createdAt = "",
        updatedAt = ""
    )
}

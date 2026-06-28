package com.example.beesmart.data.repository

import com.example.beesmart.network.models.HiveResponse
import com.example.beesmart.network.models.HiveStatus
import com.example.beesmart.network.models.HiveType
import com.example.beesmart.network.models.InspectionResponse
import com.example.beesmart.network.models.TaskPriority
import com.example.beesmart.network.models.TaskResponse
import com.example.beesmart.network.models.TaskStatus
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiaryIntelligenceCalculatorTest {
    private val today = LocalDate.of(2026, 6, 1)

    @Test
    fun `sick hive becomes critical with visible reason`() {
        val radar = calculate(
            hives = listOf(hive("hive-1", status = HiveStatus.Sick))
        )

        assertEquals(1, radar.criticalHivesCount)
        assertTrue(radar.priorities.single().reasons.any { it.contains("bolnav") })
    }

    @Test
    fun `recent active hive with confirmed queen and brood stays stable`() {
        val radar = calculate(
            hives = listOf(hive("hive-1", queenConfirmed = true, beeFrames = 8, broodFrames = 5)),
            inspections = listOf(inspection("hive-1", "2026-05-28T10:00:00Z"))
        )

        assertEquals(100, radar.healthScore)
        assertEquals(1, radar.stableHivesCount)
        assertTrue(radar.priorities.isEmpty())
    }

    @Test
    fun `missing queen confirmation and stale inspection raise watch priority`() {
        val radar = calculate(
            hives = listOf(hive("hive-1", queenConfirmed = false)),
            inspections = listOf(inspection("hive-1", "2026-05-01T10:00:00Z"))
        )

        assertEquals(1, radar.watchHivesCount)
        assertTrue(radar.priorities.single().reasons.any { it.contains("reginei") })
        assertTrue(radar.priorities.single().reasons.any { it.contains("30 de zile") })
    }

    @Test
    fun `eggs in latest inspection confirm queen indirectly`() {
        val radar = calculate(
            hives = listOf(hive("hive-1", queenConfirmed = false, beeFrames = 8)),
            inspections = listOf(
                inspection(
                    hiveId = "hive-1",
                    date = "2026-05-28T10:00:00Z",
                    eggsSeen = true
                )
            )
        )

        assertEquals(100, radar.healthScore)
        assertEquals(1, radar.stableHivesCount)
        assertTrue(radar.priorities.isEmpty())
    }

    @Test
    fun `overdue critical task raises intervention priority`() {
        val radar = calculate(
            hives = listOf(hive("hive-1", queenConfirmed = true, beeFrames = 8, broodFrames = 5)),
            tasks = listOf(task("hive-1", "2026-05-20T10:00:00Z", TaskPriority.Critical)),
            inspections = listOf(inspection("hive-1", "2026-05-28T10:00:00Z"))
        )

        assertEquals(1, radar.watchHivesCount)
        assertTrue(radar.priorities.single().reasons.any { it.contains("task critic") })
    }

    @Test
    fun `DeepBee warning participates in explainable score`() {
        val radar = calculate(
            hives = listOf(hive("hive-1", queenConfirmed = true, beeFrames = 8, broodFrames = 5)),
            inspections = listOf(inspection("hive-1", "2026-05-28T10:00:00Z")),
            aiSnapshots = listOf(
                AiHiveSnapshot(
                    hiveId = "hive-1",
                    inspectionDate = "2026-05-28T10:00:00Z",
                    level = BroodAnalyzer.Level.WARNING,
                    concerns = listOf("Raport larve/căpăcit în scădere"),
                    recommendations = listOf("Compară cu inspecția următoare")
                )
            )
        )

        val priority = radar.priorities.single()
        assertEquals(70, priority.healthScore)
        assertEquals("Compară cu inspecția următoare", priority.action)
        assertTrue(priority.reasons.any { it.contains("larve") })
        assertEquals(1, radar.deepBeeHivesCount)
    }

    @Test
    fun `inactive hives do not distort apiary score`() {
        val radar = calculate(
            hives = listOf(
                hive("active", queenConfirmed = true, beeFrames = 8, broodFrames = 5),
                hive("inactive", status = HiveStatus.Inactive)
            ),
            inspections = listOf(inspection("active", "2026-05-28T10:00:00Z"))
        )

        assertEquals(1, radar.monitoredHivesCount)
        assertEquals(100, radar.healthScore)
    }

    private fun calculate(
        hives: List<HiveResponse>,
        tasks: List<TaskResponse> = emptyList(),
        inspections: List<InspectionResponse> = emptyList(),
        aiSnapshots: List<AiHiveSnapshot> = emptyList()
    ) = ApiaryIntelligenceCalculator.calculate(
        hives = hives,
        tasks = tasks,
        inspections = inspections,
        aiSnapshots = aiSnapshots,
        today = today
    )

    private fun hive(
        id: String,
        status: HiveStatus = HiveStatus.Active,
        queenConfirmed: Boolean = false,
        beeFrames: Int = 0,
        broodFrames: Int = 0
    ) = HiveResponse(
        id = id,
        apiaryId = "apiary",
        apiaryName = "Stupina",
        name = id,
        type = HiveType.Dadant,
        status = status,
        notes = null,
        createdAt = "",
        updatedAt = "",
        reginaPrezenta = queenConfirmed,
        rameAlbine = beeFrames,
        ramePuiet = broodFrames
    )

    private fun inspection(
        hiveId: String,
        date: String,
        queenSeen: Boolean = false,
        eggsSeen: Boolean = false,
        larvaeSeen: Boolean = false,
        broodFrames: Int? = null
    ) = InspectionResponse(
        id = "$hiveId-$date",
        hiveId = hiveId,
        hiveName = hiveId,
        apiaryId = "apiary",
        apiaryName = "Stupina",
        inspectionDate = date,
        broodFrames = broodFrames,
        queenSeen = queenSeen,
        eggsSeen = eggsSeen,
        larvaeSeen = larvaeSeen,
        createdAt = date,
        updatedAt = date
    )

    private fun task(hiveId: String, dueDate: String, priority: TaskPriority) = TaskResponse(
        id = "$hiveId-$dueDate",
        userId = "user",
        apiaryId = "apiary",
        apiaryName = "Stupina",
        hiveId = hiveId,
        hiveName = hiveId,
        title = "Intervenție",
        description = null,
        priority = priority,
        status = TaskStatus.Pending,
        dueDate = dueDate,
        completedAt = null,
        createdAt = "",
        updatedAt = ""
    )
}

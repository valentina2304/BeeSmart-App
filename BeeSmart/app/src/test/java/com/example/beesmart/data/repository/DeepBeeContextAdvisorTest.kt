package com.example.beesmart.data.repository

import com.example.beesmart.network.models.HiveResponse
import com.example.beesmart.network.models.HiveStatus
import com.example.beesmart.network.models.HiveTreatment
import com.example.beesmart.network.models.HiveType
import com.example.beesmart.network.models.InspectionResponse
import com.example.beesmart.network.models.TaskPriority
import com.example.beesmart.network.models.TaskResponse
import com.example.beesmart.network.models.TaskStatus
import com.example.beesmart.network.models.TreatmentType
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepBeeContextAdvisorTest {
    private val today = LocalDate.of(2026, 6, 1)

    @Test
    fun `missing brood and unconfirmed queen produce cautious verification advice`() {
        val advice = advise(
            hive = hive(queenConfirmed = false),
            journalInspection = inspection(queenSeen = false),
            snapshots = listOf(
                snapshot(
                    totalCells = 120,
                    cappedBrood = 0,
                    larvae = 0,
                    eggs = 0
                )
            )
        )

        val queenAdvice = advice.single { it.category == AdviceCategory.QUEEN }
        assertEquals(AdvicePriority.IMPORTANT, queenAdvice.priority)
        assertEquals(false, queenAdvice.veterinaryReviewRecommended)
        assertTrue(queenAdvice.evidence.any { it.contains("0 celule de puiet") })
    }

    @Test
    fun `queenless status keeps queen verification urgent`() {
        val advice = advise(
            hive = hive(status = HiveStatus.Queenless),
            journalInspection = inspection(queenSeen = false),
            snapshots = listOf(snapshot(totalCells = 120, cappedBrood = 0, larvae = 0, eggs = 0))
        )

        assertEquals(AdvicePriority.URGENT, advice.single { it.category == AdviceCategory.QUEEN }.priority)
    }

    @Test
    fun `eggs in journal confirm queen indirectly`() {
        val advice = advise(
            hive = hive(queenConfirmed = false),
            journalInspection = inspection(queenSeen = false, eggsSeen = true),
            snapshots = listOf(snapshot())
        )

        assertTrue(advice.none { it.category == AdviceCategory.QUEEN })
    }

    @Test
    fun `low stores on meaningful frame produce nutrition verification`() {
        val advice = advise(
            hive = hive(queenConfirmed = true),
            snapshots = listOf(
                snapshot(
                    totalCells = 100,
                    cappedBrood = 40,
                    larvae = 20,
                    eggs = 5,
                    storesRatio = 0.03
                )
            )
        )

        val nutrition = advice.single { it.category == AdviceCategory.NUTRITION }
        assertEquals(AdvicePriority.IMPORTANT, nutrition.priority)
        assertTrue(nutrition.action.contains("ramele alaturate"))
    }

    @Test
    fun `spatial brood gaps produce brood distribution advice`() {
        val advice = advise(
            hive = hive(queenConfirmed = true),
            snapshots = listOf(
                snapshot(
                    totalCells = 140,
                    cappedBrood = 30,
                    larvae = 10,
                    eggs = 5,
                    broodGapRatio = 0.62,
                    broodCompactness = 0.34
                )
            )
        )

        val broodAdvice = advice.single { it.category == AdviceCategory.BROOD }
        assertEquals(AdvicePriority.IMPORTANT, broodAdvice.priority)
        assertTrue(broodAdvice.explanation.contains("Coordonatele DeepBee"))
        assertTrue(broodAdvice.evidence.any { it.contains("62%") })
    }

    @Test
    fun `central stores produce nutrition position advice`() {
        val advice = advise(
            hive = hive(queenConfirmed = true),
            snapshots = listOf(
                snapshot(
                    totalCells = 120,
                    cappedBrood = 20,
                    larvae = 8,
                    eggs = 4,
                    storesRatio = 0.18,
                    storesEdgeRatio = 0.12
                )
            )
        )

        val nutrition = advice.single { it.category == AdviceCategory.NUTRITION }
        assertEquals(AdvicePriority.WATCH, nutrition.priority)
        assertTrue(nutrition.title.contains("pozitia rezervelor"))
        assertTrue(nutrition.evidence.any { it.contains("12%") })
    }

    @Test
    fun `worsening brood spatial history produces longitudinal advice`() {
        val advice = advise(
            hive = hive(queenConfirmed = true),
            snapshots = listOf(
                snapshot(
                    inspectionDate = "2026-05-10T10:00:00Z",
                    broodDensity = 0.72,
                    broodCompactness = 0.82,
                    broodGapRatio = 0.18
                ),
                snapshot(
                    inspectionDate = "2026-05-30T10:00:00Z",
                    broodDensity = 0.56,
                    broodCompactness = 0.52,
                    broodGapRatio = 0.47
                )
            )
        )

        val brood = advice.single { it.title == "Urmareste evolutia compactitatii puietului" }
        assertEquals(AdvicePriority.IMPORTANT, brood.priority)
        assertTrue(brood.evidence.any { it.contains("2026-05-10 -> 2026-05-30") })
        assertTrue(brood.evidence.any { it.contains("Compactitate") })
        assertTrue(brood.action.contains("rama reprezentativa"))
    }

    @Test
    fun `declining pollen position history produces nutrition advice`() {
        val advice = advise(
            hive = hive(queenConfirmed = true),
            snapshots = listOf(
                snapshot(
                    inspectionDate = "2026-05-10T10:00:00Z",
                    storesRatio = 0.24,
                    storesEdgeRatio = 0.68,
                    pollen = 12,
                    pollenNearBroodRatio = 0.72
                ),
                snapshot(
                    inspectionDate = "2026-05-30T10:00:00Z",
                    storesRatio = 0.17,
                    storesEdgeRatio = 0.44,
                    pollen = 12,
                    pollenNearBroodRatio = 0.28
                )
            )
        )

        val nutrition = advice.single { it.title == "Compara rezervele si polenul in timp" }
        assertEquals(AdvicePriority.WATCH, nutrition.priority)
        assertTrue(nutrition.evidence.any { it.contains("Polen langa puiet") })
        assertTrue(nutrition.action.contains("coroana de miere"))
    }

    @Test
    fun `overdue planned treatment produces review advice without prescription`() {
        val advice = DeepBeeContextAdvisor.advise(
            context = DeepBeeHiveContext(
                hive = hive(queenConfirmed = true),
                inspections = listOf(inspection()),
                tasks = emptyList(),
                treatments = listOf(treatment(nextTreatmentDate = "2026-05-20")),
                extractions = emptyList(),
                aiSnapshots = listOf(snapshot())
            ),
            today = today
        )

        val treatmentAdvice = advice.single { it.category == AdviceCategory.TREATMENT }
        assertEquals(AdvicePriority.IMPORTANT, treatmentAdvice.priority)
        assertTrue(treatmentAdvice.action.contains("regulile locale"))
        assertTrue(treatmentAdvice.action.contains("inainte de orice aplicare"))
    }

    @Test
    fun `healthy hive with honey frames exposes harvest opportunity`() {
        val advice = advise(
            hive = hive(queenConfirmed = true, honeyFrames = 6),
            journalInspection = inspection(honeyFrames = 6, honeyCappingPercent = 50),
            snapshots = listOf(
                snapshot(
                    level = BroodAnalyzer.Level.HEALTHY,
                    storesRatio = 0.32
                )
            )
        )

        val opportunity = advice.single { it.category == AdviceCategory.PRODUCTION }
        assertEquals(AdvicePriority.OPPORTUNITY, opportunity.priority)
        assertTrue(opportunity.action.contains("cel putin o treime"))
        assertTrue(opportunity.evidence.any { it.contains("rame cu miere") })
    }

    @Test
    fun `harvest opportunity relies on AI signal not manual capping`() {
        // Manual capping is no longer collected; harvest is driven by honey frames,
        // the AI stores/health signal and extraction history.
        val advice = advise(
            hive = hive(queenConfirmed = true, honeyFrames = 6),
            journalInspection = inspection(honeyFrames = 6, honeyCappingPercent = 20),
            snapshots = listOf(
                snapshot(
                    level = BroodAnalyzer.Level.HEALTHY,
                    storesRatio = 0.32
                )
            )
        )

        assertTrue(advice.any { it.category == AdviceCategory.PRODUCTION })
    }

    @Test
    fun `space pressure alone does not create swarm advice without colony strength`() {
        val advice = advise(
            hive = hive(
                queenConfirmed = true,
                beeFrames = 4,
                broodFrames = 2,
                honeyFrames = 1
            ),
            journalInspection = inspection(
                framesCount = 10,
                broodFrames = 2,
                honeyFrames = 1,
                pollenFrames = 1,
                spaceNeeded = true
            ),
            snapshots = listOf(snapshot(totalCells = 80, cappedBrood = 10, larvae = 4, eggs = 1))
        )

        assertTrue(advice.none { it.category == AdviceCategory.SWARM })
    }

    @Test
    fun `strong crowded hive in swarm season produces cautious swarm verification`() {
        val advice = advise(
            hive = hive(
                queenConfirmed = true,
                queenAge = 3,
                beeFrames = 9,
                broodFrames = 6,
                honeyFrames = 5
            ),
            journalInspection = inspection(
                framesCount = 10,
                broodFrames = 6,
                honeyFrames = 4,
                pollenFrames = 1,
                queenCellsWithEggs = true,
                beardingAtEntrance = true
            ),
            snapshots = listOf(
                snapshot(
                    totalCells = 100,
                    cappedBrood = 45,
                    larvae = 18,
                    eggs = 6,
                    emptyCells = 6,
                    storesRatio = 0.30
                )
            )
        )

        val swarm = advice.single { it.category == AdviceCategory.SWARM }
        assertEquals(AdvicePriority.IMPORTANT, swarm.priority)
        assertTrue(swarm.action.contains("botcile"))
        assertTrue(swarm.explanation.contains("semnal de verificare"))
        assertTrue(swarm.evidence.any { it.contains("barba sau aglomerare") })
    }

    @Test
    fun `summer seasonal calendar suggests an assisted task when none is planned`() {
        val advice = DeepBeeContextAdvisor.advise(
            context = DeepBeeHiveContext(
                hive = hive(queenConfirmed = true),
                inspections = listOf(inspection()),
                tasks = emptyList(),
                treatments = listOf(treatment(nextTreatmentDate = "2026-07-01")),
                extractions = emptyList(),
                aiSnapshots = listOf(snapshot())
            ),
            today = today
        )

        val seasonal = advice.single { it.title == "Calendar sezonier: spatiu, roire si cules" }
        assertEquals(AdvicePriority.WATCH, seasonal.priority)
        assertTrue(seasonal.action.contains("Propune un task"))
        assertTrue(seasonal.evidence.any { it.contains("botci cu oua") })
    }

    @Test
    fun `seasonal calendar is not repeated when beekeeper already has a seasonal task`() {
        val advice = DeepBeeContextAdvisor.advise(
            context = DeepBeeHiveContext(
                hive = hive(queenConfirmed = true),
                inspections = listOf(inspection()),
                tasks = listOf(task(title = "Calendar sezonier: spatiu si cules")),
                treatments = listOf(treatment(nextTreatmentDate = "2026-07-01")),
                extractions = emptyList(),
                aiSnapshots = listOf(snapshot())
            ),
            today = today
        )

        assertTrue(advice.none { it.title.startsWith("Calendar sezonier") })
    }

    @Test
    fun `digest ignores inactive hives and sorts urgent advice first`() {
        val digest = DeepBeeContextAdvisor.digest(
            hives = listOf(
                hive(id = "urgent", status = HiveStatus.Queenless),
                hive(id = "inactive", status = HiveStatus.Inactive)
            ),
            tasks = emptyList(),
            inspections = listOf(inspection(hiveId = "urgent", queenSeen = false)),
            treatments = emptyList(),
            extractions = emptyList(),
            aiSnapshots = listOf(
                snapshot(
                    hiveId = "urgent",
                    totalCells = 80,
                    cappedBrood = 0,
                    larvae = 0,
                    eggs = 0
                )
            ),
            today = today
        )

        assertEquals(1, digest.urgentCount)
        assertEquals("urgent", digest.advice.first().hiveId)
        assertTrue(digest.advice.none { it.hiveId == "inactive" })
    }

    private fun advise(
        hive: HiveResponse,
        journalInspection: InspectionResponse = inspection(hiveId = hive.id),
        snapshots: List<AiHiveSnapshot>
    ) = DeepBeeContextAdvisor.advise(
        context = DeepBeeHiveContext(
            hive = hive,
            inspections = listOf(journalInspection),
            tasks = emptyList(),
            treatments = emptyList(),
            extractions = emptyList(),
            aiSnapshots = snapshots
        ),
        today = today
    )

    private fun hive(
        id: String = "hive",
        status: HiveStatus = HiveStatus.Active,
        queenConfirmed: Boolean = false,
        queenAge: Int = 0,
        beeFrames: Int = 8,
        broodFrames: Int = 4,
        honeyFrames: Int = 0
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
        varstaRegina = queenAge,
        rameAlbine = beeFrames,
        ramePuiet = broodFrames,
        rameMiere = honeyFrames
    )

    private fun inspection(
        hiveId: String = "hive",
        queenSeen: Boolean = true,
        eggsSeen: Boolean = false,
        larvaeSeen: Boolean = false,
        framesCount: Int? = null,
        broodFrames: Int? = null,
        honeyFrames: Int? = null,
        pollenFrames: Int? = null,
        spaceNeeded: Boolean = false,
        queenCellsWithEggs: Boolean = false,
        beardingAtEntrance: Boolean = false,
        honeyCappingPercent: Int? = null
    ) = InspectionResponse(
        id = "inspection-$hiveId",
        hiveId = hiveId,
        hiveName = hiveId,
        apiaryId = "apiary",
        apiaryName = "Stupina",
        inspectionDate = "2026-05-30T10:00:00Z",
        framesCount = framesCount,
        broodFrames = broodFrames,
        honeyFrames = honeyFrames,
        pollenFrames = pollenFrames,
        queenSeen = queenSeen,
        eggsSeen = eggsSeen,
        larvaeSeen = larvaeSeen,
        createdAt = "",
        updatedAt = "",
        spaceNeeded = spaceNeeded,
        queenCellsWithEggs = queenCellsWithEggs,
        beardingAtEntrance = beardingAtEntrance,
        honeyCappingPercent = honeyCappingPercent
    )

    private fun snapshot(
        hiveId: String = "hive",
        level: BroodAnalyzer.Level = BroodAnalyzer.Level.ATTENTION,
        totalCells: Int = 100,
        cappedBrood: Int = 50,
        larvae: Int = 20,
        eggs: Int = 5,
        emptyCells: Int = 0,
        storesRatio: Double? = 0.20,
        broodDensity: Double? = null,
        broodCompactness: Double? = null,
        broodGapRatio: Double? = null,
        storesEdgeRatio: Double? = null,
        pollen: Int = 0,
        pollenNearBroodRatio: Double? = null,
        inspectionDate: String = "2026-05-30T10:00:00Z"
    ) = AiHiveSnapshot(
        hiveId = hiveId,
        inspectionDate = inspectionDate,
        level = level,
        concerns = emptyList(),
        recommendations = emptyList(),
        totalCells = totalCells,
        cappedBroodCells = cappedBrood,
        larvaeCells = larvae,
        eggsCells = eggs,
        pollenCells = pollen,
        emptyCells = emptyCells,
        broodDensity = broodDensity,
        storesRatio = storesRatio,
        broodCompactness = broodCompactness,
        broodGapRatio = broodGapRatio,
        storesEdgeRatio = storesEdgeRatio,
        pollenNearBroodRatio = pollenNearBroodRatio,
        larvaeToCappedRatio = if (cappedBrood == 0) null else larvae.toDouble() / cappedBrood
    )

    private fun treatment(nextTreatmentDate: String) = HiveTreatment(
        id = "treatment",
        hiveId = "hive",
        apiaryId = "apiary",
        treatmentDate = "2026-05-01",
        type = TreatmentType.Preventive,
        productName = "Produs jurnal",
        substance = null,
        dosage = null,
        notes = null,
        nextTreatmentDate = nextTreatmentDate,
        createdAt = "",
        updatedAt = ""
    )

    private fun task(title: String) = TaskResponse(
        id = "task",
        userId = "user",
        apiaryId = "apiary",
        apiaryName = "Stupina",
        hiveId = "hive",
        hiveName = "hive",
        title = title,
        description = null,
        priority = TaskPriority.Normal,
        status = TaskStatus.Pending,
        dueDate = null,
        completedAt = null,
        createdAt = "",
        updatedAt = ""
    )
}

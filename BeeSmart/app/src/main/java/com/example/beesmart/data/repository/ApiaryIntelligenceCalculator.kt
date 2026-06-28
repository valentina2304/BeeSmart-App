package com.example.beesmart.data.repository

import com.example.beesmart.network.models.HiveResponse
import com.example.beesmart.network.models.HiveStatus
import com.example.beesmart.network.models.InspectionResponse
import com.example.beesmart.network.models.TaskPriority
import com.example.beesmart.network.models.TaskResponse
import com.example.beesmart.network.models.TaskStatus
import java.time.LocalDate

data class ApiaryRadar(
    val healthScore: Int = 0,
    val monitoredHivesCount: Int = 0,
    val stableHivesCount: Int = 0,
    val watchHivesCount: Int = 0,
    val criticalHivesCount: Int = 0,
    val inspectionCoveragePercent: Int = 0,
    val deepBeeHivesCount: Int = 0,
    val priorities: List<HivePriority> = emptyList()
)

data class HivePriority(
    val hiveId: String,
    val hiveName: String,
    val apiaryName: String,
    val healthScore: Int,
    val level: HiveRiskLevel,
    val action: String,
    val reasons: List<String>
)

data class AiHiveSnapshot(
    val hiveId: String,
    val inspectionDate: String,
    val level: BroodAnalyzer.Level,
    val concerns: List<String>,
    val recommendations: List<String>,
    val totalCells: Int = 0,
    val cappedBroodCells: Int = 0,
    val larvaeCells: Int = 0,
    val eggsCells: Int = 0,
    val honeyCells: Int = 0,
    val pollenCells: Int = 0,
    val emptyCells: Int = 0,
    val broodDensity: Double? = null,
    val larvaeToCappedRatio: Double? = null,
    val storesRatio: Double? = null,
    val broodCompactness: Double? = null,
    val broodGapRatio: Double? = null,
    val storesEdgeRatio: Double? = null,
    val pollenNearBroodRatio: Double? = null
)

enum class HiveRiskLevel {
    STABLE,
    WATCH,
    CRITICAL
}

/**
 * Produces an explainable intervention queue from locally available apiary data.
 * This is decision support, not a veterinary diagnostic model: every penalty is
 * surfaced as a readable reason and can be checked by the beekeeper.
 */
object ApiaryIntelligenceCalculator {

    fun calculate(
        hives: List<HiveResponse>,
        tasks: List<TaskResponse>,
        inspections: List<InspectionResponse>,
        aiSnapshots: List<AiHiveSnapshot> = emptyList(),
        today: LocalDate = LocalDate.now()
    ): ApiaryRadar {
        val monitoredHives = hives.filter { it.status != HiveStatus.Inactive }
        if (monitoredHives.isEmpty()) return ApiaryRadar()

        val latestInspectionByHive = inspections
            .mapNotNull { inspection ->
                inspection.inspectionDate.toLocalDateOrNull()?.let { date -> inspection to date }
            }
            .groupBy { (inspection, _) -> inspection.hiveId }
            .mapValues { (_, datedInspections) ->
                datedInspections.maxByOrNull { (_, date) -> date }!!.first
            }
        val latestAiByHive = aiSnapshots
            .groupBy { it.hiveId }
            .mapValues { (_, snapshots) ->
                snapshots.maxByOrNull { it.inspectionDate.toLocalDateOrNull() ?: LocalDate.MIN }!!
            }

        val priorities = monitoredHives.map { hive ->
            evaluateHive(
                hive = hive,
                tasks = tasks,
                latestInspection = latestInspectionByHive[hive.id],
                fallbackInspectionDate = hive.ultimaInspectie.toLocalDateOrNull(),
                aiSnapshot = latestAiByHive[hive.id],
                today = today
            )
        }
        val inspectedHives = monitoredHives.count { hive ->
            latestInspectionByHive[hive.id] != null || hive.ultimaInspectie.toLocalDateOrNull() != null
        }

        return ApiaryRadar(
            healthScore = priorities.map { it.healthScore }.average().toInt(),
            monitoredHivesCount = monitoredHives.size,
            stableHivesCount = priorities.count { it.level == HiveRiskLevel.STABLE },
            watchHivesCount = priorities.count { it.level == HiveRiskLevel.WATCH },
            criticalHivesCount = priorities.count { it.level == HiveRiskLevel.CRITICAL },
            inspectionCoveragePercent = (inspectedHives * 100) / monitoredHives.size,
            deepBeeHivesCount = latestAiByHive.keys.count { hiveId -> monitoredHives.any { it.id == hiveId } },
            priorities = priorities
                .filter { it.level != HiveRiskLevel.STABLE }
                .sortedWith(compareBy<HivePriority> { it.healthScore }.thenBy { it.hiveName })
        )
    }

    private fun evaluateHive(
        hive: HiveResponse,
        tasks: List<TaskResponse>,
        latestInspection: InspectionResponse?,
        fallbackInspectionDate: LocalDate?,
        aiSnapshot: AiHiveSnapshot?,
        today: LocalDate
    ): HivePriority {
        var riskPoints = 0
        val reasons = mutableListOf<String>()
        val actions = mutableListOf<String>()
        val latestInspectionDate = latestInspection?.inspectionDate.toLocalDateOrNull()
            ?: fallbackInspectionDate
        val queenConfirmed = hive.reginaPrezenta ||
            latestInspection?.queenSeen == true ||
            latestInspection?.eggsSeen == true ||
            latestInspection?.larvaeSeen == true ||
            (aiSnapshot?.eggsCells ?: 0) > 0 ||
            (aiSnapshot?.larvaeCells ?: 0) > 0
        val broodConfirmed = (latestInspection?.broodFrames ?: hive.ramePuiet) > 0 ||
            latestInspection?.eggsSeen == true ||
            latestInspection?.larvaeSeen == true ||
            (aiSnapshot?.cappedBroodCells ?: 0) > 0 ||
            (aiSnapshot?.larvaeCells ?: 0) > 0 ||
            (aiSnapshot?.eggsCells ?: 0) > 0

        when (hive.status) {
            HiveStatus.Sick -> {
                riskPoints += 70
                reasons += "Stup marcat bolnav"
                actions += "Verifică simptomele și izolează riscul"
            }
            HiveStatus.Queenless -> {
                riskPoints += 60
                reasons += "Stup marcat fără regină"
                actions += "Confirmă situația reginei și planifică remedierea"
            }
            HiveStatus.Weak -> {
                riskPoints += 35
                reasons += "Colonie marcată slabă"
                actions += "Evaluează hrana, puietul și populația de albine"
            }
            HiveStatus.Preparing -> {
                riskPoints += 10
                reasons += "Stup încă în pregătire"
                actions += "Finalizează verificarea înainte de activare"
            }
            HiveStatus.Active,
            HiveStatus.Inactive -> Unit
        }

        if (hive.status == HiveStatus.Active && !queenConfirmed) {
            riskPoints += 25
            reasons += "Prezența reginei nu este confirmată"
            actions += "Confirmă regina la următoarea inspecție"
        }
        if (hive.status == HiveStatus.Active && hive.rameAlbine > 0 && !broodConfirmed) {
            riskPoints += 20
            reasons += "Nu sunt raportate rame cu puiet"
            actions += "Verifică puietul și continuitatea pontei"
        }
        if (hive.varstaRegina > 2) {
            riskPoints += 10
            reasons += "Regina are peste 2 ani"
            actions += "Urmărește ritmul pontei și planifică schimbarea reginei"
        }

        when {
            latestInspectionDate == null -> {
                riskPoints += 15
                reasons += "Nu există inspecții în jurnal"
                actions += "Înregistrează o inspecție de bază"
            }
            latestInspectionDate.isBefore(today.minusDays(30)) -> {
                riskPoints += 25
                reasons += "Ultima inspecție are peste 30 de zile"
                actions += "Programează o inspecție prioritară"
            }
            latestInspectionDate.isBefore(today.minusDays(14)) -> {
                riskPoints += 12
                reasons += "Ultima inspecție are peste 14 zile"
                actions += "Programează o inspecție de control"
            }
        }

        val hiveTasks = tasks.filter { task ->
            task.hiveId == hive.id &&
                (task.status == TaskStatus.Pending || task.status == TaskStatus.InProgress)
        }
        val overdueTasks = hiveTasks.count { it.dueDate.toLocalDateOrNull()?.isBefore(today) == true }
        if (overdueTasks > 0) {
            riskPoints += (overdueTasks * 10).coerceAtMost(20)
            reasons += "$overdueTasks task-uri întârziate"
            actions += "Rezolvă intervențiile întârziate"
        }
        if (hiveTasks.any { it.priority == TaskPriority.Critical }) {
            riskPoints += 15
            reasons += "Există un task critic deschis"
            actions += "Prioritizează task-ul critic"
        }

        when (aiSnapshot?.level) {
            BroodAnalyzer.Level.WARNING -> {
                riskPoints += 30
                reasons += aiSnapshot.concerns.firstOrNull() ?: "DeepBee indică un semnal de avertizare"
                actions += aiSnapshot.recommendations.firstOrNull() ?: "Revizuiește analiza DeepBee"
            }
            BroodAnalyzer.Level.ATTENTION -> {
                riskPoints += 15
                reasons += aiSnapshot.concerns.firstOrNull() ?: "DeepBee indică necesar de monitorizare"
                actions += aiSnapshot.recommendations.firstOrNull() ?: "Compară cu următoarea analiză DeepBee"
            }
            BroodAnalyzer.Level.HEALTHY,
            null -> Unit
        }

        val healthScore = (100 - riskPoints).coerceIn(0, 100)
        val level = when {
            healthScore < 50 -> HiveRiskLevel.CRITICAL
            healthScore < 80 -> HiveRiskLevel.WATCH
            else -> HiveRiskLevel.STABLE
        }

        return HivePriority(
            hiveId = hive.id,
            hiveName = hive.name,
            apiaryName = hive.apiaryName,
            healthScore = healthScore,
            level = level,
            action = actions.firstOrNull() ?: "Continuă monitorizarea de rutină",
            reasons = reasons.ifEmpty { listOf("Nu sunt semnale de risc înregistrate") }
        )
    }

    private fun String?.toLocalDateOrNull(): LocalDate? {
        if (this == null || length < 10) return null
        return runCatching { LocalDate.parse(take(10)) }.getOrNull()
    }
}

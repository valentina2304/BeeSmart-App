package com.example.beesmart.data.repository

import com.example.beesmart.data.local.AppDatabase
import com.example.beesmart.data.local.entity.InspectionEntity
import com.example.beesmart.network.models.CellDetection
import com.example.beesmart.network.models.UserProfile
import com.example.beesmart.utils.SessionManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeRepository @Inject constructor(
    private val sessionManager: SessionManager,
    private val appDatabase: AppDatabase,
    private val userProfileRepository: UserProfileRepository,
    private val moshi: Moshi
) {
    private val aiCountsAdapter by lazy {
        moshi.adapter<Map<String, Int>>(
            Types.newParameterizedType(Map::class.java, String::class.java, Integer::class.javaObjectType)
        )
    }
    private val aiCellDetectionsAdapter by lazy {
        moshi.adapter<List<CellDetection>>(
            Types.newParameterizedType(List::class.java, CellDetection::class.java)
        )
    }

    suspend fun getUserProfile(): Result<UserProfile> =
        userProfileRepository.getUserProfile()

    suspend fun getCachedUserProfile(): UserProfile? =
        userProfileRepository.getCachedUserProfile()

    fun extractUserName(profile: UserProfile): String {
        val firstName = profile.firstName?.takeIf { it.isNotBlank() }
        val lastName = profile.lastName?.takeIf { it.isNotBlank() }
        return when {
            firstName != null && lastName != null -> "$firstName $lastName"
            firstName != null -> firstName
            lastName != null -> lastName
            else -> profile.email.substringBefore('@')
        }
    }

    suspend fun getDashboardStats(): Result<DashboardStats> = withContext(Dispatchers.IO) {
        try {
            val inspections = appDatabase.inspectionDao().getAll()
            Result.Success(
                DashboardAnalyticsCalculator.calculate(
                    hives = appDatabase.hiveDao().getAll().map { it.toHiveResponse() },
                    tasks = appDatabase.taskDao().getAll().map { it.toTaskResponse() },
                    inspections = inspections.map { it.toInspectionResponse() },
                    extractions = appDatabase.extractionDao().getAll().map { it.toHiveExtraction() },
                    treatments = appDatabase.treatmentDao().getAll().map { it.toHiveTreatment() },
                    aiSnapshots = getAiHiveSnapshots(inspections)
                )
            )
        } catch (e: Exception) {
            Result.Error("Statisticile locale nu au putut fi calculate", exception = e)
        }
    }

    private suspend fun getAiHiveSnapshots(inspections: List<InspectionEntity>): List<AiHiveSnapshot> {
        val inspectionsById = mutableMapOf<String, InspectionEntity>()
        inspections.forEach { inspection ->
            inspectionsById[inspection.localId] = inspection
            inspection.serverId?.let { inspectionsById[it] = inspection }
        }

        return appDatabase.inspectionAiAnalysisDao().getAll().mapNotNull { analysis ->
            val inspection = inspectionsById[analysis.inspectionLocalId]
                ?: analysis.inspectionServerId?.let(inspectionsById::get)
                ?: return@mapNotNull null
            val rawCounts = runCatching { aiCountsAdapter.fromJson(analysis.rawCountsJson) }
                .getOrNull()
                ?: return@mapNotNull null
            val cellDetections = runCatching { aiCellDetectionsAdapter.fromJson(analysis.cellDetectionsJson) }
                .getOrNull()
                ?: emptyList()
            val report = BroodAnalyzer.analyze(rawCounts, cellDetections)
            val metrics = report.metrics
            val spatial = report.spatial
            val totals = metrics.totals
            AiHiveSnapshot(
                hiveId = inspection.hiveServerId ?: inspection.hiveLocalId,
                inspectionDate = inspection.inspectionDate,
                level = report.verdict.level,
                concerns = report.verdict.concerns,
                recommendations = report.verdict.recommendations,
                totalCells = metrics.total,
                cappedBroodCells = totals[BroodAnalyzer.Category.CAPPED_BROOD] ?: 0,
                larvaeCells = totals[BroodAnalyzer.Category.LARVAE] ?: 0,
                eggsCells = totals[BroodAnalyzer.Category.EGGS] ?: 0,
                honeyCells = totals[BroodAnalyzer.Category.HONEY] ?: 0,
                pollenCells = totals[BroodAnalyzer.Category.POLLEN] ?: 0,
                emptyCells = totals[BroodAnalyzer.Category.EMPTY] ?: 0,
                broodDensity = metrics.broodDensity.takeIf { it.isFinite() },
                larvaeToCappedRatio = metrics.larvaeToCappedRatio.takeIf { it.isFinite() },
                storesRatio = metrics.storesRatio.takeIf { it.isFinite() },
                broodCompactness = spatial.broodCompactness.takeIf { it.isFinite() },
                broodGapRatio = spatial.broodGapRatio.takeIf { it.isFinite() },
                storesEdgeRatio = spatial.storesEdgeRatio.takeIf { it.isFinite() },
                pollenNearBroodRatio = spatial.pollenNearBroodRatio.takeIf { it.isFinite() }
            )
        }
    }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            appDatabase.clearAllTables()
            sessionManager.clearSession()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Deconectarea a eșuat", null, e)
        }
    }
}

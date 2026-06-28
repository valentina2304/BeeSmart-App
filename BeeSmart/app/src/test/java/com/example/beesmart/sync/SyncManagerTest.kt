package com.example.beesmart.sync

import com.example.beesmart.data.local.dao.*
import com.example.beesmart.data.local.entity.*
import com.example.beesmart.network.*
import com.example.beesmart.network.models.*
import com.example.beesmart.utils.SessionManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response
import java.io.IOException

class SyncManagerTest {

    @MockK lateinit var apiaryDao: ApiaryDao
    @MockK lateinit var hiveDao: HiveDao
    @MockK lateinit var taskDao: TaskDao
    @MockK lateinit var treatmentDao: TreatmentDao
    @MockK lateinit var extractionDao: ExtractionDao
    @MockK lateinit var inspectionDao: InspectionDao
    @MockK lateinit var inspectionPhotoDao: InspectionPhotoDao
    @MockK lateinit var syncQueueDao: SyncQueueDao
    @MockK lateinit var apiaryApi: ApiaryApi
    @MockK lateinit var hiveApi: HiveApi
    @MockK lateinit var taskApi: TaskApi
    @MockK lateinit var treatmentApi: TreatmentApi
    @MockK lateinit var extractionApi: ExtractionApi
    @MockK lateinit var inspectionApi: InspectionApi
    @MockK lateinit var authApi: AuthApi
    @MockK(relaxed = true) lateinit var sessionManager: SessionManager
    @MockK(relaxed = true) lateinit var analysisDao: com.example.beesmart.data.local.dao.InspectionAiAnalysisDao

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private lateinit var syncManager: SyncManager

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        coEvery { syncQueueDao.getPendingCount() } returns 0
        coEvery { sessionManager.markServerSyncNow() } returns Unit
        syncManager = SyncManager(
            apiaryDao, hiveDao, taskDao, treatmentDao, extractionDao, inspectionDao,
            inspectionPhotoDao, syncQueueDao, analysisDao,
            apiaryApi, hiveApi, taskApi, treatmentApi, extractionApi, inspectionApi,
            authApi, sessionManager, moshi
        )
    }

    // ==================== processQueue — empty queue ====================

    @Test
    fun `processQueue does nothing when queue is empty`() = runTest {
        coEvery { syncQueueDao.getAll() } returns emptyList()

        syncManager.processQueue()

        coVerify(exactly = 0) { apiaryApi.createApiary(any()) }
        coVerify(exactly = 0) { syncQueueDao.deleteById(any()) }
    }

    // ==================== CREATE APIARY ====================

    @Test
    fun `processQueue CREATE APIARY syncs to server and marks SYNCED`() = runTest {
        val payload = moshi.adapter(CreateApiaryRequest::class.java).toJson(CreateApiaryRequest("Livada"))
        val op = queueOp(1L, "CREATE", "APIARY", "loc-1", null, payload)
        val entity = apiaryEntity("loc-1", null)
        val serverResp = apiaryResponse("srv-1", "Livada")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { apiaryDao.getByLocalId("loc-1") } returns entity
        coEvery { apiaryApi.createApiary(any()) } returns Response.success(serverResp)
        coEvery { apiaryDao.update(any()) } returns Unit
        coEvery { syncQueueDao.deleteById(1L) } returns Unit

        syncManager.processQueue()

        coVerify { apiaryApi.createApiary(any()) }
        coVerify { apiaryDao.update(match { it.serverId == "srv-1" && it.syncStatus == SyncStatus.SYNCED }) }
        coVerify { syncQueueDao.deleteById(1L) }
    }

    @Test
    fun `processQueue CREATE APIARY API error bumps retry count`() = runTest {
        val payload = moshi.adapter(CreateApiaryRequest::class.java).toJson(CreateApiaryRequest("Livada"))
        val op = queueOp(1L, "CREATE", "APIARY", "loc-1", null, payload, retryCount = 0)
        val entity = apiaryEntity("loc-1", null)

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { apiaryDao.getByLocalId("loc-1") } returns entity
        coEvery { apiaryApi.createApiary(any()) } returns
            Response.error(500, "server error".toResponseBody("application/json".toMediaType()))
        coEvery { syncQueueDao.update(any()) } returns Unit

        syncManager.processQueue()

        coVerify { syncQueueDao.update(match { it.retryCount == 1 }) }
        coVerify(exactly = 0) { syncQueueDao.deleteById(any()) }
    }

    @Test
    fun `processQueue CREATE APIARY at max retries marks entity SYNC_FAILED`() = runTest {
        val payload = moshi.adapter(CreateApiaryRequest::class.java).toJson(CreateApiaryRequest("Livada"))
        val op = queueOp(1L, "CREATE", "APIARY", "loc-1", null, payload, retryCount = 3)
        val entity = apiaryEntity("loc-1", null)

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { apiaryDao.getByLocalId("loc-1") } returns entity
        coEvery { apiaryDao.update(any()) } returns Unit

        syncManager.processQueue()

        coVerify { apiaryDao.update(match { it.syncStatus == SyncStatus.SYNC_FAILED }) }
        coVerify(exactly = 0) { apiaryApi.createApiary(any()) }
    }

    @Test
    fun `processQueue CREATE APIARY IOException does not bump retry`() = runTest {
        val payload = moshi.adapter(CreateApiaryRequest::class.java).toJson(CreateApiaryRequest("Livada"))
        val op = queueOp(1L, "CREATE", "APIARY", "loc-1", null, payload)
        val entity = apiaryEntity("loc-1", null)

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { apiaryDao.getByLocalId("loc-1") } returns entity
        coEvery { apiaryApi.createApiary(any()) } throws IOException("connection refused")

        syncManager.processQueue()

        coVerify(exactly = 0) { syncQueueDao.update(any()) }
        coVerify(exactly = 0) { syncQueueDao.deleteById(any()) }
    }

    // ==================== CREATE HIVE ====================

    @Test
    fun `processQueue CREATE HIVE defers when parent apiary not yet synced`() = runTest {
        val payload = moshi.adapter(CreateHiveRequest::class.java)
            .toJson(CreateHiveRequest("Stupul 1", HiveType.Langstroth))
        val op = queueOp(2L, "CREATE", "HIVE", "hive-loc-1", null, payload)
        val hiveEntity = hiveEntity("hive-loc-1", null, "apiary-loc-1")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { hiveDao.getByLocalId("hive-loc-1") } returns hiveEntity
        coEvery { apiaryDao.getByLocalId("apiary-loc-1") } returns apiaryEntity("apiary-loc-1", null)
        coEvery { apiaryDao.getByServerId("apiary-loc-1") } returns null

        syncManager.processQueue()

        coVerify(exactly = 0) { hiveApi.createHive(any(), any()) }
        coVerify(exactly = 0) { syncQueueDao.deleteById(any()) }
    }

    @Test
    fun `processQueue CREATE HIVE syncs when parent apiary is synced`() = runTest {
        val payload = moshi.adapter(CreateHiveRequest::class.java)
            .toJson(CreateHiveRequest("Stupul 1", HiveType.Langstroth))
        val op = queueOp(2L, "CREATE", "HIVE", "hive-loc-1", null, payload)
        val hiveEntity = hiveEntity("hive-loc-1", null, "apiary-loc-1")
        val syncedApiary = apiaryEntity("apiary-loc-1", "apiary-srv-1")
        val serverHive = hiveResponse("hive-srv-1", "apiary-srv-1", "Stupul 1")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { hiveDao.getByLocalId("hive-loc-1") } returns hiveEntity
        coEvery { apiaryDao.getByLocalId("apiary-loc-1") } returns syncedApiary
        coEvery { hiveApi.createHive("apiary-srv-1", any()) } returns Response.success(serverHive)
        coEvery { hiveDao.update(any()) } returns Unit
        coEvery { syncQueueDao.deleteById(2L) } returns Unit

        syncManager.processQueue()

        coVerify { hiveApi.createHive("apiary-srv-1", any()) }
        coVerify { hiveDao.update(match { it.serverId == "hive-srv-1" && it.syncStatus == SyncStatus.SYNCED }) }
        coVerify { syncQueueDao.deleteById(2L) }
    }

    @Test
    fun `processQueue CREATE HIVE drops queue entry when entity deleted locally`() = runTest {
        val payload = moshi.adapter(CreateHiveRequest::class.java)
            .toJson(CreateHiveRequest("Stupul 1", HiveType.Langstroth))
        val op = queueOp(2L, "CREATE", "HIVE", "hive-loc-1", null, payload)

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { hiveDao.getByLocalId("hive-loc-1") } returns null
        coEvery { syncQueueDao.deleteById(2L) } returns Unit

        syncManager.processQueue()

        coVerify { syncQueueDao.deleteById(2L) }
        coVerify(exactly = 0) { hiveApi.createHive(any(), any()) }
    }

    // ==================== CREATE TASK ====================

    @Test
    fun `processQueue CREATE TASK syncs to server`() = runTest {
        val payload = moshi.adapter(CreateTaskRequest::class.java)
            .toJson(CreateTaskRequest("Tratament varroa", priority = TaskPriority.High))
        val op = queueOp(3L, "CREATE", "TASK", "task-loc-1", null, payload)
        val serverTask = taskResponse("task-srv-1", "Tratament varroa")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { taskApi.createTask(any()) } returns Response.success(serverTask)
        coEvery { taskDao.getByLocalId("task-loc-1") } returns taskEntity("task-loc-1", null, "Tratament varroa")
        coEvery { taskDao.update(any()) } returns Unit
        coEvery { syncQueueDao.deleteById(3L) } returns Unit

        syncManager.processQueue()

        coVerify { taskApi.createTask(any()) }
        coVerify { taskDao.update(match { it.serverId == "task-srv-1" && it.syncStatus == SyncStatus.SYNCED }) }
        coVerify { syncQueueDao.deleteById(3L) }
    }

    @Test
    fun `processQueue CREATE TASK defers when parent hive not yet synced`() = runTest {
        val payload = moshi.adapter(CreateTaskRequest::class.java)
            .toJson(CreateTaskRequest("Tratament", hiveId = "hive-loc-1"))
        val op = queueOp(3L, "CREATE", "TASK", "task-loc-1", null, payload)

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { hiveDao.getByLocalId("hive-loc-1") } returns hiveEntity("hive-loc-1", null, "apiary-1")
        coEvery { hiveDao.getByServerId("hive-loc-1") } returns null

        syncManager.processQueue()

        coVerify(exactly = 0) { taskApi.createTask(any()) }
        coVerify(exactly = 0) { syncQueueDao.deleteById(any()) }
    }

    // ==================== CREATE TREATMENT ====================

    @Test
    fun `processQueue CREATE TREATMENT syncs when hive is synced`() = runTest {
        val treatEntity = treatmentEntity("treat-loc-1", null, "hive-loc-1")
        val payload = moshi.adapter(CreateTreatmentRequest::class.java)
            .toJson(CreateTreatmentRequest("hive-loc-1", "2024-05-01", TreatmentType.Varroa, "Apivar", null, null, null, null))
        val op = queueOp(4L, "CREATE", "TREATMENT", "treat-loc-1", null, payload)
        val syncedHive = hiveEntity("hive-loc-1", "hive-srv-1", "apiary-1")
        val serverTreatment = treatmentResponse("treat-srv-1", "hive-srv-1")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { treatmentDao.getByLocalId("treat-loc-1") } returns treatEntity
        coEvery { hiveDao.getByLocalId("hive-loc-1") } returns syncedHive
        coEvery { treatmentApi.createTreatment(any()) } returns Response.success(serverTreatment)
        coEvery { treatmentDao.update(any()) } returns Unit
        coEvery { syncQueueDao.deleteById(4L) } returns Unit

        syncManager.processQueue()

        coVerify { treatmentApi.createTreatment(match { it.hiveId == "hive-srv-1" }) }
        coVerify { treatmentDao.update(match { it.serverId == "treat-srv-1" && it.syncStatus == SyncStatus.SYNCED }) }
        coVerify { syncQueueDao.deleteById(4L) }
    }

    @Test
    fun `processQueue CREATE TREATMENT defers when parent hive not synced`() = runTest {
        val treatEntity = treatmentEntity("treat-loc-1", null, "hive-loc-1")
        val payload = moshi.adapter(CreateTreatmentRequest::class.java)
            .toJson(CreateTreatmentRequest("hive-loc-1", "2024-05-01", TreatmentType.Varroa, "Apivar", null, null, null, null))
        val op = queueOp(4L, "CREATE", "TREATMENT", "treat-loc-1", null, payload)
        val unsyncedHive = hiveEntity("hive-loc-1", null, "apiary-1")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { treatmentDao.getByLocalId("treat-loc-1") } returns treatEntity
        coEvery { hiveDao.getByLocalId("hive-loc-1") } returns unsyncedHive
        coEvery { hiveDao.getByServerId("hive-loc-1") } returns null

        syncManager.processQueue()

        coVerify(exactly = 0) { treatmentApi.createTreatment(any()) }
        coVerify(exactly = 0) { syncQueueDao.deleteById(any()) }
    }

    // ==================== CREATE EXTRACTION ====================

    @Test
    fun `processQueue CREATE EXTRACTION syncs when hive is synced`() = runTest {
        val extractEntity = extractionEntity("ext-loc-1", null, "hive-loc-1")
        val payload = moshi.adapter(CreateExtractionRequest::class.java)
            .toJson(CreateExtractionRequest("hive-loc-1", "2024-07-01", ExtractionType.Honey, 5.5, "kg", null))
        val op = queueOp(5L, "CREATE", "EXTRACTION", "ext-loc-1", null, payload)
        val syncedHive = hiveEntity("hive-loc-1", "hive-srv-1", "apiary-1")
        val serverExtraction = extractionResponse("ext-srv-1", "hive-srv-1")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { extractionDao.getByLocalId("ext-loc-1") } returns extractEntity
        coEvery { hiveDao.getByLocalId("hive-loc-1") } returns syncedHive
        coEvery { extractionApi.createExtraction(any()) } returns Response.success(serverExtraction)
        coEvery { extractionDao.update(any()) } returns Unit
        coEvery { syncQueueDao.deleteById(5L) } returns Unit

        syncManager.processQueue()

        coVerify { extractionApi.createExtraction(match { it.hiveId == "hive-srv-1" }) }
        coVerify { extractionDao.update(match { it.serverId == "ext-srv-1" && it.syncStatus == SyncStatus.SYNCED }) }
        coVerify { syncQueueDao.deleteById(5L) }
    }

    // ==================== CREATE INSPECTION ====================

    @Test
    fun `processQueue CREATE INSPECTION syncs when hive is synced`() = runTest {
        val inspEntity = inspectionEntity("insp-loc-1", null, "hive-loc-1")
        val payload = moshi.adapter(CreateInspectionRequest::class.java)
            .toJson(CreateInspectionRequest("hive-loc-1", "2024-08-15T10:00:00Z"))
        val op = queueOp(6L, "CREATE", "INSPECTION", "insp-loc-1", null, payload)
        val syncedHive = hiveEntity("hive-loc-1", "hive-srv-1", "apiary-1")
        val serverInspection = inspectionResponse("insp-srv-1", "hive-srv-1")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { inspectionDao.getByLocalId("insp-loc-1") } returns inspEntity
        coEvery { hiveDao.getByLocalId("hive-loc-1") } returns syncedHive
        coEvery { inspectionApi.createInspection(any()) } returns Response.success(serverInspection)
        coEvery { inspectionDao.update(any()) } returns Unit
        coEvery { analysisDao.bindServerId("insp-loc-1", "insp-srv-1") } returns Unit
        coEvery { inspectionPhotoDao.bindInspectionServerId("insp-loc-1", "insp-srv-1") } returns Unit
        coEvery { syncQueueDao.deleteById(6L) } returns Unit

        syncManager.processQueue()

        coVerify { inspectionApi.createInspection(match { it.hiveId == "hive-srv-1" }) }
        coVerify { inspectionDao.update(match { it.serverId == "insp-srv-1" && it.syncStatus == SyncStatus.SYNCED }) }
        coVerify { syncQueueDao.deleteById(6L) }
    }

    @Test
    fun `processQueue CREATE INSPECTION_AI_ANALYSIS defers until inspection has server id`() = runTest {
        val payload = moshi.adapter(QueuedAiAnalysisCreate::class.java)
            .toJson(
                QueuedAiAnalysisCreate(
                    inspectionLocalId = "insp-loc-1",
                    request = SaveInspectionAiAnalysisRequest(mapOf("Capped" to 4))
                )
            )
        val op = queueOp(7L, "CREATE", "INSPECTION_AI_ANALYSIS", "ai-analysis-insp-loc-1", null, payload)
        val inspEntity = inspectionEntity("insp-loc-1", null, "hive-loc-1")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { inspectionDao.getByLocalId("insp-loc-1") } returns inspEntity

        syncManager.processQueue()

        coVerify(exactly = 0) { inspectionApi.saveAiAnalysis(any(), any()) }
        coVerify(exactly = 0) { syncQueueDao.deleteById(7L) }
    }

    @Test
    fun `processQueue CREATE INSPECTION_AI_ANALYSIS posts analysis when inspection is synced`() = runTest {
        val request = SaveInspectionAiAnalysisRequest(
            results = mapOf("Capped" to 4, "Nectar" to 2),
            cellDetections = listOf(
                CellDetection(
                    x = 120,
                    y = 240,
                    radius = 18,
                    normalizedX = 0.25,
                    normalizedY = 0.5,
                    normalizedRadius = 0.02,
                    className = "Eggs",
                    confidence = 0.93
                )
            )
        )
        val payload = moshi.adapter(QueuedAiAnalysisCreate::class.java)
            .toJson(
                QueuedAiAnalysisCreate(
                    inspectionLocalId = "insp-loc-1",
                    request = request
                )
            )
        val op = queueOp(7L, "CREATE", "INSPECTION_AI_ANALYSIS", "ai-analysis-insp-loc-1", null, payload)
        val inspEntity = inspectionEntity("insp-loc-1", "insp-srv-1", "hive-loc-1")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { inspectionDao.getByLocalId("insp-loc-1") } returns inspEntity
        coEvery { inspectionApi.saveAiAnalysis("insp-srv-1", any()) } returns Response.success(aiAnalysisResponse())
        coEvery { syncQueueDao.deleteById(7L) } returns Unit

        syncManager.processQueue()

        coVerify {
            inspectionApi.saveAiAnalysis(
                "insp-srv-1",
                match {
                    it.results["Capped"] == 4 &&
                        it.results["Nectar"] == 2 &&
                        it.cellDetections.single().className == "Eggs"
                }
            )
        }
        coVerify { syncQueueDao.deleteById(7L) }
    }

    // ==================== UPDATE operations ====================

    @Test
    fun `processQueue UPDATE APIARY syncs to server and marks SYNCED`() = runTest {
        val payload = moshi.adapter(UpdateApiaryRequest::class.java).toJson(UpdateApiaryRequest("Updated"))
        val op = queueOp(10L, "UPDATE", "APIARY", "loc-1", "srv-1", payload)
        val entity = apiaryEntity("loc-1", "srv-1")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { apiaryApi.updateApiary("srv-1", any()) } returns Response.success(apiaryResponse("srv-1", "Updated"))
        coEvery { apiaryDao.getByLocalId("loc-1") } returns entity
        coEvery { apiaryDao.update(any()) } returns Unit
        coEvery { syncQueueDao.deleteById(10L) } returns Unit

        syncManager.processQueue()

        coVerify { apiaryApi.updateApiary("srv-1", any()) }
        coVerify { apiaryDao.update(match { it.syncStatus == SyncStatus.SYNCED }) }
        coVerify { syncQueueDao.deleteById(10L) }
    }

    @Test
    fun `processQueue UPDATE defers when serverId is null`() = runTest {
        val payload = moshi.adapter(UpdateApiaryRequest::class.java).toJson(UpdateApiaryRequest("Updated"))
        val op = queueOp(10L, "UPDATE", "APIARY", "loc-1", null, payload)

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { apiaryDao.getByLocalId("loc-1") } returns apiaryEntity("loc-1", null)
        coEvery { apiaryDao.getByServerId("loc-1") } returns null

        syncManager.processQueue()

        coVerify(exactly = 0) { apiaryApi.updateApiary(any(), any()) }
        coVerify(exactly = 0) { syncQueueDao.deleteById(any()) }
    }

    @Test
    fun `processQueue UPDATE HIVE syncs to server`() = runTest {
        val payload = moshi.adapter(UpdateHiveRequest::class.java)
            .toJson(UpdateHiveRequest("New Name", HiveType.Dadant, HiveStatus.Active))
        val op = queueOp(11L, "UPDATE", "HIVE", "hive-loc-1", "hive-srv-1", payload)
        val entity = hiveEntity("hive-loc-1", "hive-srv-1", "apiary-1")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { hiveApi.updateHive("hive-srv-1", any()) } returns Response.success(hiveResponse("hive-srv-1", "apiary-srv-1", "New Name"))
        coEvery { hiveDao.getByLocalId("hive-loc-1") } returns entity
        coEvery { hiveDao.update(any()) } returns Unit
        coEvery { syncQueueDao.deleteById(11L) } returns Unit

        syncManager.processQueue()

        coVerify { hiveApi.updateHive("hive-srv-1", any()) }
        coVerify { syncQueueDao.deleteById(11L) }
    }

    @Test
    fun `processQueue UPDATE TASK syncs to server`() = runTest {
        val payload = moshi.adapter(UpdateTaskRequest::class.java)
            .toJson(UpdateTaskRequest("Updated", priority = TaskPriority.High, status = TaskStatus.Pending))
        val op = queueOp(12L, "UPDATE", "TASK", "task-loc-1", "task-srv-1", payload)
        val entity = taskEntity("task-loc-1", "task-srv-1", "Task")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { taskApi.updateTask("task-srv-1", any()) } returns Response.success(taskResponse("task-srv-1", "Updated"))
        coEvery { taskDao.getByLocalId("task-loc-1") } returns entity
        coEvery { taskDao.update(any()) } returns Unit
        coEvery { syncQueueDao.deleteById(12L) } returns Unit

        syncManager.processQueue()

        coVerify { taskApi.updateTask("task-srv-1", any()) }
        coVerify { syncQueueDao.deleteById(12L) }
    }

    @Test
    fun `processQueue UPDATE INSPECTION_PHOTO resolves server id from cache`() = runTest {
        val payload = moshi.adapter(UpdateInspectionPhotoRequest::class.java)
            .toJson(UpdateInspectionPhotoRequest("New description"))
        val op = queueOp(13L, "UPDATE", "INSPECTION_PHOTO", "photo-loc-1", "photo-loc-1", payload)
        val cached = InspectionPhotoEntity(
            localId = "photo-loc-1",
            serverId = "photo-srv-1",
            inspectionLocalId = "insp-loc-1",
            inspectionServerId = "insp-srv-1",
            photoUrl = "https://example.com/photo.jpg",
            description = "Old description",
            createdAt = "2024-08-15T10:00:00Z"
        )
        val server = InspectionPhotoResponse(
            id = "photo-srv-1",
            inspectionId = "insp-srv-1",
            photoUrl = "https://example.com/photo.jpg",
            description = "New description",
            createdAt = "2024-08-15T10:00:00Z"
        )

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { inspectionPhotoDao.getById("photo-loc-1") } returns cached
        coEvery { inspectionApi.updatePhoto("photo-srv-1", any()) } returns Response.success(server)
        coEvery { inspectionPhotoDao.update(any()) } returns Unit
        coEvery { syncQueueDao.deleteById(13L) } returns Unit

        syncManager.processQueue()

        coVerify { inspectionApi.updatePhoto("photo-srv-1", any()) }
        coVerify {
            inspectionPhotoDao.update(match {
                it.serverId == "photo-srv-1" &&
                    it.description == "New description" &&
                    it.syncStatus == SyncStatus.SYNCED
            })
        }
        coVerify { syncQueueDao.deleteById(13L) }
    }

    // ==================== DELETE operations ====================

    @Test
    fun `processQueue DELETE APIARY calls API and removes queue entry`() = runTest {
        val op = queueOp(20L, "DELETE", "APIARY", "loc-1", "srv-1", "")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { apiaryApi.deleteApiary("srv-1") } returns Response.success(Unit)
        coEvery { apiaryDao.deleteByLocalId("loc-1") } returns Unit
        coEvery { syncQueueDao.deleteById(20L) } returns Unit

        syncManager.processQueue()

        coVerify { apiaryApi.deleteApiary("srv-1") }
        coVerify { apiaryDao.deleteByLocalId("loc-1") }
        coVerify { syncQueueDao.deleteById(20L) }
    }

    @Test
    fun `processQueue DELETE without serverId just removes queue entry`() = runTest {
        val op = queueOp(20L, "DELETE", "APIARY", "loc-1", null, "")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { syncQueueDao.deleteById(20L) } returns Unit

        syncManager.processQueue()

        coVerify { syncQueueDao.deleteById(20L) }
        coVerify(exactly = 0) { apiaryApi.deleteApiary(any()) }
    }

    @Test
    fun `processQueue DELETE HIVE calls API`() = runTest {
        val op = queueOp(21L, "DELETE", "HIVE", "hive-loc-1", "hive-srv-1", "")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { hiveApi.deleteHive("hive-srv-1") } returns Response.success(Unit)
        coEvery { hiveDao.deleteByLocalId("hive-loc-1") } returns Unit
        coEvery { syncQueueDao.deleteById(21L) } returns Unit

        syncManager.processQueue()

        coVerify { hiveApi.deleteHive("hive-srv-1") }
        coVerify { hiveDao.deleteByLocalId("hive-loc-1") }
        coVerify { syncQueueDao.deleteById(21L) }
    }

    @Test
    fun `processQueue DELETE TASK calls API`() = runTest {
        val op = queueOp(22L, "DELETE", "TASK", "task-loc-1", "task-srv-1", "")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { taskApi.deleteTask("task-srv-1") } returns Response.success(Unit)
        coEvery { taskDao.deleteByLocalId("task-loc-1") } returns Unit
        coEvery { syncQueueDao.deleteById(22L) } returns Unit

        syncManager.processQueue()

        coVerify { taskApi.deleteTask("task-srv-1") }
        coVerify { taskDao.deleteByLocalId("task-loc-1") }
        coVerify { syncQueueDao.deleteById(22L) }
    }

    @Test
    fun `processQueue DELETE TREATMENT calls API`() = runTest {
        val op = queueOp(23L, "DELETE", "TREATMENT", "treat-loc-1", "treat-srv-1", "")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { treatmentApi.deleteTreatment("treat-srv-1") } returns Response.success(Unit)
        coEvery { treatmentDao.deleteByLocalId("treat-loc-1") } returns Unit
        coEvery { syncQueueDao.deleteById(23L) } returns Unit

        syncManager.processQueue()

        coVerify { treatmentApi.deleteTreatment("treat-srv-1") }
        coVerify { treatmentDao.deleteByLocalId("treat-loc-1") }
        coVerify { syncQueueDao.deleteById(23L) }
    }

    @Test
    fun `processQueue DELETE INSPECTION calls API`() = runTest {
        val op = queueOp(25L, "DELETE", "INSPECTION", "insp-loc-1", "insp-srv-1", "")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { inspectionApi.deleteInspection("insp-srv-1") } returns Response.success(Unit)
        coEvery { inspectionDao.deleteByLocalId("insp-loc-1") } returns Unit
        coEvery { syncQueueDao.deleteById(25L) } returns Unit

        syncManager.processQueue()

        coVerify { inspectionApi.deleteInspection("insp-srv-1") }
        coVerify { inspectionDao.deleteByLocalId("insp-loc-1") }
        coVerify { syncQueueDao.deleteById(25L) }
    }

    @Test
    fun `processQueue DELETE INSPECTION_PHOTO calls API`() = runTest {
        val op = queueOp(26L, "DELETE", "INSPECTION_PHOTO", "photo-loc-1", "photo-srv-1", "")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { inspectionPhotoDao.getById("photo-loc-1") } returns null
        coEvery { inspectionApi.deletePhoto("photo-srv-1") } returns Response.success(Unit)
        coEvery { inspectionPhotoDao.deleteById("photo-loc-1") } returns Unit
        coEvery { syncQueueDao.deleteById(26L) } returns Unit

        syncManager.processQueue()

        coVerify { inspectionApi.deletePhoto("photo-srv-1") }
        coVerify { syncQueueDao.deleteById(26L) }
    }

    @Test
    fun `processQueue DELETE IOException does not bump retry`() = runTest {
        val op = queueOp(20L, "DELETE", "APIARY", "loc-1", "srv-1", "")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { apiaryApi.deleteApiary("srv-1") } throws IOException("connection refused")

        syncManager.processQueue()

        coVerify(exactly = 0) { syncQueueDao.update(any()) }
        coVerify(exactly = 0) { syncQueueDao.deleteById(any()) }
    }

    // ==================== COMPLETE / UNCOMPLETE TASK ====================

    @Test
    fun `processQueue COMPLETE TASK calls completeTask API and marks SYNCED`() = runTest {
        val op = queueOp(30L, "COMPLETE", "TASK", "task-loc-1", "task-srv-1", "")
        val entity = taskEntity("task-loc-1", "task-srv-1", "Task")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { taskApi.completeTask("task-srv-1") } returns Response.success(taskResponse("task-srv-1", "Task", TaskStatus.Completed))
        coEvery { taskDao.getByLocalId("task-loc-1") } returns entity
        coEvery { taskDao.update(any()) } returns Unit
        coEvery { syncQueueDao.deleteById(30L) } returns Unit

        syncManager.processQueue()

        coVerify { taskApi.completeTask("task-srv-1") }
        coVerify { taskDao.update(match { it.syncStatus == SyncStatus.SYNCED }) }
        coVerify { syncQueueDao.deleteById(30L) }
    }

    @Test
    fun `processQueue UNCOMPLETE TASK calls uncompleteTask API and marks SYNCED`() = runTest {
        val op = queueOp(31L, "UNCOMPLETE", "TASK", "task-loc-1", "task-srv-1", "")
        val entity = taskEntity("task-loc-1", "task-srv-1", "Task")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { taskApi.uncompleteTask("task-srv-1") } returns Response.success(taskResponse("task-srv-1", "Task", TaskStatus.Pending))
        coEvery { taskDao.getByLocalId("task-loc-1") } returns entity
        coEvery { taskDao.update(any()) } returns Unit
        coEvery { syncQueueDao.deleteById(31L) } returns Unit

        syncManager.processQueue()

        coVerify { taskApi.uncompleteTask("task-srv-1") }
        coVerify { syncQueueDao.deleteById(31L) }
    }

    @Test
    fun `processQueue COMPLETE TASK defers when serverId null`() = runTest {
        val op = queueOp(30L, "COMPLETE", "TASK", "task-loc-1", null, "")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { taskDao.getByLocalId("task-loc-1") } returns taskEntity("task-loc-1", null, "Task")

        syncManager.processQueue()

        coVerify(exactly = 0) { taskApi.completeTask(any()) }
        coVerify(exactly = 0) { syncQueueDao.deleteById(any()) }
    }

    // ==================== retry exhaustion ====================

    @Test
    fun `processQueue UPDATE at max retries marks entity SYNC_FAILED`() = runTest {
        val payload = moshi.adapter(UpdateApiaryRequest::class.java).toJson(UpdateApiaryRequest("X"))
        val op = queueOp(10L, "UPDATE", "APIARY", "loc-1", "srv-1", payload, retryCount = 3)
        val entity = apiaryEntity("loc-1", "srv-1")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { apiaryDao.getByLocalId("loc-1") } returns entity
        coEvery { apiaryDao.update(any()) } returns Unit

        syncManager.processQueue()

        coVerify { apiaryDao.update(match { it.syncStatus == SyncStatus.SYNC_FAILED }) }
        coVerify(exactly = 0) { apiaryApi.updateApiary(any(), any()) }
    }

    @Test
    fun `processQueue DELETE at max retries marks entity SYNC_FAILED and stops`() = runTest {
        val op = queueOp(20L, "DELETE", "APIARY", "loc-1", "srv-1", "", retryCount = 3)
        val entity = apiaryEntity("loc-1", "srv-1")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { apiaryDao.getByLocalId("loc-1") } returns entity
        coEvery { apiaryDao.update(any()) } returns Unit

        syncManager.processQueue()

        coVerify(exactly = 0) { apiaryApi.deleteApiary(any()) }
    }

    // ==================== malformed payload ====================

    @Test
    fun `processQueue malformed CREATE payload drops queue entry without crashing`() = runTest {
        val op = queueOp(99L, "CREATE", "APIARY", "loc-1", null, "NOT_VALID_JSON")

        coEvery { syncQueueDao.getAll() } returns listOf(op)
        coEvery { syncQueueDao.deleteById(99L) } returns Unit

        syncManager.processQueue()

        coVerify { syncQueueDao.deleteById(99L) }
        coVerify(exactly = 0) { apiaryApi.createApiary(any()) }
    }

    // ==================== helpers ====================

    private fun queueOp(
        id: Long,
        operationType: String,
        entityType: String,
        entityLocalId: String,
        entityServerId: String?,
        payload: String,
        retryCount: Int = 0
    ) = SyncQueueEntity(
        id = id,
        operationType = operationType,
        entityType = entityType,
        entityLocalId = entityLocalId,
        entityServerId = entityServerId,
        payload = payload,
        retryCount = retryCount
    )

    private fun apiaryEntity(localId: String, serverId: String?) =
        ApiaryEntity(localId, serverId, "Livada", null, null, syncStatus = SyncStatus.PENDING_CREATE)

    private fun hiveEntity(localId: String, serverId: String?, apiaryLocalId: String) =
        HiveEntity(
            localId, serverId, apiaryLocalId, serverId?.let { "apiary-srv" },
            "Livada", "Stupul", HiveType.Langstroth.name, HiveStatus.Active.name, null,
            SyncStatus.PENDING_CREATE
        )

    private fun taskEntity(localId: String, serverId: String?, title: String) =
        TaskEntity(localId, serverId, null, null, null, null, title, null,
            TaskPriority.Normal.name, TaskStatus.Pending.name, null, null, SyncStatus.PENDING_CREATE)

    private fun treatmentEntity(localId: String, serverId: String?, hiveLocalId: String) =
        TreatmentEntity(localId, serverId, hiveLocalId, serverId?.let { "hive-srv" },
            "apiary-1", "2024-05-01", TreatmentType.Varroa.name, "Apivar", null, null, null, null,
            SyncStatus.PENDING_CREATE)

    private fun extractionEntity(localId: String, serverId: String?, hiveLocalId: String) =
        ExtractionEntity(localId, serverId, hiveLocalId, serverId?.let { "hive-srv" },
            "apiary-1", "2024-07-01", ExtractionType.Honey.name, 5.5, "kg", null, SyncStatus.PENDING_CREATE)

    private fun inspectionEntity(localId: String, serverId: String?, hiveLocalId: String) =
        InspectionEntity(localId, serverId, hiveLocalId, serverId?.let { "hive-srv" },
            "Stupul", "apiary-1", "Livada", "2024-08-15T10:00:00Z",
            25.0, 10, 4, 3, 2, true, true, true, 0, "2024-08-15T10:00:00Z",
            SyncStatus.PENDING_CREATE)

    private fun apiaryResponse(id: String, name: String) =
        ApiaryResponse(id, "user-1", name, null, null, 0, "", "")

    private fun hiveResponse(id: String, apiaryId: String, name: String) =
        HiveResponse(id, apiaryId, "Livada", name, HiveType.Langstroth, HiveStatus.Active, null, "", "")

    private fun taskResponse(id: String, title: String, status: TaskStatus = TaskStatus.Pending) =
        TaskResponse(id, "user-1", null, null, null, null, title, null,
            TaskPriority.Normal, status, null, null, "", "")

    private fun treatmentResponse(id: String, hiveId: String) =
        HiveTreatment(id, hiveId, "apiary-1", "2024-05-01", TreatmentType.Varroa, "Apivar", null, null, null, null, "", "")

    private fun extractionResponse(id: String, hiveId: String) =
        HiveExtraction(id, hiveId, "apiary-1", "2024-07-01", ExtractionType.Honey, 5.5, "kg", null, "", "")

    private fun inspectionResponse(id: String, hiveId: String) =
        InspectionResponse(id, hiveId, "Stupul", "apiary-1", "Livada",
            "2024-08-15T10:00:00Z", null, null, null, null, null, false, false, false, 0, "", "")

    private fun aiAnalysisResponse() =
        InspectionAiAnalysisResponse(
            id = "analysis-srv-1",
            inspectionId = "insp-srv-1",
            hiveId = "hive-srv-1",
            apiaryId = "apiary-1",
            inspectionDate = "2024-08-15T10:00:00Z",
            status = "success",
            results = mapOf("Capped" to 4, "Nectar" to 2),
            message = null,
            totalCells = 6,
            cappedBroodCells = 4,
            larvaeCells = 0,
            eggsCells = 0,
            honeyCells = 2,
            pollenCells = 0,
            emptyCells = 0,
            otherCells = 0,
            broodCells = 4,
            storesCells = 2,
            broodDensity = 0.67,
            larvaeToCappedRatio = 0.0,
            storesRatio = 0.33,
            createdAt = "2024-08-15T10:01:00Z"
        )
}

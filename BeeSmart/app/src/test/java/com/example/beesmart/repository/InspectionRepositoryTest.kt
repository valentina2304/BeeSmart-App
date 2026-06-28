package com.example.beesmart.repository

import com.example.beesmart.data.local.dao.HiveDao
import com.example.beesmart.data.local.dao.InspectionAiAnalysisDao
import com.example.beesmart.data.local.dao.InspectionDao
import com.example.beesmart.data.local.dao.InspectionPhotoDao
import com.example.beesmart.data.local.dao.SyncQueueDao
import com.example.beesmart.data.local.entity.HiveEntity
import com.example.beesmart.data.local.entity.InspectionEntity
import com.example.beesmart.data.local.entity.InspectionPhotoEntity
import com.example.beesmart.data.local.entity.SyncQueueEntity
import com.example.beesmart.data.local.entity.SyncStatus
import com.example.beesmart.data.repository.InspectionRepository
import com.example.beesmart.data.repository.Result
import com.example.beesmart.network.BackendReachability
import com.example.beesmart.network.InspectionApi
import com.example.beesmart.network.models.*
import com.example.beesmart.sync.ConnectivityObserver
import com.example.beesmart.sync.QueuedAiAnalysisCreate
import com.example.beesmart.sync.QueuedPhotoCreate
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

class InspectionRepositoryTest {

    @MockK lateinit var inspectionApi: InspectionApi
    @MockK lateinit var inspectionDao: InspectionDao
    @MockK lateinit var inspectionPhotoDao: InspectionPhotoDao
    @MockK lateinit var inspectionAiAnalysisDao: InspectionAiAnalysisDao
    @MockK lateinit var hiveDao: HiveDao
    @MockK lateinit var syncQueueDao: SyncQueueDao
    @MockK lateinit var connectivity: ConnectivityObserver
    @MockK lateinit var backendReachability: BackendReachability

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private lateinit var repository: InspectionRepository

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        repository = InspectionRepository(
            inspectionApi, inspectionDao, inspectionPhotoDao, inspectionAiAnalysisDao,
            hiveDao, syncQueueDao,
            connectivity, backendReachability, moshi
        )
    }

    // ==================== CREATE ====================

    @Test
    fun `createInspection offline inserts with PENDING_CREATE and queues sync`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns false
        every { backendReachability.isLikelyUnreachable() } returns false
        val hive = hiveEntity("hive-loc-1", null, "apiary-1")
        coEvery { hiveDao.getByLocalId("hive-loc-1") } returns hive
        coEvery { hiveDao.getByServerId("hive-loc-1") } returns null
        coEvery { inspectionDao.insert(any()) } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val request = CreateInspectionRequest(
            hiveId = "hive-loc-1",
            inspectionDate = "2024-08-15T10:00:00Z",
            queenSeen = true,
            framesCount = 10
        )
        val result = repository.createInspection(request)

        assertTrue(result is Result.Success)
        coVerify {
            inspectionDao.insert(match {
                it.hiveLocalId == "hive-loc-1" &&
                it.serverId == null &&
                it.syncStatus == SyncStatus.PENDING_CREATE
            })
        }
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "CREATE" && it.entityType == "INSPECTION"
            })
        }
        coVerify(exactly = 0) { inspectionApi.createInspection(any()) }
    }

    @Test
    fun `createInspection backend unreachable falls back to offline queue`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns true
        every { backendReachability.isLikelyUnreachable() } returns true
        val hive = hiveEntity("hive-loc-1", null, "apiary-1")
        coEvery { hiveDao.getByLocalId("hive-loc-1") } returns hive
        coEvery { hiveDao.getByServerId("hive-loc-1") } returns null
        coEvery { inspectionDao.insert(any()) } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val request = CreateInspectionRequest("hive-loc-1", "2024-08-15T10:00:00Z")
        val result = repository.createInspection(request)

        assertTrue(result is Result.Success)
        coVerify { inspectionDao.insert(any()) }
        coVerify(exactly = 0) { inspectionApi.createInspection(any()) }
    }

    @Test
    fun `createInspection IOException during online attempt falls back to offline queue`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns true
        every { backendReachability.isLikelyUnreachable() } returns false
        val hive = hiveEntity("hive-loc-1", null, "apiary-1")
        coEvery { hiveDao.getByLocalId("hive-loc-1") } returns hive
        coEvery { hiveDao.getByServerId("hive-loc-1") } returns null
        coEvery { inspectionApi.createInspection(any()) } throws IOException("Connection refused")
        coEvery { inspectionDao.insert(any()) } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val request = CreateInspectionRequest("hive-loc-1", "2024-08-15T10:00:00Z")
        val result = repository.createInspection(request)

        assertTrue(result is Result.Success)
        coVerify { inspectionDao.insert(match { it.syncStatus == SyncStatus.PENDING_CREATE }) }
        coVerify {
            syncQueueDao.insert(match { it.operationType == "CREATE" && it.entityType == "INSPECTION" })
        }
    }

    @Test
    fun `createInspection online success saves synced entity`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns true
        every { backendReachability.isLikelyUnreachable() } returns false
        val serverResp = inspectionResponse("srv-insp-1", "hive-srv-1")
        coEvery { inspectionApi.createInspection(any()) } returns Response.success(serverResp)
        coEvery { inspectionDao.insert(any()) } returns Unit

        val result = repository.createInspection(CreateInspectionRequest("hive-srv-1", "2024-08-15T10:00:00Z"))

        assertTrue(result is Result.Success)
        assertEquals("srv-insp-1", (result as Result.Success).data.id)
        coVerify { inspectionDao.insert(match { it.serverId == "srv-insp-1" && it.syncStatus == SyncStatus.SYNCED }) }
    }

    // ==================== READ ====================

    @Test
    fun `getAllInspections offline returns cached data`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns false
        every { backendReachability.isLikelyUnreachable() } returns false
        coEvery { inspectionDao.getAll() } returns listOf(inspectionEntity("loc-1", null, "hive-1"))

        val result = repository.getAllInspections()

        assertTrue(result is Result.Success)
        assertEquals(1, (result as Result.Success).data.size)
        coVerify(exactly = 0) { inspectionApi.getAllInspections() }
    }

    @Test
    fun `getInspectionsByHiveId offline returns cached inspections`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns false
        every { backendReachability.isLikelyUnreachable() } returns false
        val cached = listOf(inspectionEntity("loc-1", null, "hive-1"))
        coEvery { inspectionDao.getByHiveId("hive-1") } returns cached

        val result = repository.getInspectionsByHiveId("hive-1")

        assertTrue(result is Result.Success)
        assertEquals(1, (result as Result.Success).data.size)
        coVerify(exactly = 0) { inspectionApi.getInspectionsByHiveId(any()) }
    }

    // ==================== UPDATE ====================

    @Test
    fun `updateInspection offline updates entity and queues UPDATE`() = runTest {
        val existing = inspectionEntity("loc-1", "srv-1", "hive-1", syncStatus = SyncStatus.SYNCED)
        every { connectivity.isCurrentlyOnline() } returns false
        every { backendReachability.isLikelyUnreachable() } returns false
        coEvery { inspectionDao.getByLocalId("loc-1") } returns existing
        coEvery { inspectionDao.update(any()) } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val request = UpdateInspectionRequest("2024-09-01T10:00:00Z", queenSeen = true, framesCount = 12)
        val result = repository.updateInspection("loc-1", request)

        assertTrue(result is Result.Success)
        coVerify {
            inspectionDao.update(match { it.syncStatus == SyncStatus.PENDING_UPDATE })
        }
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "UPDATE" && it.entityType == "INSPECTION" && it.entityServerId == "srv-1"
            })
        }
    }

    @Test
    fun `updateInspection offline PENDING_CREATE entity updates without adding extra queue entry`() = runTest {
        val pendingCreate = inspectionEntity("loc-1", null, "hive-1", syncStatus = SyncStatus.PENDING_CREATE)
        val createAdapter = moshi.adapter(CreateInspectionRequest::class.java)
        val createOp = SyncQueueEntity(
            id = 5L,
            operationType = "CREATE",
            entityType = "INSPECTION",
            entityLocalId = "loc-1",
            entityServerId = null,
            payload = createAdapter.toJson(
                CreateInspectionRequest(
                    hiveId = "hive-1",
                    inspectionDate = "2024-08-01T10:00:00Z"
                )
            )
        )
        every { connectivity.isCurrentlyOnline() } returns false
        every { backendReachability.isLikelyUnreachable() } returns false
        coEvery { inspectionDao.getByLocalId("loc-1") } returns pendingCreate
        coEvery { inspectionDao.update(any()) } returns Unit
        coEvery {
            syncQueueDao.getLatestForEntity("CREATE", "INSPECTION", "loc-1")
        } returns createOp
        coEvery { syncQueueDao.update(any()) } returns Unit

        val result = repository.updateInspection("loc-1", UpdateInspectionRequest("2024-09-01T10:00:00Z"))

        assertTrue(result is Result.Success)
        coVerify { inspectionDao.update(match { it.syncStatus == SyncStatus.PENDING_CREATE }) }
        coVerify {
            syncQueueDao.update(match {
                it.id == 5L &&
                    createAdapter.fromJson(it.payload)?.inspectionDate == "2024-09-01T10:00:00Z"
            })
        }
        coVerify(exactly = 0) { syncQueueDao.insert(any()) }
    }

    @Test
    fun `updateInspection IOException falls back to offline update`() = runTest {
        val existing = inspectionEntity("loc-1", "srv-1", "hive-1", syncStatus = SyncStatus.SYNCED)
        every { connectivity.isCurrentlyOnline() } returns true
        every { backendReachability.isLikelyUnreachable() } returns false
        coEvery { inspectionDao.getByLocalId("loc-1") } returns existing
        coEvery { inspectionApi.updateInspection(any(), any()) } throws IOException("unreachable")
        coEvery { inspectionDao.update(any()) } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val result = repository.updateInspection("loc-1", UpdateInspectionRequest("2024-09-01T10:00:00Z"))

        assertTrue(result is Result.Success)
        coVerify { inspectionDao.update(any()) }
        coVerify { syncQueueDao.insert(match { it.operationType == "UPDATE" }) }
    }

    // ==================== DELETE ====================

    @Test
    fun `deleteInspection offline local-only entity deletes immediately without queuing`() = runTest {
        val localOnly = inspectionEntity("loc-1", null, "hive-1", syncStatus = SyncStatus.PENDING_CREATE)
        every { connectivity.isCurrentlyOnline() } returns false
        every { backendReachability.isLikelyUnreachable() } returns false
        coEvery { inspectionDao.getByLocalId("loc-1") } returns localOnly
        coEvery { inspectionDao.deleteByLocalId("loc-1") } returns Unit
        coEvery { syncQueueDao.deleteByEntityLocalId("loc-1") } returns Unit

        val result = repository.deleteInspection("loc-1")

        assertTrue(result is Result.Success)
        coVerify { inspectionDao.deleteByLocalId("loc-1") }
        coVerify { syncQueueDao.deleteByEntityLocalId("loc-1") }
        coVerify(exactly = 0) { syncQueueDao.insert(any()) }
    }

    @Test
    fun `deleteInspection offline synced entity marks PENDING_DELETE and queues sync`() = runTest {
        val synced = inspectionEntity("loc-1", "srv-1", "hive-1", syncStatus = SyncStatus.SYNCED)
        every { connectivity.isCurrentlyOnline() } returns false
        every { backendReachability.isLikelyUnreachable() } returns false
        coEvery { inspectionDao.getByLocalId("loc-1") } returns synced
        coEvery { inspectionDao.update(any()) } returns Unit
        coEvery { syncQueueDao.deleteByEntityLocalId("loc-1") } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val result = repository.deleteInspection("loc-1")

        assertTrue(result is Result.Success)
        coVerify { inspectionDao.update(match { it.syncStatus == SyncStatus.PENDING_DELETE }) }
        coVerify { syncQueueDao.deleteByEntityLocalId("loc-1") }
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "DELETE" && it.entityType == "INSPECTION" && it.entityServerId == "srv-1"
            })
        }
    }

    @Test
    fun `deleteInspection IOException falls back to offline delete`() = runTest {
        val synced = inspectionEntity("loc-1", "srv-1", "hive-1", syncStatus = SyncStatus.SYNCED)
        every { connectivity.isCurrentlyOnline() } returns true
        every { backendReachability.isLikelyUnreachable() } returns false
        coEvery { inspectionDao.getByLocalId("loc-1") } returns synced
        coEvery { inspectionApi.deleteInspection(any()) } throws IOException("unreachable")
        coEvery { inspectionDao.update(any()) } returns Unit
        coEvery { syncQueueDao.deleteByEntityLocalId("loc-1") } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val result = repository.deleteInspection("loc-1")

        assertTrue(result is Result.Success)
        coVerify { inspectionDao.update(match { it.syncStatus == SyncStatus.PENDING_DELETE }) }
        coVerify { syncQueueDao.insert(match { it.operationType == "DELETE" }) }
    }

    @Test
    fun `deleteInspection online calls API and removes local entity`() = runTest {
        val entity = inspectionEntity("loc-1", "srv-1", "hive-1")
        every { connectivity.isCurrentlyOnline() } returns true
        every { backendReachability.isLikelyUnreachable() } returns false
        coEvery { inspectionDao.getByLocalId("loc-1") } returns entity
        coEvery { inspectionApi.deleteInspection("loc-1") } returns Response.success(Unit)
        coEvery { inspectionDao.deleteByLocalId("loc-1") } returns Unit

        val result = repository.deleteInspection("loc-1")

        assertTrue(result is Result.Success)
        coVerify { inspectionApi.deleteInspection("loc-1") }
        coVerify { inspectionDao.deleteByLocalId("loc-1") }
    }

    // ==================== PHOTO OPERATIONS ====================

    @Test
    fun `addPhoto offline queues CREATE for INSPECTION_PHOTO`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns false
        every { backendReachability.isLikelyUnreachable() } returns false
        val entity = inspectionEntity("loc-1", "srv-1", "hive-1")
        coEvery { inspectionDao.getByLocalId("loc-1") } returns entity
        coEvery { inspectionPhotoDao.insert(any()) } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L
        coEvery { inspectionDao.update(any()) } returns Unit

        val request = AddInspectionPhotoRequest("https://example.com/photo.jpg", "Rame de puiet")
        val result = repository.addPhoto("loc-1", request)

        assertTrue(result is Result.Success)
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "CREATE" && it.entityType == "INSPECTION_PHOTO"
            })
        }
        coVerify { inspectionDao.update(match { it.photosCount == entity.photosCount + 1 }) }
    }

    @Test
    fun `addPhoto online calls API directly`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns true
        every { backendReachability.isLikelyUnreachable() } returns false
        val photoResp = InspectionPhotoResponse("photo-srv-1", "srv-1", "https://example.com/photo.jpg", null, "")
        coEvery { inspectionApi.addPhoto("srv-1", any()) } returns Response.success(photoResp)
        coEvery { inspectionDao.getByLocalId("srv-1") } returns null
        coEvery { inspectionPhotoDao.insert(any()) } returns Unit

        val result = repository.addPhoto("srv-1", AddInspectionPhotoRequest("https://example.com/photo.jpg"))

        assertTrue(result is Result.Success)
        assertEquals("photo-srv-1", (result as Result.Success).data.id)
        coVerify { inspectionApi.addPhoto("srv-1", any()) }
    }

    @Test
    fun `addPhoto IOException falls back to offline queue`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns true
        every { backendReachability.isLikelyUnreachable() } returns false
        val entity = inspectionEntity("loc-1", "srv-1", "hive-1")
        coEvery { inspectionDao.getByLocalId("loc-1") } returns entity
        coEvery { inspectionApi.addPhoto(any(), any()) } throws IOException("unreachable")
        coEvery { inspectionPhotoDao.insert(any()) } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L
        coEvery { inspectionDao.update(any()) } returns Unit

        val result = repository.addPhoto("loc-1", AddInspectionPhotoRequest("https://example.com/photo.jpg"))

        assertTrue(result is Result.Success)
        coVerify { syncQueueDao.insert(match { it.operationType == "CREATE" && it.entityType == "INSPECTION_PHOTO" }) }
    }

    @Test
    fun `updatePhoto offline queues UPDATE for INSPECTION_PHOTO`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns false
        every { backendReachability.isLikelyUnreachable() } returns false
        coEvery { inspectionPhotoDao.getById("photo-loc-1") } returns null
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val result = repository.updatePhoto("photo-loc-1", UpdateInspectionPhotoRequest("New description"))

        assertTrue(result is Result.Success)
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "UPDATE" && it.entityType == "INSPECTION_PHOTO"
            })
        }
    }

    @Test
    fun `updatePhoto offline pending create updates queued CREATE payload without UPDATE`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns false
        every { backendReachability.isLikelyUnreachable() } returns false
        val photo = InspectionPhotoEntity(
            localId = "photo-loc-1",
            serverId = null,
            inspectionLocalId = "insp-loc-1",
            inspectionServerId = null,
            photoUrl = "https://example.com/photo.jpg",
            description = "Old description",
            createdAt = "2024-08-15T10:00:00Z",
            syncStatus = SyncStatus.PENDING_CREATE
        )
        val createAdapter = moshi.adapter(QueuedPhotoCreate::class.java)
        val createOp = SyncQueueEntity(
            id = 7L,
            operationType = "CREATE",
            entityType = "INSPECTION_PHOTO",
            entityLocalId = "photo-loc-1",
            entityServerId = null,
            payload = createAdapter.toJson(
                QueuedPhotoCreate(
                    inspectionLocalId = "insp-loc-1",
                    request = AddInspectionPhotoRequest(
                        photoUrl = "https://example.com/photo.jpg",
                        description = "Old description"
                    )
                )
            )
        )
        coEvery { inspectionPhotoDao.getById("photo-loc-1") } returns photo
        coEvery { inspectionPhotoDao.update(any()) } returns Unit
        coEvery {
            syncQueueDao.getLatestForEntity("CREATE", "INSPECTION_PHOTO", "photo-loc-1")
        } returns createOp
        coEvery { syncQueueDao.update(any()) } returns Unit

        val result = repository.updatePhoto("photo-loc-1", UpdateInspectionPhotoRequest("New description"))

        assertTrue(result is Result.Success)
        coVerify {
            inspectionPhotoDao.update(match {
                it.description == "New description" && it.syncStatus == SyncStatus.PENDING_CREATE
            })
        }
        coVerify {
            syncQueueDao.update(match {
                it.id == 7L &&
                    createAdapter.fromJson(it.payload)?.request?.description == "New description"
            })
        }
        coVerify(exactly = 0) {
            syncQueueDao.insert(match {
                it.operationType == "UPDATE" && it.entityType == "INSPECTION_PHOTO"
            })
        }
    }

    @Test
    fun `deletePhoto offline queues DELETE for INSPECTION_PHOTO`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns false
        every { backendReachability.isLikelyUnreachable() } returns false
        val photo = InspectionPhotoEntity(
            localId = "photo-loc-1",
            serverId = "photo-1",
            inspectionLocalId = "insp-loc-1",
            inspectionServerId = "insp-srv-1",
            photoUrl = "https://example.com/photo.jpg",
            description = null,
            createdAt = "2024-08-15T10:00:00Z",
            syncStatus = SyncStatus.SYNCED
        )
        val inspection = inspectionEntity("insp-loc-1", "insp-srv-1", "hive-1")
        coEvery { inspectionPhotoDao.getById("photo-1") } returns photo
        coEvery { inspectionDao.getByLocalId("insp-loc-1") } returns inspection
        coEvery { inspectionDao.update(any()) } returns Unit
        coEvery { inspectionPhotoDao.update(any()) } returns Unit
        coEvery { syncQueueDao.deleteByEntityLocalId("photo-loc-1") } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val result = repository.deletePhoto("photo-1")

        assertTrue(result is Result.Success)
        coVerify { inspectionPhotoDao.update(match { it.syncStatus == SyncStatus.PENDING_DELETE }) }
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "DELETE" &&
                    it.entityType == "INSPECTION_PHOTO" &&
                    it.entityLocalId == "photo-loc-1" &&
                    it.entityServerId == "photo-1"
            })
        }
    }

    @Test
    fun `deletePhoto online calls API`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns true
        every { backendReachability.isLikelyUnreachable() } returns false
        coEvery { inspectionApi.deletePhoto("photo-srv-1") } returns Response.success(Unit)
        coEvery { inspectionPhotoDao.deleteById("photo-srv-1") } returns Unit

        val result = repository.deletePhoto("photo-srv-1")

        assertTrue(result is Result.Success)
        coVerify { inspectionApi.deletePhoto("photo-srv-1") }
    }

    // ==================== AI ANALYSIS ====================

    @Test
    fun `saveAiAnalysis offline queues CREATE for INSPECTION_AI_ANALYSIS`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns false
        every { backendReachability.isLikelyUnreachable() } returns false
        val entity = inspectionEntity("loc-1", null, "hive-1", syncStatus = SyncStatus.PENDING_CREATE)
        val adapter = moshi.adapter(QueuedAiAnalysisCreate::class.java)
        coEvery { inspectionDao.getByLocalId("loc-1") } returns entity
        coEvery {
            syncQueueDao.getLatestForEntity("CREATE", "INSPECTION_AI_ANALYSIS", "ai-analysis-loc-1")
        } returns null
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val request = SaveInspectionAiAnalysisRequest(
            results = mapOf("Capped" to 5, "Larves" to 3, "Nectar" to 2),
            status = "success",
            message = "ok",
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
        val result = repository.saveAiAnalysis("loc-1", request)

        assertTrue(result is Result.Success)
        coVerify {
            syncQueueDao.insert(match {
                val payload = adapter.fromJson(it.payload)
                it.operationType == "CREATE" &&
                    it.entityType == "INSPECTION_AI_ANALYSIS" &&
                    it.entityLocalId == "ai-analysis-loc-1" &&
                    payload != null &&
                    payload.inspectionLocalId == "loc-1" &&
                    payload.request.results["Nectar"] == 2 &&
                    payload.request.cellDetections.single().className == "Eggs"
            })
        }
        coVerify(exactly = 0) { inspectionApi.saveAiAnalysis(any(), any()) }
    }

    @Test
    fun `saveAiAnalysis server error refreshes queued INSPECTION_AI_ANALYSIS payload`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns true
        every { backendReachability.isLikelyUnreachable() } returns false
        val entity = inspectionEntity("loc-1", "srv-1", "hive-1")
        val existing = SyncQueueEntity(
            id = 9L,
            operationType = "CREATE",
            entityType = "INSPECTION_AI_ANALYSIS",
            entityLocalId = "ai-analysis-loc-1",
            entityServerId = "srv-1",
            payload = "{}",
            retryCount = 2
        )
        val adapter = moshi.adapter(QueuedAiAnalysisCreate::class.java)
        coEvery { inspectionApi.saveAiAnalysis("srv-1", any()) } returns
            Response.error(503, "server error".toResponseBody("application/json".toMediaType()))
        coEvery { inspectionDao.getByLocalId("srv-1") } returns entity
        coEvery {
            syncQueueDao.getLatestForEntity("CREATE", "INSPECTION_AI_ANALYSIS", "ai-analysis-loc-1")
        } returns existing
        coEvery { syncQueueDao.update(any()) } returns Unit

        val request = SaveInspectionAiAnalysisRequest(
            results = mapOf("Eggs" to 4),
            status = "success"
        )
        val result = repository.saveAiAnalysis("srv-1", request)

        assertTrue(result is Result.Success)
        coVerify {
            syncQueueDao.update(match {
                val payload = adapter.fromJson(it.payload)
                it.id == 9L &&
                    it.retryCount == 0 &&
                    payload != null &&
                    payload.inspectionLocalId == "loc-1" &&
                    payload.request.results["Eggs"] == 4
            })
        }
        coVerify(exactly = 0) { syncQueueDao.insert(any()) }
    }

    // ==================== helpers ====================

    private fun inspectionEntity(
        localId: String,
        serverId: String?,
        hiveLocalId: String,
        syncStatus: SyncStatus = SyncStatus.SYNCED
    ) = InspectionEntity(
        localId = localId,
        serverId = serverId,
        hiveLocalId = hiveLocalId,
        hiveServerId = serverId?.let { "hive-srv" },
        hiveName = "Stupul Test",
        apiaryId = "apiary-1",
        apiaryName = "Livada",
        inspectionDate = "2024-08-15T10:00:00Z",
        temperature = 25.0,
        framesCount = 10,
        broodFrames = 4,
        honeyFrames = 3,
        pollenFrames = 2,
        queenSeen = true,
        eggsSeen = true,
        larvaeSeen = true,
        photosCount = 0,
        createdAt = "2024-08-15T10:00:00Z",
        syncStatus = syncStatus
    )

    private fun hiveEntity(localId: String, serverId: String?, apiaryLocalId: String) =
        HiveEntity(
            localId = localId,
            serverId = serverId,
            apiaryLocalId = apiaryLocalId,
            apiaryServerId = serverId?.let { "apiary-srv" },
            apiaryName = "Livada",
            name = "Stupul Test",
            type = "Langstroth",
            status = "Active",
            notes = null,
            syncStatus = SyncStatus.SYNCED
        )

    private fun inspectionResponse(id: String, hiveId: String) =
        InspectionResponse(
            id = id,
            hiveId = hiveId,
            hiveName = "Stupul Test",
            apiaryId = "apiary-1",
            apiaryName = "Livada",
            inspectionDate = "2024-08-15T10:00:00Z",
            queenSeen = true,
            eggsSeen = true,
            larvaeSeen = true,
            photosCount = 0,
            createdAt = "2024-08-15T10:00:00Z",
            updatedAt = "2024-08-15T10:00:00Z"
        )
}

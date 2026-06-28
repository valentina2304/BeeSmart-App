package com.example.beesmart.repository

import com.example.beesmart.data.local.dao.SyncQueueDao
import com.example.beesmart.data.local.dao.TreatmentDao
import com.example.beesmart.data.local.entity.SyncStatus
import com.example.beesmart.data.local.entity.TreatmentEntity
import com.example.beesmart.data.repository.Result
import com.example.beesmart.data.repository.TreatmentRepository
import com.example.beesmart.network.BackendReachability
import com.example.beesmart.network.TreatmentApi
import com.example.beesmart.network.models.*
import com.example.beesmart.sync.ConnectivityObserver
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

class TreatmentRepositoryTest {

    @MockK lateinit var treatmentApi: TreatmentApi
    @MockK lateinit var treatmentDao: TreatmentDao
    @MockK lateinit var syncQueueDao: SyncQueueDao
    @MockK lateinit var connectivity: ConnectivityObserver
    @MockK(relaxed = true) lateinit var backendReachability: BackendReachability

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private lateinit var repository: TreatmentRepository

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        every { backendReachability.isLikelyUnreachable() } returns false
        repository = TreatmentRepository(treatmentApi, treatmentDao, syncQueueDao, connectivity, backendReachability, moshi)
    }

    // ==================== CREATE ====================

    @Test
    fun `createTreatment offline inserts with PENDING_CREATE and queues sync`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { treatmentDao.insert(any()) } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val request = CreateTreatmentRequest(
            hiveId = "hive-local-1",
            treatmentDate = "2024-05-01",
            type = TreatmentType.Varroa,
            productName = "Apivar",
            substance = "Amitraz",
            dosage = "2 benzi",
            notes = null,
            nextTreatmentDate = null
        )
        val result = repository.createTreatment(request)

        assertTrue(result is Result.Success)
        coVerify {
            treatmentDao.insert(match {
                it.productName == "Apivar" &&
                it.serverId == null &&
                it.hiveLocalId == "hive-local-1" &&
                it.syncStatus == SyncStatus.PENDING_CREATE
            })
        }
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "CREATE" && it.entityType == "TREATMENT"
            })
        }
        coVerify(exactly = 0) { treatmentApi.createTreatment(any()) }
    }

    @Test
    fun `createTreatment online calls API and saves synced entity`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns true
        val serverResp = treatmentResponse("srv-1", "hive-srv-1")
        coEvery { treatmentApi.createTreatment(any()) } returns Response.success(serverResp)
        coEvery { treatmentDao.insert(any()) } returns Unit

        val request = CreateTreatmentRequest("hive-srv-1", "2024-05-01", TreatmentType.Varroa, "Apivar", null, null, null, null)
        val result = repository.createTreatment(request)

        assertTrue(result is Result.Success)
        assertEquals("srv-1", (result as Result.Success).data.id)
        coVerify { treatmentApi.createTreatment(any()) }
        coVerify { treatmentDao.insert(match { it.serverId == "srv-1" && it.syncStatus == SyncStatus.SYNCED }) }
    }

    @Test
    fun `createTreatment online network exception returns error`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { treatmentApi.createTreatment(any()) } throws RuntimeException("timeout")

        val result = repository.createTreatment(
            CreateTreatmentRequest("hive-1", "2024-05-01", TreatmentType.Varroa, "Apivar", null, null, null, null)
        )

        assertTrue(result is Result.Error)
    }

    // ==================== READ ====================

    @Test
    fun `getTreatmentsByHiveId offline returns cached treatments`() = runTest {
        val cached = listOf(treatmentEntity("loc-1", null, "hive-loc-1"))
        coEvery { treatmentDao.getByHiveId("hive-loc-1") } returns cached
        every { connectivity.isCurrentlyOnline() } returns false

        val result = repository.getTreatmentsByHiveId("hive-loc-1")

        assertTrue(result is Result.Success)
        assertEquals(1, (result as Result.Success).data.size)
        coVerify(exactly = 0) { treatmentApi.getTreatmentsByHiveId(any()) }
    }

    @Test
    fun `getTreatmentsByHiveId offline returns empty list when no cache`() = runTest {
        coEvery { treatmentDao.getByHiveId("hive-1") } returns emptyList()
        every { connectivity.isCurrentlyOnline() } returns false

        val result = repository.getTreatmentsByHiveId("hive-1")

        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data.isEmpty())
    }

    @Test
    fun `getTreatmentById offline returns cached treatment`() = runTest {
        coEvery { treatmentDao.getByLocalId("loc-1") } returns treatmentEntity("loc-1", null, "hive-1")
        every { connectivity.isCurrentlyOnline() } returns false

        val result = repository.getTreatmentById("loc-1")

        assertTrue(result is Result.Success)
        coVerify(exactly = 0) { treatmentApi.getTreatmentById(any()) }
    }

    @Test
    fun `getTreatmentById offline not found returns error`() = runTest {
        coEvery { treatmentDao.getByLocalId("missing") } returns null
        every { connectivity.isCurrentlyOnline() } returns false

        val result = repository.getTreatmentById("missing")

        assertTrue(result is Result.Error)
    }

    // ==================== UPDATE ====================

    @Test
    fun `updateTreatment offline updates local entity with PENDING_UPDATE and queues sync`() = runTest {
        val existing = treatmentEntity("loc-1", "srv-1", "hive-local-1")
        coEvery { treatmentDao.getByLocalId("loc-1") } returns existing
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { treatmentDao.update(any()) } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val request = UpdateTreatmentRequest("2024-06-01", TreatmentType.Nosema, "OxyBee", null, null, "Updated notes", null)
        val result = repository.updateTreatment("loc-1", request)

        assertTrue(result is Result.Success)
        coVerify {
            treatmentDao.update(match {
                it.productName == "OxyBee" && it.syncStatus == SyncStatus.PENDING_UPDATE
            })
        }
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "UPDATE" && it.entityType == "TREATMENT" && it.entityServerId == "srv-1"
            })
        }
    }

    @Test
    fun `updateTreatment online calls API`() = runTest {
        coEvery { treatmentDao.getByLocalId("loc-1") } returns treatmentEntity("loc-1", "srv-1", "hive-1")
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { treatmentApi.updateTreatment("loc-1", any()) } returns
            Response.success(treatmentResponse("srv-1", "hive-1"))
        coEvery { treatmentDao.insert(any()) } returns Unit

        val result = repository.updateTreatment("loc-1", UpdateTreatmentRequest("2024-06-01", TreatmentType.Varroa, "Apivar", null, null, null, null))

        assertTrue(result is Result.Success)
        coVerify { treatmentApi.updateTreatment("loc-1", any()) }
    }

    // ==================== DELETE ====================

    @Test
    fun `deleteTreatment offline local-only entity deletes immediately without queuing`() = runTest {
        val localOnly = treatmentEntity("loc-1", null, "hive-1", syncStatus = SyncStatus.PENDING_CREATE)
        coEvery { treatmentDao.getByLocalId("loc-1") } returns localOnly
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { treatmentDao.deleteByLocalId("loc-1") } returns Unit
        coEvery { syncQueueDao.deleteByEntityLocalId("loc-1") } returns Unit

        val result = repository.deleteTreatment("loc-1")

        assertTrue(result is Result.Success)
        coVerify { treatmentDao.deleteByLocalId("loc-1") }
        coVerify { syncQueueDao.deleteByEntityLocalId("loc-1") }
        coVerify(exactly = 0) { syncQueueDao.insert(any()) }
    }

    @Test
    fun `deleteTreatment offline synced entity marks PENDING_DELETE and queues sync`() = runTest {
        val synced = treatmentEntity("loc-1", "srv-1", "hive-1", syncStatus = SyncStatus.SYNCED)
        coEvery { treatmentDao.getByLocalId("loc-1") } returns synced
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { treatmentDao.update(any()) } returns Unit
        coEvery { syncQueueDao.deleteByEntityLocalId("loc-1") } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val result = repository.deleteTreatment("loc-1")

        assertTrue(result is Result.Success)
        coVerify { treatmentDao.update(match { it.syncStatus == SyncStatus.PENDING_DELETE }) }
        coVerify { syncQueueDao.deleteByEntityLocalId("loc-1") }
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "DELETE" && it.entityType == "TREATMENT" && it.entityServerId == "srv-1"
            })
        }
        coVerify(exactly = 0) { treatmentDao.deleteByLocalId(any()) }
    }

    @Test
    fun `deleteTreatment online calls API and removes local entity`() = runTest {
        val entity = treatmentEntity("loc-1", "srv-1", "hive-1")
        coEvery { treatmentDao.getByLocalId("loc-1") } returns entity
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { treatmentApi.deleteTreatment("loc-1") } returns Response.success(Unit)
        coEvery { treatmentDao.deleteByLocalId("loc-1") } returns Unit

        val result = repository.deleteTreatment("loc-1")

        assertTrue(result is Result.Success)
        coVerify { treatmentApi.deleteTreatment("loc-1") }
        coVerify { treatmentDao.deleteByLocalId("loc-1") }
    }

    @Test
    fun `deleteTreatment online API error returns Result Error`() = runTest {
        coEvery { treatmentDao.getByLocalId("loc-1") } returns treatmentEntity("loc-1", "srv-1", "hive-1")
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { treatmentApi.deleteTreatment("loc-1") } returns
            Response.error(500, "error".toResponseBody("application/json".toMediaType()))

        val result = repository.deleteTreatment("loc-1")

        assertTrue(result is Result.Error)
    }

    // ==================== helpers ====================

    private fun treatmentEntity(
        localId: String,
        serverId: String?,
        hiveLocalId: String,
        syncStatus: SyncStatus = SyncStatus.SYNCED
    ) = TreatmentEntity(
        localId = localId,
        serverId = serverId,
        hiveLocalId = hiveLocalId,
        hiveServerId = serverId?.let { "hive-srv" },
        apiaryId = "apiary-1",
        treatmentDate = "2024-05-01",
        type = TreatmentType.Varroa.name,
        productName = "Apivar",
        substance = "Amitraz",
        dosage = null,
        notes = null,
        nextTreatmentDate = null,
        syncStatus = syncStatus
    )

    private fun treatmentResponse(id: String, hiveId: String) =
        HiveTreatment(
            id = id,
            hiveId = hiveId,
            apiaryId = "apiary-1",
            treatmentDate = "2024-05-01",
            type = TreatmentType.Varroa,
            productName = "Apivar",
            substance = "Amitraz",
            dosage = null,
            notes = null,
            nextTreatmentDate = null,
            createdAt = "",
            updatedAt = ""
        )
}

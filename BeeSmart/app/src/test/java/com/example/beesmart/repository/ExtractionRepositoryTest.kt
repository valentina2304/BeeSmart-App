package com.example.beesmart.repository

import com.example.beesmart.data.local.dao.ExtractionDao
import com.example.beesmart.data.local.dao.SyncQueueDao
import com.example.beesmart.data.local.entity.ExtractionEntity
import com.example.beesmart.data.local.entity.SyncStatus
import com.example.beesmart.data.repository.ExtractionRepository
import com.example.beesmart.data.repository.Result
import com.example.beesmart.network.BackendReachability
import com.example.beesmart.network.ExtractionApi
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

class ExtractionRepositoryTest {

    @MockK lateinit var extractionApi: ExtractionApi
    @MockK lateinit var extractionDao: ExtractionDao
    @MockK lateinit var syncQueueDao: SyncQueueDao
    @MockK lateinit var connectivity: ConnectivityObserver
    @MockK(relaxed = true) lateinit var backendReachability: BackendReachability

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private lateinit var repository: ExtractionRepository

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        every { backendReachability.isLikelyUnreachable() } returns false
        repository = ExtractionRepository(extractionApi, extractionDao, syncQueueDao, connectivity, backendReachability, moshi)
    }

    // ==================== CREATE ====================

    @Test
    fun `createExtraction offline inserts with PENDING_CREATE and queues sync`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { extractionDao.insert(any()) } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val request = CreateExtractionRequest(
            hiveId = "hive-local-1",
            extractionDate = "2024-07-01",
            type = ExtractionType.Honey,
            quantity = 5.5,
            unit = "kg",
            notes = "Prima recoltă"
        )
        val result = repository.createExtraction(request)

        assertTrue(result is Result.Success)
        coVerify {
            extractionDao.insert(match {
                it.hiveLocalId == "hive-local-1" &&
                it.serverId == null &&
                it.quantity == 5.5 &&
                it.syncStatus == SyncStatus.PENDING_CREATE
            })
        }
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "CREATE" && it.entityType == "EXTRACTION"
            })
        }
        coVerify(exactly = 0) { extractionApi.createExtraction(any()) }
    }

    @Test
    fun `createExtraction online calls API and saves synced entity`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns true
        val serverResp = extractionResponse("srv-1", "hive-srv-1")
        coEvery { extractionApi.createExtraction(any()) } returns Response.success(serverResp)
        coEvery { extractionDao.insert(any()) } returns Unit

        val result = repository.createExtraction(
            CreateExtractionRequest("hive-srv-1", "2024-07-01", ExtractionType.Honey, 5.5, "kg", null)
        )

        assertTrue(result is Result.Success)
        assertEquals("srv-1", (result as Result.Success).data.id)
        coVerify { extractionApi.createExtraction(any()) }
        coVerify { extractionDao.insert(match { it.serverId == "srv-1" && it.syncStatus == SyncStatus.SYNCED }) }
    }

    @Test
    fun `createExtraction online network exception returns error`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { extractionApi.createExtraction(any()) } throws RuntimeException("timeout")

        val result = repository.createExtraction(
            CreateExtractionRequest("hive-1", "2024-07-01", ExtractionType.Honey, 1.0, "kg", null)
        )

        assertTrue(result is Result.Error)
    }

    // ==================== READ ====================

    @Test
    fun `getExtractionsByHiveId offline returns cached extractions`() = runTest {
        val cached = listOf(extractionEntity("loc-1", null, "hive-loc-1"))
        coEvery { extractionDao.getByHiveId("hive-loc-1") } returns cached
        every { connectivity.isCurrentlyOnline() } returns false

        val result = repository.getExtractionsByHiveId("hive-loc-1")

        assertTrue(result is Result.Success)
        assertEquals(1, (result as Result.Success).data.size)
        coVerify(exactly = 0) { extractionApi.getExtractionsByHiveId(any()) }
    }

    @Test
    fun `getExtractionsByHiveId offline returns empty list when no cache`() = runTest {
        coEvery { extractionDao.getByHiveId("hive-1") } returns emptyList()
        every { connectivity.isCurrentlyOnline() } returns false

        val result = repository.getExtractionsByHiveId("hive-1")

        assertTrue(result is Result.Success)
        assertTrue((result as Result.Success).data.isEmpty())
    }

    @Test
    fun `getExtractionsByHiveId online replaces cache for hive`() = runTest {
        coEvery { extractionDao.getByHiveId("hive-srv-1") } returns emptyList()
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { extractionApi.getExtractionsByHiveId("hive-srv-1") } returns
            Response.success(listOf(extractionResponse("srv-1", "hive-srv-1")))
        coEvery { extractionDao.deleteSyncedByHiveId("hive-srv-1") } returns Unit
        coEvery { extractionDao.insertAll(any()) } returns Unit

        val result = repository.getExtractionsByHiveId("hive-srv-1")

        assertTrue(result is Result.Success)
        coVerify { extractionDao.deleteSyncedByHiveId("hive-srv-1") }
        coVerify { extractionDao.insertAll(any()) }
    }

    @Test
    fun `getExtractionById offline returns cached extraction`() = runTest {
        coEvery { extractionDao.getByLocalId("loc-1") } returns extractionEntity("loc-1", null, "hive-1")
        every { connectivity.isCurrentlyOnline() } returns false

        val result = repository.getExtractionById("loc-1")

        assertTrue(result is Result.Success)
        coVerify(exactly = 0) { extractionApi.getExtractionById(any()) }
    }

    @Test
    fun `getExtractionById offline not found returns error`() = runTest {
        coEvery { extractionDao.getByLocalId("missing") } returns null
        every { connectivity.isCurrentlyOnline() } returns false

        val result = repository.getExtractionById("missing")

        assertTrue(result is Result.Error)
    }

    // ==================== UPDATE ====================

    @Test
    fun `updateExtraction offline updates local entity with PENDING_UPDATE and queues sync`() = runTest {
        val existing = extractionEntity("loc-1", "srv-1", "hive-local-1")
        coEvery { extractionDao.getByLocalId("loc-1") } returns existing
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { extractionDao.update(any()) } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val request = UpdateExtractionRequest("2024-08-01", ExtractionType.Pollen, 2.0, "kg", "Updated")
        val result = repository.updateExtraction("loc-1", request)

        assertTrue(result is Result.Success)
        coVerify {
            extractionDao.update(match {
                it.quantity == 2.0 && it.syncStatus == SyncStatus.PENDING_UPDATE
            })
        }
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "UPDATE" && it.entityType == "EXTRACTION" && it.entityServerId == "srv-1"
            })
        }
    }

    @Test
    fun `updateExtraction offline entity not found returns error`() = runTest {
        coEvery { extractionDao.getByLocalId("missing") } returns null
        coEvery { extractionDao.getByServerId("missing") } returns null
        every { connectivity.isCurrentlyOnline() } returns false

        val result = repository.updateExtraction("missing", UpdateExtractionRequest("2024-08-01", ExtractionType.Honey, 1.0, "kg", null))

        assertTrue(result is Result.Error)
    }

    @Test
    fun `updateExtraction online calls API`() = runTest {
        coEvery { extractionDao.getByLocalId("loc-1") } returns extractionEntity("loc-1", "srv-1", "hive-1")
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { extractionApi.updateExtraction("loc-1", any()) } returns
            Response.success(extractionResponse("srv-1", "hive-1"))
        coEvery { extractionDao.insert(any()) } returns Unit

        val result = repository.updateExtraction("loc-1", UpdateExtractionRequest("2024-08-01", ExtractionType.Honey, 5.0, "kg", null))

        assertTrue(result is Result.Success)
        coVerify { extractionApi.updateExtraction("loc-1", any()) }
    }

    // ==================== DELETE ====================

    @Test
    fun `deleteExtraction offline local-only entity deletes immediately without queuing`() = runTest {
        val localOnly = extractionEntity("loc-1", null, "hive-1", syncStatus = SyncStatus.PENDING_CREATE)
        coEvery { extractionDao.getByLocalId("loc-1") } returns localOnly
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { extractionDao.deleteByLocalId("loc-1") } returns Unit
        coEvery { syncQueueDao.deleteByEntityLocalId("loc-1") } returns Unit

        val result = repository.deleteExtraction("loc-1")

        assertTrue(result is Result.Success)
        coVerify { extractionDao.deleteByLocalId("loc-1") }
        coVerify { syncQueueDao.deleteByEntityLocalId("loc-1") }
        coVerify(exactly = 0) { syncQueueDao.insert(any()) }
    }

    @Test
    fun `deleteExtraction offline synced entity marks PENDING_DELETE and queues sync`() = runTest {
        val synced = extractionEntity("loc-1", "srv-1", "hive-1", syncStatus = SyncStatus.SYNCED)
        coEvery { extractionDao.getByLocalId("loc-1") } returns synced
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { extractionDao.update(any()) } returns Unit
        coEvery { syncQueueDao.deleteByEntityLocalId("loc-1") } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val result = repository.deleteExtraction("loc-1")

        assertTrue(result is Result.Success)
        coVerify { extractionDao.update(match { it.syncStatus == SyncStatus.PENDING_DELETE }) }
        coVerify { syncQueueDao.deleteByEntityLocalId("loc-1") }
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "DELETE" && it.entityType == "EXTRACTION" && it.entityServerId == "srv-1"
            })
        }
        coVerify(exactly = 0) { extractionDao.deleteByLocalId(any()) }
    }

    @Test
    fun `deleteExtraction online calls API and removes local entity`() = runTest {
        val entity = extractionEntity("loc-1", "srv-1", "hive-1")
        coEvery { extractionDao.getByLocalId("loc-1") } returns entity
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { extractionApi.deleteExtraction("loc-1") } returns Response.success(Unit)
        coEvery { extractionDao.deleteByLocalId("loc-1") } returns Unit

        val result = repository.deleteExtraction("loc-1")

        assertTrue(result is Result.Success)
        coVerify { extractionApi.deleteExtraction("loc-1") }
        coVerify { extractionDao.deleteByLocalId("loc-1") }
    }

    @Test
    fun `deleteExtraction online API error returns Result Error`() = runTest {
        coEvery { extractionDao.getByLocalId("loc-1") } returns extractionEntity("loc-1", "srv-1", "hive-1")
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { extractionApi.deleteExtraction("loc-1") } returns
            Response.error(404, "not found".toResponseBody("application/json".toMediaType()))

        val result = repository.deleteExtraction("loc-1")

        assertTrue(result is Result.Error)
    }

    // ==================== helpers ====================

    private fun extractionEntity(
        localId: String,
        serverId: String?,
        hiveLocalId: String,
        syncStatus: SyncStatus = SyncStatus.SYNCED
    ) = ExtractionEntity(
        localId = localId,
        serverId = serverId,
        hiveLocalId = hiveLocalId,
        hiveServerId = serverId?.let { "hive-srv" },
        apiaryId = "apiary-1",
        extractionDate = "2024-07-01",
        type = ExtractionType.Honey.name,
        quantity = 5.5,
        unit = "kg",
        notes = null,
        syncStatus = syncStatus
    )

    private fun extractionResponse(id: String, hiveId: String) =
        HiveExtraction(
            id = id,
            hiveId = hiveId,
            apiaryId = "apiary-1",
            extractionDate = "2024-07-01",
            type = ExtractionType.Honey,
            quantity = 5.5,
            unit = "kg",
            notes = null,
            createdAt = "",
            updatedAt = ""
        )
}

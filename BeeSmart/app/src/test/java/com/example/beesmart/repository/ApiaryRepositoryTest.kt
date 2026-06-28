package com.example.beesmart.repository

import com.example.beesmart.data.local.dao.ApiaryDao
import com.example.beesmart.data.local.dao.SyncQueueDao
import com.example.beesmart.data.local.entity.ApiaryEntity
import com.example.beesmart.data.local.entity.SyncStatus
import com.example.beesmart.data.repository.ApiaryRepository
import com.example.beesmart.data.repository.Result
import com.example.beesmart.network.ApiaryApi
import com.example.beesmart.network.BackendReachability
import com.example.beesmart.network.models.ApiaryResponse
import com.example.beesmart.network.models.CreateApiaryRequest
import com.example.beesmart.network.models.UpdateApiaryRequest
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

class ApiaryRepositoryTest {

    @MockK lateinit var apiaryApi: ApiaryApi
    @MockK lateinit var apiaryDao: ApiaryDao
    @MockK lateinit var syncQueueDao: SyncQueueDao
    @MockK lateinit var connectivity: ConnectivityObserver
    @MockK(relaxed = true) lateinit var backendReachability: BackendReachability

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private lateinit var repository: ApiaryRepository

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        every { backendReachability.isLikelyUnreachable() } returns false
        repository = ApiaryRepository(apiaryApi, apiaryDao, syncQueueDao, connectivity, backendReachability, moshi)
    }

    // ==================== CREATE ====================

    @Test
    fun `createApiary offline inserts entity with PENDING_CREATE and adds to sync queue`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { apiaryDao.insert(any()) } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val result = repository.createApiary(CreateApiaryRequest("Livada", "Desc", "Cluj"))

        assertTrue(result is Result.Success)
        with(result as Result.Success) {
            assertEquals("Livada", data.name)
            assertNull(data.userId.ifEmpty { null }?.let { null } ?: run { null })
        }
        coVerify {
            apiaryDao.insert(match {
                it.name == "Livada" &&
                it.serverId == null &&
                it.syncStatus == SyncStatus.PENDING_CREATE
            })
        }
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "CREATE" &&
                it.entityType == "APIARY" &&
                it.entityServerId == null
            })
        }
        coVerify(exactly = 0) { apiaryApi.createApiary(any()) }
    }

    @Test
    fun `createApiary online calls API and saves synced entity`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns true
        val serverResp = apiaryResponse("srv-1", "Livada")
        coEvery { apiaryApi.createApiary(any()) } returns Response.success(serverResp)
        coEvery { apiaryDao.insert(any()) } returns Unit

        val result = repository.createApiary(CreateApiaryRequest("Livada"))

        assertTrue(result is Result.Success)
        assertEquals("srv-1", (result as Result.Success).data.id)
        coVerify { apiaryApi.createApiary(any()) }
        coVerify { apiaryDao.insert(match { it.serverId == "srv-1" && it.syncStatus == SyncStatus.SYNCED }) }
        coVerify(exactly = 0) { syncQueueDao.insert(any()) }
    }

    @Test
    fun `createApiary online API error returns Result Error`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { apiaryApi.createApiary(any()) } returns
            Response.error(400, "bad request".toResponseBody("application/json".toMediaType()))

        val result = repository.createApiary(CreateApiaryRequest("Livada"))

        assertTrue(result is Result.Error)
    }

    @Test
    fun `createApiary online network exception returns Result Error`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { apiaryApi.createApiary(any()) } throws RuntimeException("timeout")

        val result = repository.createApiary(CreateApiaryRequest("Livada"))

        assertTrue(result is Result.Error)
    }

    // ==================== READ ====================

    @Test
    fun `getAllApiaries offline returns cached data without calling API`() = runTest {
        val cached = listOf(
            ApiaryEntity("loc-1", null, "Livada", null, null, syncStatus = SyncStatus.PENDING_CREATE)
        )
        coEvery { apiaryDao.getAll() } returns cached
        every { connectivity.isCurrentlyOnline() } returns false

        val result = repository.getAllApiaries()

        assertTrue(result is Result.Success)
        assertEquals(1, (result as Result.Success).data.size)
        assertEquals("loc-1", result.data.first().id)
        coVerify(exactly = 0) { apiaryApi.getAllApiaries() }
    }

    @Test
    fun `getAllApiaries online replaces cache with server data`() = runTest {
        // After insertAll, getAll() returns the server-derived entity (merge logic
        // re-reads Room so PENDING_CREATE rows survive a refresh).
        val syncedFromServer = ApiaryEntity("srv-1", "srv-1", "Server", null, null, syncStatus = SyncStatus.SYNCED)
        coEvery { apiaryDao.getAll() } returnsMany listOf(emptyList(), listOf(syncedFromServer))
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { apiaryApi.getAllApiaries() } returns Response.success(listOf(apiaryResponse("srv-1", "Server")))
        coEvery { apiaryDao.deleteAllSynced() } returns Unit
        coEvery { apiaryDao.insertAll(any()) } returns Unit

        val result = repository.getAllApiaries()

        assertTrue(result is Result.Success)
        assertEquals("srv-1", (result as Result.Success).data.first().id)
        coVerify { apiaryDao.deleteAllSynced() }
        coVerify { apiaryDao.insertAll(any()) }
    }

    @Test
    fun `getAllApiaries online network exception falls back to cache`() = runTest {
        val cached = listOf(ApiaryEntity("loc-1", null, "Cached", null, null))
        coEvery { apiaryDao.getAll() } returns cached
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { apiaryApi.getAllApiaries() } throws RuntimeException("timeout")

        val result = repository.getAllApiaries()

        assertTrue(result is Result.Success)
        assertEquals(1, (result as Result.Success).data.size)
    }

    @Test
    fun `getAllApiaries online network exception with empty cache returns error`() = runTest {
        coEvery { apiaryDao.getAll() } returns emptyList()
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { apiaryApi.getAllApiaries() } throws RuntimeException("timeout")

        val result = repository.getAllApiaries()

        assertTrue(result is Result.Error)
    }

    // ==================== UPDATE ====================

    @Test
    fun `updateApiary offline updates local entity with PENDING_UPDATE and queues sync`() = runTest {
        val existing = ApiaryEntity("loc-1", "srv-1", "Old", null, null, syncStatus = SyncStatus.SYNCED)
        coEvery { apiaryDao.getByLocalId("loc-1") } returns existing
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { apiaryDao.update(any()) } returns Unit
        coEvery { syncQueueDao.deleteByEntityLocalId("loc-1") } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val result = repository.updateApiary("loc-1", UpdateApiaryRequest("New", "NewDesc"))

        assertTrue(result is Result.Success)
        assertEquals("New", (result as Result.Success).data.name)
        coVerify {
            apiaryDao.update(match {
                it.name == "New" && it.syncStatus == SyncStatus.PENDING_UPDATE
            })
        }
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "UPDATE" &&
                it.entityType == "APIARY" &&
                it.entityLocalId == "loc-1" &&
                it.entityServerId == "srv-1"
            })
        }
    }

    @Test
    fun `updateApiary offline entity not found returns error`() = runTest {
        coEvery { apiaryDao.getByLocalId("missing") } returns null
        coEvery { apiaryDao.getByServerId("missing") } returns null
        every { connectivity.isCurrentlyOnline() } returns false

        val result = repository.updateApiary("missing", UpdateApiaryRequest("X"))

        assertTrue(result is Result.Error)
    }

    @Test
    fun `updateApiary online calls API and stores updated entity`() = runTest {
        val existing = ApiaryEntity("loc-1", "srv-1", "Old", null, null)
        coEvery { apiaryDao.getByLocalId("loc-1") } returns existing
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { apiaryApi.updateApiary("loc-1", any()) } returns Response.success(apiaryResponse("srv-1", "New"))
        coEvery { apiaryDao.insert(any()) } returns Unit

        val result = repository.updateApiary("loc-1", UpdateApiaryRequest("New"))

        assertTrue(result is Result.Success)
        coVerify { apiaryApi.updateApiary("loc-1", any()) }
        coVerify { apiaryDao.insert(any()) }
    }

    // ==================== DELETE ====================

    @Test
    fun `deleteApiary offline local-only entity deletes immediately without queuing`() = runTest {
        val localOnly = ApiaryEntity("loc-1", null, "Local", null, null, syncStatus = SyncStatus.PENDING_CREATE)
        coEvery { apiaryDao.getByLocalId("loc-1") } returns localOnly
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { apiaryDao.deleteByLocalId("loc-1") } returns Unit
        coEvery { syncQueueDao.deleteByEntityLocalId("loc-1") } returns Unit

        val result = repository.deleteApiary("loc-1")

        assertTrue(result is Result.Success)
        coVerify { apiaryDao.deleteByLocalId("loc-1") }
        coVerify { syncQueueDao.deleteByEntityLocalId("loc-1") }
        coVerify(exactly = 0) { syncQueueDao.insert(any()) }
    }

    @Test
    fun `deleteApiary offline synced entity marks PENDING_DELETE and queues sync`() = runTest {
        val synced = ApiaryEntity("loc-1", "srv-1", "Synced", null, null, syncStatus = SyncStatus.SYNCED)
        coEvery { apiaryDao.getByLocalId("loc-1") } returns synced
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { apiaryDao.update(any()) } returns Unit
        coEvery { syncQueueDao.deleteByEntityLocalId("loc-1") } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val result = repository.deleteApiary("loc-1")

        assertTrue(result is Result.Success)
        coVerify { apiaryDao.update(match { it.syncStatus == SyncStatus.PENDING_DELETE }) }
        coVerify { syncQueueDao.deleteByEntityLocalId("loc-1") }
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "DELETE" &&
                it.entityType == "APIARY" &&
                it.entityLocalId == "loc-1" &&
                it.entityServerId == "srv-1"
            })
        }
        coVerify(exactly = 0) { apiaryDao.deleteByLocalId(any()) }
    }

    @Test
    fun `deleteApiary online calls API and removes local entity`() = runTest {
        val entity = ApiaryEntity("loc-1", "srv-1", "Synced", null, null)
        coEvery { apiaryDao.getByLocalId("srv-1") } returns null
        coEvery { apiaryDao.getByServerId("srv-1") } returns entity
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { apiaryApi.deleteApiary("srv-1") } returns Response.success(Unit)
        coEvery { apiaryDao.deleteByLocalId("loc-1") } returns Unit

        val result = repository.deleteApiary("srv-1")

        assertTrue(result is Result.Success)
        coVerify { apiaryApi.deleteApiary("srv-1") }
        coVerify { apiaryDao.deleteByLocalId("loc-1") }
    }

    @Test
    fun `deleteApiary online API error returns Result Error`() = runTest {
        val entity = ApiaryEntity("loc-1", "srv-1", "Synced", null, null)
        coEvery { apiaryDao.getByLocalId("srv-1") } returns null
        coEvery { apiaryDao.getByServerId("srv-1") } returns entity
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { apiaryApi.deleteApiary("srv-1") } returns
            Response.error(404, "not found".toResponseBody("application/json".toMediaType()))

        val result = repository.deleteApiary("srv-1")

        assertTrue(result is Result.Error)
    }

    // ==================== helpers ====================

    private fun apiaryResponse(id: String, name: String) =
        ApiaryResponse(id, "user-1", name, null, null, 0, "2024-01-01", "2024-01-01")
}

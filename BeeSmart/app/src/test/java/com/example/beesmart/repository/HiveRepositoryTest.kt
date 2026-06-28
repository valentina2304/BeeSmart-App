package com.example.beesmart.repository

import com.example.beesmart.data.local.dao.HiveDao
import com.example.beesmart.data.local.dao.SyncQueueDao
import com.example.beesmart.data.local.entity.HiveEntity
import com.example.beesmart.data.local.entity.SyncStatus
import com.example.beesmart.data.repository.HiveRepository
import com.example.beesmart.data.repository.Result
import com.example.beesmart.network.BackendReachability
import com.example.beesmart.network.HiveApi
import com.example.beesmart.network.models.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.*
import io.mockk.impl.annotations.MockK
import com.example.beesmart.sync.ConnectivityObserver
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class HiveRepositoryTest {

    @MockK lateinit var hiveApi: HiveApi
    @MockK lateinit var hiveDao: HiveDao
    @MockK lateinit var syncQueueDao: SyncQueueDao
    @MockK lateinit var connectivity: ConnectivityObserver
    @MockK(relaxed = true) lateinit var backendReachability: BackendReachability

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private lateinit var repository: HiveRepository

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        every { backendReachability.isLikelyUnreachable() } returns false
        repository = HiveRepository(hiveApi, hiveDao, syncQueueDao, connectivity, backendReachability, moshi)
    }

    // ==================== CREATE ====================

    @Test
    fun `createHive offline inserts entity with PENDING_CREATE and queues sync`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { hiveDao.insert(any()) } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val request = CreateHiveRequest("Stupul 1", HiveType.Langstroth, HiveStatus.Active, "note")
        val result = repository.createHive("apiary-local-1", request)

        assertTrue(result is Result.Success)
        with(result as Result.Success) {
            assertEquals("Stupul 1", data.name)
        }
        coVerify {
            hiveDao.insert(match {
                it.name == "Stupul 1" &&
                it.serverId == null &&
                it.apiaryLocalId == "apiary-local-1" &&
                it.syncStatus == SyncStatus.PENDING_CREATE
            })
        }
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "CREATE" &&
                it.entityType == "HIVE" &&
                it.entityServerId == null
            })
        }
        coVerify(exactly = 0) { hiveApi.createHive(any(), any()) }
    }

    @Test
    fun `createHive online calls API and saves synced entity`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns true
        val serverResp = hiveResponse("srv-hive-1", "apiary-srv-1", "Stupul 1")
        coEvery { hiveApi.createHive("apiary-srv-1", any()) } returns Response.success(serverResp)
        coEvery { hiveDao.insert(any()) } returns Unit

        val result = repository.createHive("apiary-srv-1", CreateHiveRequest("Stupul 1", HiveType.Langstroth))

        assertTrue(result is Result.Success)
        assertEquals("srv-hive-1", (result as Result.Success).data.id)
        coVerify { hiveApi.createHive("apiary-srv-1", any()) }
        coVerify { hiveDao.insert(match { it.serverId == "srv-hive-1" && it.syncStatus == SyncStatus.SYNCED }) }
    }

    @Test
    fun `createHive offline persists queen and frame details without manual last inspection`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { hiveDao.insert(any()) } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val request = CreateHiveRequest(
            name = "Stup complet",
            type = HiveType.Dadant,
            status = HiveStatus.Active,
            notes = "Familie buna",
            reginaPrezenta = true,
            varstaRegina = 2,
            rameAlbine = 8,
            ramePuiet = 5,
            rameMiere = 3
        )

        val result = repository.createHive("apiary-local-1", request)

        assertTrue(result is Result.Success)
        coVerify {
            hiveDao.insert(match {
                it.reginaPrezenta &&
                    it.varstaRegina == 2 &&
                    it.rameAlbine == 8 &&
                    it.ramePuiet == 5 &&
                    it.rameMiere == 3 &&
                    it.ultimaInspectie == null &&
                    it.syncStatus == SyncStatus.PENDING_CREATE
            })
        }
        coVerify {
            syncQueueDao.insert(match {
                it.payload.contains("\"reginaPrezenta\":true") &&
                    it.payload.contains("\"varstaRegina\":2") &&
                    it.payload.contains("\"rameAlbine\":8") &&
                    !it.payload.contains("ultimaInspectie")
            })
        }
    }

    @Test
    fun `createHive online network exception returns error`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { hiveApi.createHive(any(), any()) } throws RuntimeException("timeout")

        val result = repository.createHive("apiary-1", CreateHiveRequest("X", HiveType.Other))

        assertTrue(result is Result.Error)
    }

    // ==================== READ ====================

    @Test
    fun `getAllHives offline returns cached data`() = runTest {
        val cached = listOf(hiveEntity("loc-1", null, "apiary-loc"))
        coEvery { hiveDao.getAll() } returns cached
        every { connectivity.isCurrentlyOnline() } returns false

        val result = repository.getAllHives()

        assertTrue(result is Result.Success)
        assertEquals(1, (result as Result.Success).data.size)
        coVerify(exactly = 0) { hiveApi.getAllHives() }
    }

    @Test
    fun `getHivesByApiaryId offline returns cached hives for apiary`() = runTest {
        val cached = listOf(hiveEntity("loc-1", null, "apiary-1"))
        coEvery { hiveDao.getByApiaryId("apiary-1") } returns cached
        every { connectivity.isCurrentlyOnline() } returns false

        val result = repository.getHivesByApiaryId("apiary-1")

        assertTrue(result is Result.Success)
        assertEquals(1, (result as Result.Success).data.size)
        coVerify(exactly = 0) { hiveApi.getHivesByApiaryId(any()) }
    }

    @Test
    fun `getHivesByApiaryId online replaces cache for apiary`() = runTest {
        coEvery { hiveDao.getByApiaryId("apiary-1") } returns emptyList()
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { hiveApi.getHivesByApiaryId("apiary-1") } returns
            Response.success(listOf(hiveResponse("srv-1", "apiary-1", "Stupul")))
        coEvery { hiveDao.deleteSyncedByApiaryId("apiary-1") } returns Unit
        coEvery { hiveDao.insertAll(any()) } returns Unit

        val result = repository.getHivesByApiaryId("apiary-1")

        assertTrue(result is Result.Success)
        coVerify { hiveDao.deleteSyncedByApiaryId("apiary-1") }
        coVerify { hiveDao.insertAll(any()) }
    }

    @Test
    fun `getHivesByApiaryId online preserves local id alias for synced hive`() = runTest {
        val existing = hiveEntity("local-qr-id", "srv-hive-1", "apiary-local")
        coEvery { hiveDao.getByApiaryId("apiary-srv") } returnsMany listOf(
            listOf(existing),
            listOf(existing)
        )
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { hiveApi.getHivesByApiaryId("apiary-srv") } returns
            Response.success(listOf(hiveResponse("srv-hive-1", "apiary-srv", "Stupul actualizat")))
        coEvery { hiveDao.deleteSyncedByApiaryId("apiary-srv") } returns Unit
        coEvery { hiveDao.insertAll(any()) } returns Unit

        val result = repository.getHivesByApiaryId("apiary-srv")

        assertTrue(result is Result.Success)
        coVerify {
            hiveDao.insertAll(match { entities ->
                entities.single().localId == "local-qr-id" &&
                    entities.single().serverId == "srv-hive-1" &&
                    entities.single().apiaryLocalId == "apiary-local"
            })
        }
    }

    @Test
    fun `getHiveById offline returns cached hive by localId`() = runTest {
        val entity = hiveEntity("loc-1", null, "apiary-1")
        coEvery { hiveDao.getByLocalId("loc-1") } returns entity
        every { connectivity.isCurrentlyOnline() } returns false

        val result = repository.getHiveById("loc-1")

        assertTrue(result is Result.Success)
        coVerify(exactly = 0) { hiveApi.getHiveById(any()) }
    }

    @Test
    fun `getHiveById offline not found returns error`() = runTest {
        coEvery { hiveDao.getByLocalId("missing") } returns null
        coEvery { hiveDao.getByServerId("missing") } returns null
        every { connectivity.isCurrentlyOnline() } returns false

        val result = repository.getHiveById("missing")

        assertTrue(result is Result.Error)
    }

    // ==================== UPDATE ====================

    @Test
    fun `updateHive offline updates local entity with PENDING_UPDATE and queues sync`() = runTest {
        val existing = hiveEntity("loc-1", "srv-1", "apiary-1")
        coEvery { hiveDao.getByLocalId("loc-1") } returns existing
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { hiveDao.update(any()) } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val result = repository.updateHive("loc-1", UpdateHiveRequest("New Name", HiveType.Dadant, HiveStatus.Weak))

        assertTrue(result is Result.Success)
        assertEquals("New Name", (result as Result.Success).data.name)
        coVerify {
            hiveDao.update(match {
                it.name == "New Name" && it.syncStatus == SyncStatus.PENDING_UPDATE
            })
        }
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "UPDATE" &&
                it.entityType == "HIVE" &&
                it.entityLocalId == "loc-1" &&
                it.entityServerId == "srv-1"
            })
        }
    }

    @Test
    fun `updateHive online calls API and stores updated entity`() = runTest {
        val existing = hiveEntity("loc-1", "srv-1", "apiary-1")
        coEvery { hiveDao.getByLocalId("loc-1") } returns existing
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { hiveApi.updateHive("loc-1", any()) } returns
            Response.success(hiveResponse("srv-1", "apiary-1", "Updated"))
        coEvery { hiveDao.insert(any()) } returns Unit

        val result = repository.updateHive("loc-1", UpdateHiveRequest("Updated", HiveType.Langstroth, HiveStatus.Active))

        assertTrue(result is Result.Success)
        coVerify { hiveApi.updateHive("loc-1", any()) }
    }

    @Test
    fun `updateHive offline preserves automatic last inspection while updating queen and frames`() = runTest {
        val existing = hiveEntity("loc-1", "srv-1", "apiary-1").copy(
            ultimaInspectie = "2026-05-04T10:00:00Z"
        )
        coEvery { hiveDao.getByLocalId("loc-1") } returns existing
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { hiveDao.update(any()) } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val result = repository.updateHive(
            "loc-1",
            UpdateHiveRequest(
                name = "Stup actualizat",
                type = HiveType.Langstroth,
                status = HiveStatus.Active,
                notes = null,
                reginaPrezenta = true,
                varstaRegina = 1,
                rameAlbine = 9,
                ramePuiet = 6,
                rameMiere = 4
            )
        )

        assertTrue(result is Result.Success)
        coVerify {
            hiveDao.update(match {
                it.name == "Stup actualizat" &&
                    it.reginaPrezenta &&
                    it.varstaRegina == 1 &&
                    it.rameAlbine == 9 &&
                    it.ramePuiet == 6 &&
                    it.rameMiere == 4 &&
                    it.ultimaInspectie == "2026-05-04T10:00:00Z" &&
                    it.syncStatus == SyncStatus.PENDING_UPDATE
            })
        }
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "UPDATE" &&
                    it.payload.contains("\"rameMiere\":4") &&
                    !it.payload.contains("ultimaInspectie")
            })
        }
    }

    // ==================== DELETE ====================

    @Test
    fun `deleteHive offline local-only entity deletes immediately without queuing`() = runTest {
        val localOnly = hiveEntity("loc-1", null, "apiary-1", syncStatus = SyncStatus.PENDING_CREATE)
        coEvery { hiveDao.getByLocalId("loc-1") } returns localOnly
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { hiveDao.deleteByLocalId("loc-1") } returns Unit
        coEvery { syncQueueDao.deleteByEntityLocalId("loc-1") } returns Unit

        val result = repository.deleteHive("loc-1")

        assertTrue(result is Result.Success)
        coVerify { hiveDao.deleteByLocalId("loc-1") }
        coVerify { syncQueueDao.deleteByEntityLocalId("loc-1") }
        coVerify(exactly = 0) { syncQueueDao.insert(any()) }
    }

    @Test
    fun `deleteHive offline synced entity marks PENDING_DELETE and queues sync`() = runTest {
        val synced = hiveEntity("loc-1", "srv-1", "apiary-1", syncStatus = SyncStatus.SYNCED)
        coEvery { hiveDao.getByLocalId("loc-1") } returns synced
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { hiveDao.update(any()) } returns Unit
        coEvery { syncQueueDao.deleteByEntityLocalId("loc-1") } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val result = repository.deleteHive("loc-1")

        assertTrue(result is Result.Success)
        coVerify { hiveDao.update(match { it.syncStatus == SyncStatus.PENDING_DELETE }) }
        coVerify { syncQueueDao.deleteByEntityLocalId("loc-1") }
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "DELETE" &&
                it.entityType == "HIVE" &&
                it.entityServerId == "srv-1"
            })
        }
    }

    @Test
    fun `deleteHive online calls API and removes local entity`() = runTest {
        val entity = hiveEntity("loc-1", "srv-1", "apiary-1")
        coEvery { hiveDao.getByLocalId("srv-1") } returns null
        coEvery { hiveDao.getByServerId("srv-1") } returns entity
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { hiveApi.deleteHive("srv-1") } returns Response.success(Unit)
        coEvery { hiveDao.deleteByLocalId("loc-1") } returns Unit

        val result = repository.deleteHive("srv-1")

        assertTrue(result is Result.Success)
        coVerify { hiveApi.deleteHive("srv-1") }
        coVerify { hiveDao.deleteByLocalId("loc-1") }
    }

    @Test
    fun `deleteHive online API error returns Result Error`() = runTest {
        val entity = hiveEntity("loc-1", "srv-1", "apiary-1")
        coEvery { hiveDao.getByLocalId("srv-1") } returns null
        coEvery { hiveDao.getByServerId("srv-1") } returns entity
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { hiveApi.deleteHive("srv-1") } returns
            Response.error(404, "not found".toResponseBody("application/json".toMediaType()))

        val result = repository.deleteHive("srv-1")

        assertTrue(result is Result.Error)
    }

    // ==================== helpers ====================

    private fun hiveEntity(
        localId: String,
        serverId: String?,
        apiaryLocalId: String,
        syncStatus: SyncStatus = SyncStatus.SYNCED
    ) = HiveEntity(
        localId = localId,
        serverId = serverId,
        apiaryLocalId = apiaryLocalId,
        apiaryServerId = serverId?.let { "apiary-srv" },
        apiaryName = "Test Apiary",
        name = "Stupul $localId",
        type = HiveType.Langstroth.name,
        status = HiveStatus.Active.name,
        notes = null,
        syncStatus = syncStatus
    )

    private fun hiveResponse(id: String, apiaryId: String, name: String) =
        HiveResponse(id, apiaryId, "Test Apiary", name, HiveType.Langstroth, HiveStatus.Active, null, "", "")
}

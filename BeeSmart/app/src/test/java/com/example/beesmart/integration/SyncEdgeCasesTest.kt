package com.example.beesmart.integration

import com.example.beesmart.data.local.entity.ApiaryEntity
import com.example.beesmart.data.local.entity.HiveEntity
import com.example.beesmart.data.local.entity.SyncStatus
import com.example.beesmart.data.repository.Result
import com.example.beesmart.network.models.CreateApiaryRequest
import com.example.beesmart.network.models.CreateHiveRequest
import com.example.beesmart.network.models.HiveStatus
import com.example.beesmart.network.models.HiveType
import com.example.beesmart.network.models.UpdateApiaryRequest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Edge cases for the sync engine — these are the scenarios where bugs are most
 * likely to hide: retries, partial failures, ordering, malformed payloads,
 * the deferred-update path, and the network-IOException path.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class SyncEdgeCasesTest {

    private lateinit var h: SyncTestHarness

    @Before
    fun setUp() {
        h = SyncTestHarness.create()
    }

    @After
    fun tearDown() {
        h.tearDown()
    }

    // ---- RETRY POLICY ----

    @Test
    fun `repeated 500 errors bump retry count up to 3 then mark entity SYNC_FAILED`() = runTest {
        h.setOffline()
        val created = (h.apiaryRepository.createApiary(
            CreateApiaryRequest("Retry Apiary", null, null)
        ) as Result.Success).data
        val localId = created.id

        h.setOnline()
        // Three 500s in a row
        h.mockServer.enqueue(MockResponses.serverError())
        h.mockServer.enqueue(MockResponses.serverError())
        h.mockServer.enqueue(MockResponses.serverError())

        // First attempt: retryCount 0 → 1
        h.syncManager.processQueue()
        var op = h.db.syncQueueDao().getAll().single()
        assertEquals(1, op.retryCount)
        assertEquals(SyncStatus.PENDING_CREATE, h.db.apiaryDao().getByLocalId(localId)!!.syncStatus)

        // Second: retryCount 1 → 2
        h.syncManager.processQueue()
        op = h.db.syncQueueDao().getAll().single()
        assertEquals(2, op.retryCount)
        assertEquals(SyncStatus.PENDING_CREATE, h.db.apiaryDao().getByLocalId(localId)!!.syncStatus)

        // Third: retryCount 2 → 3 → entity flagged SYNC_FAILED
        h.syncManager.processQueue()
        op = h.db.syncQueueDao().getAll().single()
        assertEquals(3, op.retryCount)
        assertEquals(
            "Entity must be flagged SYNC_FAILED once retries exhausted",
            SyncStatus.SYNC_FAILED, h.db.apiaryDao().getByLocalId(localId)!!.syncStatus
        )

        // 3 HTTP attempts total
        assertEquals(3, h.mockServer.requestCount)
    }

    @Test
    fun `op already past MAX_RETRIES skips the network call entirely`() = runTest {
        // Seed an entity + queue row at retryCount = 3
        h.db.apiaryDao().insert(
            ApiaryEntity("loc-x", null, "X", null, null, syncStatus = SyncStatus.PENDING_CREATE)
        )
        val payload = """{"name":"X","description":null,"location":null}"""
        h.db.syncQueueDao().insert(
            com.example.beesmart.data.local.entity.SyncQueueEntity(
                operationType = "CREATE", entityType = "APIARY",
                entityLocalId = "loc-x", entityServerId = null,
                payload = payload, retryCount = 3
            )
        )

        h.setOnline()
        h.syncManager.processQueue()

        // No HTTP request was made — the op was rejected at the gate
        assertEquals(0, h.mockServer.requestCount)
        // Entity is flagged failed
        assertEquals(
            SyncStatus.SYNC_FAILED,
            h.db.apiaryDao().getByLocalId("loc-x")!!.syncStatus
        )
    }

    // ---- NETWORK FAULT (IOException) ----

    @Test
    fun `network IOException does NOT bump retry counter so transient outages do not waste retries`() = runTest {
        h.setOffline()
        val created = (h.apiaryRepository.createApiary(
            CreateApiaryRequest("Net Apiary", null, null)
        ) as Result.Success).data

        h.setOnline()
        // Socket disconnect → IOException
        h.mockServer.enqueue(MockResponses.networkFailure())
        h.syncManager.processQueue()

        val op = h.db.syncQueueDao().getAll().single()
        assertEquals(
            "IOException must NOT count toward MAX_RETRIES (transient by design)",
            0, op.retryCount
        )
        assertEquals(
            SyncStatus.PENDING_CREATE,
            h.db.apiaryDao().getByLocalId(created.id)!!.syncStatus
        )

        // Now real success on next pass
        h.mockServer.enqueue(MockResponses.apiary("srv-OK"))
        h.syncManager.processQueue()

        assertEquals(0, h.db.syncQueueDao().getAll().size)
        assertEquals("srv-OK", h.db.apiaryDao().getByLocalId(created.id)!!.serverId)
    }

    // ---- DEFERRED OPS ----

    @Test
    fun `hive CREATE with unsynced parent apiary defers and stays in queue, parent sync resolves it`() = runTest {
        h.setOffline()
        val apiary = (h.apiaryRepository.createApiary(
            CreateApiaryRequest("Parent A", null, null)
        ) as Result.Success).data
        val hive = (h.hiveRepository.createHive(
            apiary.id, CreateHiveRequest("Child H", HiveType.Langstroth, HiveStatus.Active, null)
        ) as Result.Success).data

        h.setOnline()
        // Only enqueue apiary success — hive should still go through after parent resolves
        // but inside the SAME processQueue call (createOrder runs APIARY then HIVE)
        h.mockServer.enqueue(MockResponses.apiary("srv-AP"))
        h.mockServer.enqueue(MockResponses.hive("srv-HV", "srv-AP"))

        h.syncManager.processQueue()

        // Both ops drained, hive carries propagated apiaryServerId
        assertEquals(0, h.db.syncQueueDao().getAll().size)
        val hiveRow = h.db.hiveDao().getByLocalId(hive.id)!!
        assertEquals("srv-HV", hiveRow.serverId)
        assertEquals("srv-AP", hiveRow.apiaryServerId)
    }

    @Test
    fun `if parent apiary FAILS in pass, child hive create defers and queue keeps both ops`() = runTest {
        h.setOffline()
        val apiary = (h.apiaryRepository.createApiary(
            CreateApiaryRequest("Parent A", null, null)
        ) as Result.Success).data
        val hive = (h.hiveRepository.createHive(
            apiary.id, CreateHiveRequest("Child H", HiveType.Langstroth, HiveStatus.Active, null)
        ) as Result.Success).data

        h.setOnline()
        // Apiary CREATE returns 500
        h.mockServer.enqueue(MockResponses.serverError())
        // Hive request will NOT be made because parent is unresolved
        h.syncManager.processQueue()

        // Only one HTTP request (the apiary one)
        assertEquals(1, h.mockServer.requestCount)

        val queue = h.db.syncQueueDao().getAll()
        assertEquals(2, queue.size)
        // Apiary op retryCount bumped; hive op untouched
        val apiaryOp = queue.first { it.entityType == "APIARY" }
        val hiveOp = queue.first { it.entityType == "HIVE" }
        assertEquals(1, apiaryOp.retryCount)
        assertEquals(0, hiveOp.retryCount)

        // The hive entity remains PENDING_CREATE with no serverId
        val hiveRow = h.db.hiveDao().getByLocalId(hive.id)!!
        assertNull(hiveRow.serverId)
        assertEquals(SyncStatus.PENDING_CREATE, hiveRow.syncStatus)
    }

    // ---- PROCESSING ORDER ----

    @Test
    fun `processQueue runs CREATEs strictly before UPDATEs and DELETEs`() = runTest {
        // Seed: synced apiary (so we can DELETE it server-side); offline new apiary; offline update on the synced one
        h.db.apiaryDao().insert(
            ApiaryEntity("loc-existing", "srv-existing", "Existing", null, null, syncStatus = SyncStatus.SYNCED)
        )

        h.setOffline()
        // 1) Update existing apiary → produces UPDATE op (serverId in op)
        h.apiaryRepository.updateApiary(
            "loc-existing",
            UpdateApiaryRequest(name = "Existing renamed", description = null, location = null)
        )
        // 2) Create a brand-new apiary → CREATE op
        h.apiaryRepository.createApiary(
            CreateApiaryRequest("Brand new", null, null)
        )

        // Queue order on insert is: UPDATE, CREATE — so we can prove processQueue reorders.
        val queueBefore = h.db.syncQueueDao().getAll()
        assertEquals(2, queueBefore.size)
        assertEquals("UPDATE", queueBefore[0].operationType)
        assertEquals("CREATE", queueBefore[1].operationType)

        h.setOnline()
        // Expect the dispatcher to issue CREATE first, then UPDATE
        h.mockServer.enqueue(MockResponses.apiary("srv-new"))
        h.mockServer.enqueue(MockResponses.apiary("srv-existing", "Existing renamed"))

        h.syncManager.processQueue()

        val first = h.mockServer.takeRequest()
        val second = h.mockServer.takeRequest()
        assertEquals("CREATE before UPDATE in dispatch order", "POST", first.method)
        assertEquals("/api/apiaries", first.path)
        assertEquals("PUT", second.method)
        assertEquals("/api/apiaries/srv-existing", second.path)
        assertEquals(0, h.db.syncQueueDao().getAll().size)
    }

    // ---- MIXED CRUD COLLAPSING ----

    @Test
    fun `update queued before CREATE syncs resolves serverId at dispatch time`() = runTest {
        h.setOffline()
        val created = (h.apiaryRepository.createApiary(
            CreateApiaryRequest("Iter A", null, null)
        ) as Result.Success).data
        h.apiaryRepository.updateApiary(
            created.id,
            UpdateApiaryRequest("Iter A v2", "edited", null)
        )

        // 2 queued ops; UPDATE op has no entityServerId yet (CREATE hasn't synced)
        val updateOpBefore = h.db.syncQueueDao().getAll().first { it.operationType == "UPDATE" }
        assertNull(updateOpBefore.entityServerId)

        h.setOnline()
        h.mockServer.enqueue(MockResponses.apiary("srv-A"))
        h.mockServer.enqueue(MockResponses.apiary("srv-A", "Iter A v2"))
        h.syncManager.processQueue()
        assertEquals(2, h.mockServer.requestCount)
        val createReq = h.mockServer.takeRequest()
        val updateReq = h.mockServer.takeRequest()
        assertEquals("POST", createReq.method)
        assertEquals("/api/apiaries", createReq.path)
        assertEquals("PUT", updateReq.method)
        assertEquals("/api/apiaries/srv-A", updateReq.path)
        assertEquals(0, h.db.syncQueueDao().getAll().size)
        assertEquals("srv-A", h.db.apiaryDao().getByLocalId(created.id)!!.serverId)
    }

    // ---- MALFORMED PAYLOADS ----

    @Test
    fun `malformed payload in queue is dropped without retries and without HTTP call`() = runTest {
        // Seed entity + a queue row with garbage payload
        h.db.apiaryDao().insert(
            ApiaryEntity("loc-bad", null, "Bad", null, null, syncStatus = SyncStatus.PENDING_CREATE)
        )
        h.db.syncQueueDao().insert(
            com.example.beesmart.data.local.entity.SyncQueueEntity(
                operationType = "CREATE", entityType = "APIARY",
                entityLocalId = "loc-bad", entityServerId = null,
                payload = "{not valid json",
                retryCount = 0
            )
        )

        h.setOnline()
        h.syncManager.processQueue()

        // Op was dropped (no retries), no HTTP issued
        assertEquals(0, h.mockServer.requestCount)
        assertEquals(0, h.db.syncQueueDao().getAll().size)
    }

    // ---- IDEMPOTENCY: re-running processQueue after success ----

    @Test
    fun `running processQueue twice after success does nothing the second time`() = runTest {
        h.setOffline()
        val created = (h.apiaryRepository.createApiary(
            CreateApiaryRequest("Idem A", null, null)
        ) as Result.Success).data

        h.setOnline()
        h.mockServer.enqueue(MockResponses.apiary("srv-i"))
        h.syncManager.processQueue()
        assertEquals(1, h.mockServer.requestCount)
        assertEquals(0, h.db.syncQueueDao().getAll().size)

        // Second call: queue empty, no HTTP issued
        h.syncManager.processQueue()
        assertEquals(1, h.mockServer.requestCount)

        // Entity still SYNCED with assigned serverId
        val row = h.db.apiaryDao().getByLocalId(created.id)!!
        assertEquals("srv-i", row.serverId)
        assertEquals(SyncStatus.SYNCED, row.syncStatus)
    }

    // ---- DELETE LOCAL-ONLY ENTITY: CREATE op gets dropped before HTTP ----

    @Test
    fun `delete on a never-synced entity drops queued CREATE without HTTP call`() = runTest {
        h.setOffline()
        val created = (h.apiaryRepository.createApiary(
            CreateApiaryRequest("Throwaway", null, null)
        ) as Result.Success).data

        // Repository removes both entity row and CREATE queue row immediately when offline+local-only
        h.apiaryRepository.deleteApiary(created.id)
        assertEquals(0, h.db.syncQueueDao().getAll().size)
        assertNull(h.db.apiaryDao().getByLocalId(created.id))

        h.setOnline()
        h.syncManager.processQueue()
        assertEquals(0, h.mockServer.requestCount)
    }

    // ---- 404 ON DELETE: entity already gone server-side ----

    @Test
    fun `404 on DELETE bumps retry like other errors but eventually marks failed`() = runTest {
        h.db.apiaryDao().insert(
            ApiaryEntity("loc-d", "srv-d", "Doomed", null, null, syncStatus = SyncStatus.SYNCED)
        )
        h.setOffline()
        h.apiaryRepository.deleteApiary("loc-d")

        h.setOnline()
        // 404 three times
        repeat(3) { h.mockServer.enqueue(MockResponses.notFound()) }

        h.syncManager.processQueue()
        h.syncManager.processQueue()
        h.syncManager.processQueue()

        assertEquals(3, h.mockServer.requestCount)
        // After 3 failed DELETEs, entity should be flagged
        assertEquals(
            SyncStatus.SYNC_FAILED,
            h.db.apiaryDao().getByLocalId("loc-d")!!.syncStatus
        )
    }

    // ---- INTERLEAVED PARENT SYNC FAILURE ----

    @Test
    fun `if hive's parent apiary fails permanently, hive create stays deferred indefinitely`() = runTest {
        h.setOffline()
        val apiary = (h.apiaryRepository.createApiary(
            CreateApiaryRequest("Parent A", null, null)
        ) as Result.Success).data
        val hive = (h.hiveRepository.createHive(
            apiary.id, CreateHiveRequest("Child H", HiveType.Other, HiveStatus.Active, null)
        ) as Result.Success).data

        h.setOnline()
        // Apiary fails 3 times
        repeat(3) { h.mockServer.enqueue(MockResponses.serverError()) }

        // Three full passes
        h.syncManager.processQueue()
        h.syncManager.processQueue()
        h.syncManager.processQueue()

        // Apiary marked SYNC_FAILED; queue still holds the apiary op (retryCount=3) and the hive op (untouched, retryCount=0)
        assertEquals(
            SyncStatus.SYNC_FAILED,
            h.db.apiaryDao().getByLocalId(apiary.id)!!.syncStatus
        )
        val queue = h.db.syncQueueDao().getAll()
        val apiaryOp = queue.firstOrNull { it.entityType == "APIARY" }
        val hiveOp = queue.firstOrNull { it.entityType == "HIVE" }
        assertNotNull("Apiary op must remain so retry-stuck state is visible", apiaryOp)
        assertNotNull("Hive op must remain — never had a chance to fire", hiveOp)
        assertEquals(3, apiaryOp!!.retryCount)
        assertEquals(0, hiveOp!!.retryCount)

        // Hive entity still PENDING_CREATE without serverId
        val hiveRow = h.db.hiveDao().getByLocalId(hive.id)!!
        assertNull(hiveRow.serverId)
        assertNotEquals(SyncStatus.SYNCED, hiveRow.syncStatus)

        // No further calls trigger
        val before = h.mockServer.requestCount
        h.syncManager.processQueue()
        assertEquals(
            "After MAX_RETRIES, processQueue must not keep hammering the apiary endpoint",
            before, h.mockServer.requestCount
        )
    }
}

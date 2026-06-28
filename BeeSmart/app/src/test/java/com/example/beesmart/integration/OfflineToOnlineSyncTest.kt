package com.example.beesmart.integration

import com.example.beesmart.data.local.entity.SyncStatus
import com.example.beesmart.data.repository.Result
import com.example.beesmart.network.models.CreateApiaryRequest
import com.example.beesmart.network.models.CreateExtractionRequest
import com.example.beesmart.network.models.CreateHiveRequest
import com.example.beesmart.network.models.CreateInspectionRequest
import com.example.beesmart.network.models.CreateTaskRequest
import com.example.beesmart.network.models.CreateTreatmentRequest
import com.example.beesmart.network.models.ExtractionType
import com.example.beesmart.network.models.HiveStatus
import com.example.beesmart.network.models.HiveType
import com.example.beesmart.network.models.TreatmentType
import com.example.beesmart.network.models.UpdateApiaryRequest
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * End-to-end integration tests covering the full offline→online sync flow.
 *
 * Each test:
 *  1. Sets the harness offline
 *  2. Performs CRUD via the real Repository (writes Room + sync_queue)
 *  3. Sets the harness online and enqueues a MockWebServer response
 *  4. Calls [SyncManager.processQueue]
 *  5. Asserts on Room state, the actual HTTP request received, and the queue.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class OfflineToOnlineSyncTest {

    private lateinit var h: SyncTestHarness

    @Before
    fun setUp() {
        h = SyncTestHarness.create()
    }

    @After
    fun tearDown() {
        h.tearDown()
    }

    // -------- APIARY --------

    @Test
    fun `apiary created offline syncs to server with proper payload and updates local serverId`() = runTest {
        h.setOffline()
        val createResult = h.apiaryRepository.createApiary(
            CreateApiaryRequest(name = "Stupina Cluj", description = "Pe deal", location = "Cluj")
        )
        assertTrue(createResult is Result.Success)

        val localId = (createResult as Result.Success).data.id
        // Before sync: entity exists, has no serverId, status PENDING_CREATE, queue has one row
        val before = h.db.apiaryDao().getByLocalId(localId)!!
        assertNull(before.serverId)
        assertEquals(SyncStatus.PENDING_CREATE, before.syncStatus)
        assertEquals(1, h.db.syncQueueDao().getAll().size)

        // Server is now reachable, will return assigned serverId
        h.setOnline()
        h.mockServer.enqueue(MockResponses.apiary(id = "srv-apiary-1", name = "Stupina Cluj"))

        h.syncManager.processQueue()

        // 1. Server received POST /api/apiaries with correct body
        val req = h.mockServer.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/apiaries", req.path)
        val body = req.body.readUtf8()
        assertTrue("body should contain name", body.contains("Stupina Cluj"))
        assertTrue("body should contain location", body.contains("Cluj"))

        // 2. Local entity now has serverId and SYNCED status
        val after = h.db.apiaryDao().getByLocalId(localId)!!
        assertEquals("srv-apiary-1", after.serverId)
        assertEquals(SyncStatus.SYNCED, after.syncStatus)

        // 3. Queue is empty
        assertEquals(0, h.db.syncQueueDao().getAll().size)
    }

    @Test
    fun `update on a previously-synced apiary syncs PUT with the known serverId`() = runTest {
        // Seed a synced apiary so we focus on the UPDATE path
        h.db.apiaryDao().insert(
            com.example.beesmart.data.local.entity.ApiaryEntity(
                localId = "loc-A",
                serverId = "srv-A",
                name = "Original",
                description = null,
                location = null,
                syncStatus = SyncStatus.SYNCED
            )
        )

        h.setOffline()
        val updateResult = h.apiaryRepository.updateApiary(
            "loc-A",
            UpdateApiaryRequest(name = "Renamed", description = "edited", location = "Iasi")
        )
        assertTrue(updateResult is Result.Success)

        val pending = h.db.apiaryDao().getByLocalId("loc-A")!!
        assertEquals(SyncStatus.PENDING_UPDATE, pending.syncStatus)
        assertEquals("Renamed", pending.name)
        // UPDATE op holds the serverId — SyncManager will use it directly
        val op = h.db.syncQueueDao().getAll().single()
        assertEquals("UPDATE", op.operationType)
        assertEquals("srv-A", op.entityServerId)

        h.setOnline()
        h.mockServer.enqueue(MockResponses.apiary("srv-A", "Renamed"))
        h.syncManager.processQueue()

        val req = h.mockServer.takeRequest()
        assertEquals("PUT", req.method)
        assertEquals("/api/apiaries/srv-A", req.path)
        assertTrue(req.body.readUtf8().contains("Renamed"))

        assertEquals(SyncStatus.SYNCED, h.db.apiaryDao().getByLocalId("loc-A")!!.syncStatus)
        assertEquals(0, h.db.syncQueueDao().getAll().size)
    }

    @Test
    fun `apiary deleted offline before ever syncing removes locally without HTTP call`() = runTest {
        h.setOffline()
        val created = (h.apiaryRepository.createApiary(
            CreateApiaryRequest(name = "Throwaway", description = null, location = null)
        ) as Result.Success).data

        // Delete while still offline & local-only
        val deleteResult = h.apiaryRepository.deleteApiary(created.id)
        assertTrue(deleteResult is Result.Success)

        // Repository should have wiped both the entity row and the CREATE queue entry
        // (since there's nothing to delete on the server).
        assertNull(h.db.apiaryDao().getByLocalId(created.id))

        // Now go online and processQueue: must NOT issue any HTTP request
        h.setOnline()
        h.syncManager.processQueue()

        // No requests landed at the mock server
        assertEquals(0, h.mockServer.requestCount)
    }

    @Test
    fun `apiary deleted offline AFTER prior server sync issues DELETE on processQueue`() = runTest {
        // Seed a synced apiary with a serverId already known
        val existing = com.example.beesmart.data.local.entity.ApiaryEntity(
            localId = "loc-1",
            serverId = "srv-99",
            name = "Old Apiary",
            description = null,
            location = null,
            syncStatus = SyncStatus.SYNCED
        )
        h.db.apiaryDao().insert(existing)

        h.setOffline()
        val deleteResult = h.apiaryRepository.deleteApiary("loc-1")
        assertTrue(deleteResult is Result.Success)

        // PENDING_DELETE marker + queue entry containing serverId
        val marked = h.db.apiaryDao().getByLocalId("loc-1")!!
        assertEquals(SyncStatus.PENDING_DELETE, marked.syncStatus)
        val queueRow = h.db.syncQueueDao().getAll().single()
        assertEquals("DELETE", queueRow.operationType)
        assertEquals("srv-99", queueRow.entityServerId)

        // Online: server confirms deletion
        h.setOnline()
        h.mockServer.enqueue(MockResponses.emptyOk())
        h.syncManager.processQueue()

        val req = h.mockServer.takeRequest()
        assertEquals("DELETE", req.method)
        assertEquals("/api/apiaries/srv-99", req.path)

        // Queue cleared. (Note: SyncManager doesn't itself delete the local row;
        // it only clears the queue entry. The PENDING_DELETE row remains until
        // the repository or a sweep reaps it. The contract being tested here is
        // that the HTTP DELETE was issued and the queue is drained.)
        assertEquals(0, h.db.syncQueueDao().getAll().size)
    }

    // -------- HIVE (parent → child propagation) --------

    @Test
    fun `apiary plus hive created offline sync in dependency order with proper id propagation`() = runTest {
        h.setOffline()
        val apiary = (h.apiaryRepository.createApiary(
            CreateApiaryRequest(name = "Stupina P", description = null, location = null)
        ) as Result.Success).data
        val apiaryLocalId = apiary.id

        val hive = (h.hiveRepository.createHive(
            apiaryLocalId,
            CreateHiveRequest(name = "Stup #1", type = HiveType.Langstroth, status = HiveStatus.Active, notes = null)
        ) as Result.Success).data
        val hiveLocalId = hive.id

        // Two CREATEs queued, hive has no apiaryServerId yet
        val hiveBefore = h.db.hiveDao().getByLocalId(hiveLocalId)!!
        assertNull(hiveBefore.serverId)
        assertNull(hiveBefore.apiaryServerId)
        assertEquals(apiaryLocalId, hiveBefore.apiaryLocalId)

        h.setOnline()
        // Order in the dispatcher is APIARY → HIVE, so enqueue apiary first
        h.mockServer.enqueue(MockResponses.apiary(id = "srv-apiary-1"))
        h.mockServer.enqueue(MockResponses.hive(id = "srv-hive-1", apiaryId = "srv-apiary-1"))

        h.syncManager.processQueue()

        // Verify HTTP path of HIVE used the resolved apiary serverId, not localId
        val apiaryReq = h.mockServer.takeRequest()
        assertEquals("POST", apiaryReq.method)
        assertEquals("/api/apiaries", apiaryReq.path)

        val hiveReq = h.mockServer.takeRequest()
        assertEquals("POST", hiveReq.method)
        assertEquals(
            "Hive must be POSTed under the resolved server apiary id",
            "/api/hives/apiary/srv-apiary-1", hiveReq.path
        )

        // Both entities now have serverId & SYNCED, hive carries the apiary serverId
        val apiaryAfter = h.db.apiaryDao().getByLocalId(apiaryLocalId)!!
        val hiveAfter = h.db.hiveDao().getByLocalId(hiveLocalId)!!
        assertEquals("srv-apiary-1", apiaryAfter.serverId)
        assertEquals(SyncStatus.SYNCED, apiaryAfter.syncStatus)
        assertEquals("srv-hive-1", hiveAfter.serverId)
        assertEquals("srv-apiary-1", hiveAfter.apiaryServerId)
        assertEquals(SyncStatus.SYNCED, hiveAfter.syncStatus)

        assertEquals(0, h.db.syncQueueDao().getAll().size)
    }

    // -------- TASK with parent dependencies --------

    @Test
    fun `task created offline against unsynced apiary and hive defers until parents sync`() = runTest {
        h.setOffline()
        val apiary = (h.apiaryRepository.createApiary(
            CreateApiaryRequest("Stupina T", null, null)
        ) as Result.Success).data
        val hive = (h.hiveRepository.createHive(
            apiary.id, CreateHiveRequest("Stup T1", HiveType.Dadant, HiveStatus.Active, null)
        ) as Result.Success).data

        h.taskRepository.createTask(
            CreateTaskRequest(
                title = "Hraneste familia",
                hiveId = hive.id,
                apiaryId = apiary.id
            )
        )

        // 3 queued ops, all CREATE; ordering APIARY → HIVE → TASK is enforced by SyncManager
        assertEquals(3, h.db.syncQueueDao().getAll().size)

        h.setOnline()
        h.mockServer.enqueue(MockResponses.apiary("srv-A"))
        h.mockServer.enqueue(MockResponses.hive("srv-H", "srv-A"))
        h.mockServer.enqueue(MockResponses.task(id = "srv-T", hiveId = "srv-H", apiaryId = "srv-A"))

        h.syncManager.processQueue()

        // Three sequential POSTs in correct order
        val r1 = h.mockServer.takeRequest()
        assertEquals("/api/apiaries", r1.path)
        val r2 = h.mockServer.takeRequest()
        assertEquals("/api/hives/apiary/srv-A", r2.path)
        val r3 = h.mockServer.takeRequest()
        assertEquals("/api/tasks", r3.path)

        // Task body contains RESOLVED server ids, not local UUIDs
        val taskBody = r3.body.readUtf8()
        assertTrue("Task payload should reference server hive id", taskBody.contains("srv-H"))
        assertTrue("Task payload should reference server apiary id", taskBody.contains("srv-A"))
        assertTrue("Task payload should NOT contain local uuid", !taskBody.contains(hive.id))

        // Queue drained; all entities SYNCED
        assertEquals(0, h.db.syncQueueDao().getAll().size)
    }

    // -------- TREATMENT (child of hive) --------

    @Test
    fun `treatment created offline syncs after parent hive and persists serverId`() = runTest {
        // Seed a fully-synced parent apiary + hive so we focus on TREATMENT path
        h.db.apiaryDao().insert(
            com.example.beesmart.data.local.entity.ApiaryEntity(
                localId = "ap-loc", serverId = "ap-srv", name = "X",
                description = null, location = null, syncStatus = SyncStatus.SYNCED
            )
        )
        h.db.hiveDao().insert(
            com.example.beesmart.data.local.entity.HiveEntity(
                localId = "hv-loc", serverId = "hv-srv",
                apiaryLocalId = "ap-loc", apiaryServerId = "ap-srv",
                apiaryName = "X", name = "H1", type = "Langstroth",
                status = "Active", notes = null, syncStatus = SyncStatus.SYNCED
            )
        )

        h.setOffline()
        val res = h.treatmentRepository.createTreatment(
            CreateTreatmentRequest(
                hiveId = "hv-loc",
                treatmentDate = "2025-04-01",
                type = TreatmentType.Varroa,
                productName = "Apivar",
                substance = "Amitraz",
                dosage = "2 strips",
                notes = null,
                nextTreatmentDate = null
            )
        )
        assertTrue(res is Result.Success)

        h.setOnline()
        h.mockServer.enqueue(MockResponses.treatment(id = "tr-srv", hiveId = "hv-srv"))
        h.syncManager.processQueue()

        val req = h.mockServer.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/api/treatments", req.path)
        val body = req.body.readUtf8()
        assertTrue("Treatment payload uses resolved hive serverId", body.contains("hv-srv"))

        // Treatment row now has serverId, hiveServerId and SYNCED
        val treatments = h.db.treatmentDao().getByHiveId("hv-loc")
        assertEquals(1, treatments.size)
        val t = treatments[0]
        assertEquals("tr-srv", t.serverId)
        assertEquals("hv-srv", t.hiveServerId)
        assertEquals(SyncStatus.SYNCED, t.syncStatus)
        assertEquals(0, h.db.syncQueueDao().getAll().size)
    }

    // -------- EXTRACTION (child of hive) --------

    @Test
    fun `extraction created offline against unsynced hive defers, then syncs after hive resolves`() = runTest {
        h.setOffline()
        val apiary = (h.apiaryRepository.createApiary(
            CreateApiaryRequest("Stupina E", null, null)
        ) as Result.Success).data
        val hive = (h.hiveRepository.createHive(
            apiary.id, CreateHiveRequest("E1", HiveType.Other, HiveStatus.Active, null)
        ) as Result.Success).data

        h.extractionRepository.createExtraction(
            CreateExtractionRequest(
                hiveId = hive.id,
                extractionDate = "2025-05-15",
                type = ExtractionType.Honey,
                quantity = 10.0,
                unit = "kg",
                notes = null
            )
        )

        h.setOnline()
        h.mockServer.enqueue(MockResponses.apiary("srv-A"))
        h.mockServer.enqueue(MockResponses.hive("srv-H", "srv-A"))
        h.mockServer.enqueue(MockResponses.extraction("srv-E", "srv-H"))

        h.syncManager.processQueue()

        assertEquals(3, h.mockServer.requestCount)
        val extractions = h.db.extractionDao().getByHiveId(hive.id)
        assertEquals(1, extractions.size)
        assertEquals("srv-E", extractions[0].serverId)
        assertEquals("srv-H", extractions[0].hiveServerId)
        assertEquals(SyncStatus.SYNCED, extractions[0].syncStatus)
        assertEquals(0, h.db.syncQueueDao().getAll().size)
    }

    // -------- INSPECTION V2 --------

    @Test
    fun `inspection v2 fields created offline are preserved and sent during sync`() = runTest {
        h.db.apiaryDao().insert(
            com.example.beesmart.data.local.entity.ApiaryEntity(
                localId = "ap-loc", serverId = "ap-srv", name = "Stupina",
                description = null, location = null, syncStatus = SyncStatus.SYNCED
            )
        )
        h.db.hiveDao().insert(
            com.example.beesmart.data.local.entity.HiveEntity(
                localId = "hv-loc", serverId = "hv-srv",
                apiaryLocalId = "ap-loc", apiaryServerId = "ap-srv",
                apiaryName = "Stupina", name = "H1", type = "Langstroth",
                status = "Active", notes = null, syncStatus = SyncStatus.SYNCED
            )
        )

        h.setOffline()
        val created = h.inspectionRepository.createInspection(
            CreateInspectionRequest(
                hiveId = "hv-loc",
                inspectionDate = "2025-05-20T08:00:00Z",
                temperature = 21.5,
                framesCount = 10,
                broodFrames = 5,
                honeyFrames = 3,
                pollenFrames = 1,
                queenSeen = true,
                eggsSeen = true,
                larvaeSeen = true,
                queenCellsSeen = true,
                queenCellsWithEggs = true,
                beardingAtEntrance = true,
                spaceNeeded = true,
                broodPattern = "compact",
                honeyCappingPercent = 75,
                feedingGiven = true,
                waterAvailable = true,
                moistureOrMold = false,
                deadBeesAtEntrance = true,
                unusualBehavior = true,
                temperament = "agresiv",
                oldCombsToReplace = 2
            )
        )
        assertTrue(created is Result.Success)
        val localId = (created as Result.Success).data.id
        val local = h.db.inspectionDao().getByLocalId(localId)!!
        assertEquals(SyncStatus.PENDING_CREATE, local.syncStatus)
        assertTrue(local.queenCellsWithEggs)
        assertTrue(local.beardingAtEntrance)
        assertTrue(local.spaceNeeded)
        assertEquals("compact", local.broodPattern)
        assertEquals(75, local.honeyCappingPercent)
        assertEquals("agresiv", local.temperament)
        assertEquals(2, local.oldCombsToReplace)

        h.setOnline()
        h.mockServer.enqueue(MockResponses.inspection(id = "ins-srv", hiveId = "hv-srv", apiaryId = "ap-srv"))

        h.syncManager.processQueue()

        val req = h.mockServer.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("/inspections", req.path)
        val body = req.body.readUtf8()
        assertTrue("Inspection payload uses resolved hive serverId", body.contains("\"hiveId\":\"hv-srv\""))
        assertTrue("V2 payload contains queen cells with eggs", body.contains("\"queenCellsWithEggs\":true"))
        assertTrue("V2 payload contains bearding", body.contains("\"beardingAtEntrance\":true"))
        assertTrue("V2 payload contains capping percent", body.contains("\"honeyCappingPercent\":75"))
        assertTrue("V2 payload contains temperament", body.contains("\"temperament\":\"agresiv\""))

        val synced = h.db.inspectionDao().getByLocalId(localId)!!
        assertEquals("ins-srv", synced.serverId)
        assertEquals("hv-srv", synced.hiveServerId)
        assertEquals(SyncStatus.SYNCED, synced.syncStatus)
        assertEquals(0, h.db.syncQueueDao().getAll().size)
    }
}

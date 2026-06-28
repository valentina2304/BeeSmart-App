package com.example.beesmart.repository

import com.example.beesmart.data.local.dao.SyncQueueDao
import com.example.beesmart.data.local.dao.TaskDao
import com.example.beesmart.data.local.entity.SyncStatus
import com.example.beesmart.data.local.entity.TaskEntity
import com.example.beesmart.data.repository.Result
import com.example.beesmart.data.repository.TaskRepository
import com.example.beesmart.network.BackendReachability
import com.example.beesmart.network.TaskApi
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

class TaskRepositoryTest {

    @MockK lateinit var taskApi: TaskApi
    @MockK lateinit var taskDao: TaskDao
    @MockK lateinit var syncQueueDao: SyncQueueDao
    @MockK lateinit var connectivity: ConnectivityObserver
    @MockK(relaxed = true) lateinit var backendReachability: BackendReachability

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private lateinit var repository: TaskRepository

    @Before
    fun setup() {
        MockKAnnotations.init(this)
        every { backendReachability.isLikelyUnreachable() } returns false
        repository = TaskRepository(taskApi, taskDao, syncQueueDao, connectivity, backendReachability, moshi)
    }

    // ==================== CREATE ====================

    @Test
    fun `createTask offline inserts with PENDING_CREATE and queues sync`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { taskDao.insert(any()) } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val request = CreateTaskRequest("Tratament varroa", priority = TaskPriority.High, dueDate = "2024-06-01")
        val result = repository.createTask(request)

        assertTrue(result is Result.Success)
        assertEquals("Tratament varroa", (result as Result.Success).data.title)
        coVerify {
            taskDao.insert(match {
                it.title == "Tratament varroa" &&
                it.serverId == null &&
                it.syncStatus == SyncStatus.PENDING_CREATE
            })
        }
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "CREATE" && it.entityType == "TASK"
            })
        }
        coVerify(exactly = 0) { taskApi.createTask(any()) }
    }

    @Test
    fun `createTask online calls API and saves synced entity`() = runTest {
        every { connectivity.isCurrentlyOnline() } returns true
        val serverResp = taskResponse("srv-task-1", "Tratament")
        coEvery { taskApi.createTask(any()) } returns Response.success(serverResp)
        coEvery { taskDao.insert(any()) } returns Unit

        val result = repository.createTask(CreateTaskRequest("Tratament"))

        assertTrue(result is Result.Success)
        assertEquals("srv-task-1", (result as Result.Success).data.id)
        coVerify { taskApi.createTask(any()) }
        coVerify { taskDao.insert(match { it.serverId == "srv-task-1" && it.syncStatus == SyncStatus.SYNCED }) }
    }

    // ==================== READ ====================

    @Test
    fun `getAllTasks offline returns cached tasks`() = runTest {
        coEvery { taskDao.getAll() } returns listOf(taskEntity("loc-1", null, "Task"))
        every { connectivity.isCurrentlyOnline() } returns false

        val result = repository.getAllTasks()

        assertTrue(result is Result.Success)
        assertEquals(1, (result as Result.Success).data.size)
        coVerify(exactly = 0) { taskApi.getAllTasks() }
    }

    @Test
    fun `getPendingTasks offline returns pending tasks from cache`() = runTest {
        val pendingEntity = taskEntity("loc-1", null, "Task", status = TaskStatus.Pending.name)
        coEvery { taskDao.getPending() } returns listOf(pendingEntity)
        every { connectivity.isCurrentlyOnline() } returns false

        val result = repository.getPendingTasks()

        assertTrue(result is Result.Success)
        assertEquals(1, (result as Result.Success).data.size)
        coVerify(exactly = 0) { taskApi.getPendingTasks() }
    }

    @Test
    fun `getOverdueTasks offline returns overdue tasks from cache`() = runTest {
        coEvery { taskDao.getOverdue(any()) } returns listOf(taskEntity("loc-1", null, "Overdue"))
        every { connectivity.isCurrentlyOnline() } returns false

        val result = repository.getOverdueTasks()

        assertTrue(result is Result.Success)
        coVerify(exactly = 0) { taskApi.getOverdueTasks() }
    }

    @Test
    fun `getTaskById offline returns cached task`() = runTest {
        coEvery { taskDao.getByLocalId("loc-1") } returns taskEntity("loc-1", null, "Task")
        every { connectivity.isCurrentlyOnline() } returns false

        val result = repository.getTaskById("loc-1")

        assertTrue(result is Result.Success)
        coVerify(exactly = 0) { taskApi.getTaskById(any()) }
    }

    @Test
    fun `getTaskById offline not found returns error`() = runTest {
        coEvery { taskDao.getByLocalId("missing") } returns null
        every { connectivity.isCurrentlyOnline() } returns false

        val result = repository.getTaskById("missing")

        assertTrue(result is Result.Error)
    }

    // ==================== UPDATE ====================

    @Test
    fun `updateTask offline updates local entity with PENDING_UPDATE and queues sync`() = runTest {
        val existing = taskEntity("loc-1", "srv-1", "Old Task")
        coEvery { taskDao.getByLocalId("loc-1") } returns existing
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { taskDao.update(any()) } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val request = UpdateTaskRequest("New Task", priority = TaskPriority.High, status = TaskStatus.InProgress)
        val result = repository.updateTask("loc-1", request)

        assertTrue(result is Result.Success)
        coVerify {
            taskDao.update(match {
                it.title == "New Task" && it.syncStatus == SyncStatus.PENDING_UPDATE
            })
        }
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "UPDATE" && it.entityType == "TASK" && it.entityServerId == "srv-1"
            })
        }
    }

    @Test
    fun `updateTask online calls API`() = runTest {
        coEvery { taskDao.getByLocalId("loc-1") } returns taskEntity("loc-1", "srv-1", "Old")
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { taskApi.updateTask("loc-1", any()) } returns Response.success(taskResponse("srv-1", "New"))
        coEvery { taskDao.insert(any()) } returns Unit

        val result = repository.updateTask("loc-1", UpdateTaskRequest("New", priority = TaskPriority.Normal, status = TaskStatus.Pending))

        assertTrue(result is Result.Success)
        coVerify { taskApi.updateTask("loc-1", any()) }
    }

    // ==================== COMPLETE / UNCOMPLETE ====================

    @Test
    fun `completeTask offline marks Completed status and queues COMPLETE action`() = runTest {
        val existing = taskEntity("loc-1", "srv-1", "Task", status = TaskStatus.Pending.name)
        coEvery { taskDao.getByLocalId("loc-1") } returns existing
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { taskDao.update(any()) } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val result = repository.completeTask("loc-1")

        assertTrue(result is Result.Success)
        assertEquals(TaskStatus.Completed.name, (result as Result.Success).data.status.name)
        coVerify {
            taskDao.update(match {
                it.status == TaskStatus.Completed.name && it.syncStatus == SyncStatus.PENDING_UPDATE
            })
        }
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "COMPLETE" && it.entityType == "TASK" && it.entityServerId == "srv-1"
            })
        }
    }

    @Test
    fun `uncompleteTask offline marks Pending status and queues UNCOMPLETE action`() = runTest {
        val existing = taskEntity("loc-1", "srv-1", "Task", status = TaskStatus.Completed.name)
        coEvery { taskDao.getByLocalId("loc-1") } returns existing
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { taskDao.update(any()) } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val result = repository.uncompleteTask("loc-1")

        assertTrue(result is Result.Success)
        assertEquals(TaskStatus.Pending.name, (result as Result.Success).data.status.name)
        coVerify {
            taskDao.update(match { it.status == TaskStatus.Pending.name })
        }
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "UNCOMPLETE" && it.entityType == "TASK"
            })
        }
    }

    @Test
    fun `completeTask online calls API`() = runTest {
        coEvery { taskDao.getByLocalId("loc-1") } returns taskEntity("loc-1", "srv-1", "Task")
        every { connectivity.isCurrentlyOnline() } returns true
        val completedResp = taskResponse("srv-1", "Task", status = TaskStatus.Completed)
        coEvery { taskApi.completeTask("loc-1") } returns Response.success(completedResp)
        coEvery { taskDao.insert(any()) } returns Unit

        val result = repository.completeTask("loc-1")

        assertTrue(result is Result.Success)
        coVerify { taskApi.completeTask("loc-1") }
    }

    @Test
    fun `uncompleteTask online calls API`() = runTest {
        coEvery { taskDao.getByLocalId("loc-1") } returns taskEntity("loc-1", "srv-1", "Task", status = TaskStatus.Completed.name)
        every { connectivity.isCurrentlyOnline() } returns true
        val pendingResp = taskResponse("srv-1", "Task", status = TaskStatus.Pending)
        coEvery { taskApi.uncompleteTask("loc-1") } returns Response.success(pendingResp)
        coEvery { taskDao.insert(any()) } returns Unit

        val result = repository.uncompleteTask("loc-1")

        assertTrue(result is Result.Success)
        coVerify { taskApi.uncompleteTask("loc-1") }
    }

    // ==================== DELETE ====================

    @Test
    fun `deleteTask offline local-only entity deletes immediately without queuing`() = runTest {
        val localOnly = taskEntity("loc-1", null, "Task", syncStatus = SyncStatus.PENDING_CREATE)
        coEvery { taskDao.getByLocalId("loc-1") } returns localOnly
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { taskDao.deleteByLocalId("loc-1") } returns Unit
        coEvery { syncQueueDao.deleteByEntityLocalId("loc-1") } returns Unit

        val result = repository.deleteTask("loc-1")

        assertTrue(result is Result.Success)
        coVerify { taskDao.deleteByLocalId("loc-1") }
        coVerify { syncQueueDao.deleteByEntityLocalId("loc-1") }
        coVerify(exactly = 0) { syncQueueDao.insert(any()) }
    }

    @Test
    fun `deleteTask offline synced entity marks PENDING_DELETE and queues sync`() = runTest {
        val synced = taskEntity("loc-1", "srv-1", "Task", syncStatus = SyncStatus.SYNCED)
        coEvery { taskDao.getByLocalId("loc-1") } returns synced
        every { connectivity.isCurrentlyOnline() } returns false
        coEvery { taskDao.update(any()) } returns Unit
        coEvery { syncQueueDao.deleteByEntityLocalId("loc-1") } returns Unit
        coEvery { syncQueueDao.insert(any()) } returns 1L

        val result = repository.deleteTask("loc-1")

        assertTrue(result is Result.Success)
        coVerify { taskDao.update(match { it.syncStatus == SyncStatus.PENDING_DELETE }) }
        coVerify { syncQueueDao.deleteByEntityLocalId("loc-1") }
        coVerify {
            syncQueueDao.insert(match {
                it.operationType == "DELETE" && it.entityType == "TASK" && it.entityServerId == "srv-1"
            })
        }
    }

    @Test
    fun `deleteTask online calls API and removes local entity`() = runTest {
        val entity = taskEntity("loc-1", "srv-1", "Task")
        coEvery { taskDao.getByLocalId("loc-1") } returns entity
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { taskApi.deleteTask("loc-1") } returns Response.success(Unit)
        coEvery { taskDao.deleteByLocalId("loc-1") } returns Unit

        val result = repository.deleteTask("loc-1")

        assertTrue(result is Result.Success)
        coVerify { taskApi.deleteTask("loc-1") }
        coVerify { taskDao.deleteByLocalId("loc-1") }
    }

    @Test
    fun `deleteTask online API error returns Result Error`() = runTest {
        coEvery { taskDao.getByLocalId("loc-1") } returns taskEntity("loc-1", "srv-1", "Task")
        every { connectivity.isCurrentlyOnline() } returns true
        coEvery { taskApi.deleteTask("loc-1") } returns
            Response.error(404, "not found".toResponseBody("application/json".toMediaType()))

        val result = repository.deleteTask("loc-1")

        assertTrue(result is Result.Error)
    }

    // ==================== helpers ====================

    private fun taskEntity(
        localId: String,
        serverId: String?,
        title: String,
        status: String = TaskStatus.Pending.name,
        syncStatus: SyncStatus = SyncStatus.SYNCED
    ) = TaskEntity(
        localId = localId,
        serverId = serverId,
        apiaryId = null,
        apiaryName = null,
        hiveId = null,
        hiveName = null,
        title = title,
        description = null,
        priority = TaskPriority.Normal.name,
        status = status,
        dueDate = null,
        completedAt = null,
        syncStatus = syncStatus
    )

    private fun taskResponse(
        id: String,
        title: String,
        status: TaskStatus = TaskStatus.Pending
    ) = TaskResponse(
        id = id,
        userId = "user-1",
        apiaryId = null,
        apiaryName = null,
        hiveId = null,
        hiveName = null,
        title = title,
        description = null,
        priority = TaskPriority.Normal,
        status = status,
        dueDate = null,
        completedAt = null,
        createdAt = "",
        updatedAt = ""
    )
}

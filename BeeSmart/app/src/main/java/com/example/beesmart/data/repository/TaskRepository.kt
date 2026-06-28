package com.example.beesmart.data.repository

import com.example.beesmart.data.local.dao.SyncQueueDao
import com.example.beesmart.data.local.dao.TaskDao
import com.example.beesmart.data.local.entity.SyncQueueEntity
import com.example.beesmart.data.local.entity.SyncStatus
import com.example.beesmart.data.local.entity.TaskEntity
import com.example.beesmart.data.local.entity.toEntity
import com.example.beesmart.network.BackendReachability
import com.example.beesmart.network.TaskApi
import com.example.beesmart.network.models.*
import com.example.beesmart.sync.ConnectivityObserver
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepository @Inject constructor(
    private val taskApi: TaskApi,
    private val taskDao: TaskDao,
    private val syncQueueDao: SyncQueueDao,
    private val connectivity: ConnectivityObserver,
    private val backendReachability: BackendReachability,
    private val moshi: Moshi
) {
    private val createAdapter by lazy { moshi.adapter(CreateTaskRequest::class.java) }
    private val updateAdapter by lazy { moshi.adapter(UpdateTaskRequest::class.java) }

    private fun canReachBackend(): Boolean =
        connectivity.canReachBackend(backendReachability)

    suspend fun getCachedAllTasks(): List<TaskResponse> = withContext(Dispatchers.IO) {
        taskDao.getAll().map { it.toTaskResponse() }
    }

    fun observeCachedAllTasks(): Flow<List<TaskResponse>> =
        taskDao.observeAll().map { tasks -> tasks.map { it.toTaskResponse() } }

    suspend fun getCachedPendingTasks(): List<TaskResponse> = withContext(Dispatchers.IO) {
        taskDao.getPending().map { it.toTaskResponse() }
    }

    fun observeCachedPendingTasks(): Flow<List<TaskResponse>> =
        taskDao.observePending().map { tasks -> tasks.map { it.toTaskResponse() } }

    suspend fun getCachedOverdueTasks(): List<TaskResponse> = withContext(Dispatchers.IO) {
        taskDao.getOverdue(Instant.now().toString()).map { it.toTaskResponse() }
    }

    fun observeCachedOverdueTasks(): Flow<List<TaskResponse>> =
        taskDao.observeOverdue(Instant.now().toString()).map { tasks -> tasks.map { it.toTaskResponse() } }

    suspend fun getCachedTaskById(id: String): TaskResponse? = withContext(Dispatchers.IO) {
        taskDao.getByLocalId(id)?.toTaskResponse()
    }

    suspend fun getAllTasks(): Result<List<TaskResponse>> = withContext(Dispatchers.IO) {
        val cached = taskDao.getAll().map { it.toTaskResponse() }
        if (!canReachBackend()) {
            return@withContext Result.Success(cached)
        }
        return@withContext try {
            val response = taskApi.getAllTasks()
            if (response.isSuccessful && response.body() != null) {
                val serverData = response.body()!!
                taskDao.deleteAllSynced()
                taskDao.insertAll(serverData.map { it.toEntity() })
                // Merge: include PENDING_CREATE/UPDATE entries not yet on server.
                Result.Success(taskDao.getAll().map { it.toTaskResponse() })
            } else if (cached.isNotEmpty()) Result.Success(cached)
            else Result.Error(response.errorBody()?.string() ?: "Nu s-au putut încărca task-urile")
        } catch (e: Exception) {
            Result.Success(cached)
        }
    }

    suspend fun getPendingTasks(): Result<List<TaskResponse>> = withContext(Dispatchers.IO) {
        val cached = taskDao.getPending().map { it.toTaskResponse() }
        if (!canReachBackend()) {
            return@withContext Result.Success(cached)
        }
        return@withContext try {
            val response = taskApi.getPendingTasks()
            if (response.isSuccessful && response.body() != null) {
                // Insert server data, then return merged Room view (includes PENDING entries).
                taskDao.insertAll(response.body()!!.map { it.toEntity() })
                Result.Success(taskDao.getPending().map { it.toTaskResponse() })
            } else if (cached.isNotEmpty()) Result.Success(cached)
            else Result.Error(response.errorBody()?.string() ?: "Nu s-au putut încărca task-urile în așteptare")
        } catch (e: Exception) {
            Result.Success(cached)
        }
    }

    suspend fun getOverdueTasks(): Result<List<TaskResponse>> = withContext(Dispatchers.IO) {
        val now = Instant.now().toString()
        val cached = taskDao.getOverdue(now).map { it.toTaskResponse() }
        if (!canReachBackend()) {
            return@withContext Result.Success(cached)
        }
        return@withContext try {
            val response = taskApi.getOverdueTasks()
            if (response.isSuccessful && response.body() != null) {
                taskDao.insertAll(response.body()!!.map { it.toEntity() })
                // Merge: PENDING tasks created offline that are already overdue stay visible.
                Result.Success(taskDao.getOverdue(now).map { it.toTaskResponse() })
            } else if (cached.isNotEmpty()) Result.Success(cached)
            else Result.Error(response.errorBody()?.string() ?: "Nu s-au putut încărca task-urile întârziate")
        } catch (e: Exception) {
            Result.Success(cached)
        }
    }

    suspend fun getTaskById(id: String): Result<TaskResponse> = withContext(Dispatchers.IO) {
        val cached = taskDao.getByLocalId(id)
        if (!canReachBackend()) {
            return@withContext cached?.let { Result.Success(it.toTaskResponse()) }
                ?: Result.Error("Nicio conexiune la internet")
        }
        return@withContext try {
            val response = taskApi.getTaskById(id)
            if (response.isSuccessful && response.body() != null) Result.Success(response.body()!!)
            else cached?.let { Result.Success(it.toTaskResponse()) }
                ?: Result.Error(response.errorBody()?.string() ?: "Nu s-a putut încărca task-ul")
        } catch (e: Exception) {
            cached?.let { Result.Success(it.toTaskResponse()) }
                ?: Result.Error(e.message ?: "Eroare de rețea")
        }
    }

    private suspend fun queueCreateOffline(request: CreateTaskRequest): TaskResponse {
        val localId = UUID.randomUUID().toString()
        val entity = TaskEntity(
            localId = localId,
            serverId = null,
            apiaryId = request.apiaryId,
            apiaryName = null,
            hiveId = request.hiveId,
            hiveName = null,
            title = request.title,
            description = request.description,
            priority = request.priority.name,
            status = TaskStatus.Pending.name,
            dueDate = request.dueDate,
            completedAt = null,
            syncStatus = SyncStatus.PENDING_CREATE
        )
        taskDao.insert(entity)
        syncQueueDao.insert(
            SyncQueueEntity(
                operationType = "CREATE",
                entityType = "TASK",
                entityLocalId = localId,
                entityServerId = null,
                payload = createAdapter.toJson(request)
            )
        )
        return entity.toTaskResponse()
    }

    suspend fun enqueueCreateTask(request: CreateTaskRequest): Result<TaskResponse> =
        withContext(Dispatchers.IO) {
            Result.Success(queueCreateOffline(request))
        }

    suspend fun createTask(request: CreateTaskRequest): Result<TaskResponse> = withContext(Dispatchers.IO) {
        if (!canReachBackend()) {
            return@withContext Result.Success(queueCreateOffline(request))
        }
        return@withContext try {
            val response = taskApi.createTask(request)
            if (response.isSuccessful && response.body() != null) {
                val server = response.body()!!
                taskDao.insert(server.toEntity())
                Result.Success(server)
            } else {
                Result.Error(response.errorBody()?.string() ?: "Nu s-a putut crea task-ul")
            }
        } catch (e: IOException) {
            Result.Success(queueCreateOffline(request))
        } catch (e: Exception) {
            Result.Error(e.message ?: "Eroare de rețea")
        }
    }

    private suspend fun queueUpdateOffline(entity: TaskEntity, request: UpdateTaskRequest): TaskResponse {
        val updated = entity.copy(
            title = request.title,
            description = request.description,
            priority = request.priority.name,
            status = request.status.name,
            dueDate = request.dueDate,
            syncStatus = SyncStatus.PENDING_UPDATE
        )
        taskDao.update(updated)
        syncQueueDao.insert(
            SyncQueueEntity(
                operationType = "UPDATE",
                entityType = "TASK",
                entityLocalId = entity.localId,
                entityServerId = entity.serverId,
                payload = updateAdapter.toJson(request)
            )
        )
        return updated.toTaskResponse()
    }

    suspend fun enqueueUpdateTask(id: String, request: UpdateTaskRequest): Result<TaskResponse> =
        withContext(Dispatchers.IO) {
            val entity = taskDao.getByLocalId(id) ?: taskDao.getByServerId(id)
            entity?.let { Result.Success(queueUpdateOffline(it, request)) }
                ?: Result.Error("Sarcina negasita local")
        }

    suspend fun updateTask(id: String, request: UpdateTaskRequest): Result<TaskResponse> = withContext(Dispatchers.IO) {
        val entity = taskDao.getByLocalId(id) ?: taskDao.getByServerId(id)
        if (!canReachBackend()) {
            return@withContext entity?.let { Result.Success(queueUpdateOffline(it, request)) }
                ?: Result.Error("Nicio conexiune la internet")
        }
        return@withContext try {
            val response = taskApi.updateTask(id, request)
            if (response.isSuccessful && response.body() != null) {
                val server = response.body()!!
                taskDao.insert(server.toEntity())
                Result.Success(server)
            } else {
                Result.Error(response.errorBody()?.string() ?: "Nu s-a putut actualiza task-ul")
            }
        } catch (e: IOException) {
            entity?.let { Result.Success(queueUpdateOffline(it, request)) }
                ?: Result.Error("Sarcină negăsită local", exception = e)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Eroare de rețea")
        }
    }

    private suspend fun queueCompleteOffline(entity: TaskEntity): TaskResponse {
        val updated = entity.copy(status = TaskStatus.Completed.name, syncStatus = SyncStatus.PENDING_UPDATE)
        taskDao.update(updated)
        syncQueueDao.insert(
            SyncQueueEntity(
                operationType = "COMPLETE",
                entityType = "TASK",
                entityLocalId = entity.localId,
                entityServerId = entity.serverId,
                payload = ""
            )
        )
        return updated.toTaskResponse()
    }

    suspend fun enqueueCompleteTask(id: String): Result<TaskResponse> = withContext(Dispatchers.IO) {
        val entity = taskDao.getByLocalId(id) ?: taskDao.getByServerId(id)
        entity?.let { Result.Success(queueCompleteOffline(it)) }
            ?: Result.Error("Sarcina negasita local")
    }

    suspend fun completeTask(id: String): Result<TaskResponse> = withContext(Dispatchers.IO) {
        val entity = taskDao.getByLocalId(id) ?: taskDao.getByServerId(id)
        if (!canReachBackend()) {
            return@withContext entity?.let { Result.Success(queueCompleteOffline(it)) }
                ?: Result.Error("Nicio conexiune la internet")
        }
        return@withContext try {
            val response = taskApi.completeTask(id)
            if (response.isSuccessful && response.body() != null) {
                val server = response.body()!!
                taskDao.insert(server.toEntity())
                Result.Success(server)
            } else {
                Result.Error(response.errorBody()?.string() ?: "Nu s-a putut finaliza task-ul")
            }
        } catch (e: IOException) {
            entity?.let { Result.Success(queueCompleteOffline(it)) }
                ?: Result.Error("Sarcină negăsită local", exception = e)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Eroare de rețea")
        }
    }

    private suspend fun queueUncompleteOffline(entity: TaskEntity): TaskResponse {
        val updated = entity.copy(status = TaskStatus.Pending.name, syncStatus = SyncStatus.PENDING_UPDATE)
        taskDao.update(updated)
        syncQueueDao.insert(
            SyncQueueEntity(
                operationType = "UNCOMPLETE",
                entityType = "TASK",
                entityLocalId = entity.localId,
                entityServerId = entity.serverId,
                payload = ""
            )
        )
        return updated.toTaskResponse()
    }

    suspend fun enqueueUncompleteTask(id: String): Result<TaskResponse> = withContext(Dispatchers.IO) {
        val entity = taskDao.getByLocalId(id) ?: taskDao.getByServerId(id)
        entity?.let { Result.Success(queueUncompleteOffline(it)) }
            ?: Result.Error("Sarcina negasita local")
    }

    suspend fun uncompleteTask(id: String): Result<TaskResponse> = withContext(Dispatchers.IO) {
        val entity = taskDao.getByLocalId(id) ?: taskDao.getByServerId(id)
        if (!canReachBackend()) {
            return@withContext entity?.let { Result.Success(queueUncompleteOffline(it)) }
                ?: Result.Error("Nicio conexiune la internet")
        }
        return@withContext try {
            val response = taskApi.uncompleteTask(id)
            if (response.isSuccessful && response.body() != null) {
                val server = response.body()!!
                taskDao.insert(server.toEntity())
                Result.Success(server)
            } else {
                Result.Error(response.errorBody()?.string() ?: "Nu s-a putut redeschide task-ul")
            }
        } catch (e: IOException) {
            entity?.let { Result.Success(queueUncompleteOffline(it)) }
                ?: Result.Error("Sarcină negăsită local", exception = e)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Eroare de rețea")
        }
    }

    private suspend fun queueDeleteOffline(entity: TaskEntity) {
        if (entity.serverId == null) {
            taskDao.deleteByLocalId(entity.localId)
            syncQueueDao.deleteByEntityLocalId(entity.localId)
        } else {
            taskDao.update(entity.copy(syncStatus = SyncStatus.PENDING_DELETE))
            syncQueueDao.deleteByEntityLocalId(entity.localId)
            syncQueueDao.insert(
                SyncQueueEntity(
                    operationType = "DELETE",
                    entityType = "TASK",
                    entityLocalId = entity.localId,
                    entityServerId = entity.serverId,
                    payload = ""
                )
            )
        }
    }

    suspend fun enqueueDeleteTask(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val entity = taskDao.getByLocalId(id) ?: taskDao.getByServerId(id)
        entity?.let {
            queueDeleteOffline(it)
            Result.Success(Unit)
        } ?: Result.Error("Sarcina negasita local")
    }

    suspend fun deleteTask(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val entity = taskDao.getByLocalId(id) ?: taskDao.getByServerId(id)
        if (!canReachBackend()) {
            return@withContext entity?.let {
                queueDeleteOffline(it)
                Result.Success(Unit)
            } ?: Result.Error("Nicio conexiune la internet")
        }
        return@withContext try {
            val response = taskApi.deleteTask(id)
            if (response.isSuccessful) {
                entity?.let { taskDao.deleteByLocalId(it.localId) }
                Result.Success(Unit)
            } else {
                Result.Error(response.errorBody()?.string() ?: "Nu s-a putut șterge task-ul")
            }
        } catch (e: IOException) {
            entity?.let {
                queueDeleteOffline(it)
                Result.Success(Unit)
            } ?: Result.Error("Sarcină negăsită local", exception = e)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Eroare de rețea")
        }
    }
}

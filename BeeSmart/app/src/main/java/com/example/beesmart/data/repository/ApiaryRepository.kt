package com.example.beesmart.data.repository

import com.example.beesmart.data.local.dao.ApiaryDao
import com.example.beesmart.data.local.dao.SyncQueueDao
import com.example.beesmart.data.local.entity.ApiaryEntity
import com.example.beesmart.data.local.entity.SyncQueueEntity
import com.example.beesmart.data.local.entity.SyncStatus
import com.example.beesmart.data.local.entity.toEntity
import com.example.beesmart.network.ApiaryApi
import com.example.beesmart.network.BackendReachability
import com.example.beesmart.network.models.ApiaryDetailResponse
import com.example.beesmart.network.models.ApiaryResponse
import com.example.beesmart.network.models.CreateApiaryRequest
import com.example.beesmart.network.models.UpdateApiaryRequest
import com.example.beesmart.sync.ConnectivityObserver
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiaryRepository @Inject constructor(
    private val apiaryApi: ApiaryApi,
    private val apiaryDao: ApiaryDao,
    private val syncQueueDao: SyncQueueDao,
    private val connectivity: ConnectivityObserver,
    private val backendReachability: BackendReachability,
    private val moshi: Moshi
) {
    private val createAdapter by lazy { moshi.adapter(CreateApiaryRequest::class.java) }
    private val updateAdapter by lazy { moshi.adapter(UpdateApiaryRequest::class.java) }

    private fun canReachBackend(): Boolean =
        connectivity.canReachBackend(backendReachability)

    suspend fun getCachedApiaries(): List<ApiaryResponse> = withContext(Dispatchers.IO) {
        apiaryDao.getAll().map { it.toApiaryResponse() }
    }

    fun observeCachedApiaries(): Flow<List<ApiaryResponse>> =
        apiaryDao.observeAll().map { apiaries -> apiaries.map { it.toApiaryResponse() } }

    suspend fun getCachedApiaryById(id: String): ApiaryDetailResponse? = withContext(Dispatchers.IO) {
        val entity = apiaryDao.getByLocalId(id) ?: apiaryDao.getByServerId(id)
        entity?.let {
            ApiaryDetailResponse(
                id = it.serverId ?: it.localId,
                userId = "",
                name = it.name,
                description = it.description,
                location = it.location,
                hives = emptyList(),
                createdAt = "",
                updatedAt = it.updatedAt.toString()
            )
        }
    }

    suspend fun getAllApiaries(): Result<List<ApiaryResponse>> = withContext(Dispatchers.IO) {
        val cached = apiaryDao.getAll().map { it.toApiaryResponse() }
        if (!canReachBackend()) {
            return@withContext Result.Success(cached)
        }
        return@withContext try {
            val response = apiaryApi.getAllApiaries()
            if (response.isSuccessful && response.body() != null) {
                val serverData = response.body()!!
                apiaryDao.deleteAllSynced()
                apiaryDao.insertAll(serverData.map { it.toEntity() })
                // Return the merged Room view so locally-created PENDING_CREATE
                // entries remain visible until the sync queue drains.
                Result.Success(apiaryDao.getAll().map { it.toApiaryResponse() })
            } else if (cached.isNotEmpty()) {
                Result.Success(cached)
            } else {
                Result.Error(response.errorBody()?.string() ?: "Nu s-au putut încărca stupinele")
            }
        } catch (e: Exception) {
            if (cached.isNotEmpty()) {
                Result.Success(cached)
            } else {
                Result.Error(e.message ?: "Nu s-au putut incarca stupinele", exception = e)
            }
        }
    }

    /**
     * Read-only lookup of the apiary's text location, served straight from Room
     * so it works offline. Returns null if the apiary isn't cached or has no location.
     */
    suspend fun getCachedApiaryLocation(id: String): String? = withContext(Dispatchers.IO) {
        val entity = apiaryDao.getByLocalId(id) ?: apiaryDao.getByServerId(id)
        entity?.location?.takeIf { it.isNotBlank() }
    }

    suspend fun getApiaryById(id: String): Result<ApiaryDetailResponse> = withContext(Dispatchers.IO) {
        // Try network first when online
        if (canReachBackend()) {
            return@withContext try {
                val response = apiaryApi.getApiaryById(id)
                if (response.isSuccessful && response.body() != null) Result.Success(response.body()!!)
                else Result.Error(response.errorBody()?.string() ?: "Nu s-a putut încărca stupina")
            } catch (e: Exception) {
                // Network failed — fall through to cache below
                null
            } ?: loadApiaryFromCache(id)
        }
        // Offline path: load from Room
        loadApiaryFromCache(id)
    }

    private suspend fun loadApiaryFromCache(id: String): Result<ApiaryDetailResponse> {
        val entity = apiaryDao.getByServerId(id) ?: apiaryDao.getByLocalId(id)
            ?: return Result.Error("Stupina nu a fost găsită în cache local")
        return Result.Success(
            ApiaryDetailResponse(
                id = entity.serverId ?: entity.localId,
                userId = "",
                name = entity.name,
                description = entity.description,
                location = entity.location,
                hives = emptyList(),
                createdAt = "",
                updatedAt = entity.updatedAt.toString()
            )
        )
    }

    private suspend fun queueCreateOffline(request: CreateApiaryRequest): ApiaryResponse {
        val localId = UUID.randomUUID().toString()
        val entity = ApiaryEntity(
            localId = localId,
            serverId = null,
            name = request.name,
            description = request.description,
            location = request.location,
            syncStatus = SyncStatus.PENDING_CREATE
        )
        apiaryDao.insert(entity)
        syncQueueDao.insert(
            SyncQueueEntity(
                operationType = "CREATE",
                entityType = "APIARY",
                entityLocalId = localId,
                entityServerId = null,
                payload = createAdapter.toJson(request)
            )
        )
        return entity.toApiaryResponse()
    }

    suspend fun enqueueCreateApiary(request: CreateApiaryRequest): Result<ApiaryResponse> =
        withContext(Dispatchers.IO) {
            Result.Success(queueCreateOffline(request))
        }

    suspend fun createApiary(request: CreateApiaryRequest): Result<ApiaryResponse> = withContext(Dispatchers.IO) {
        if (!canReachBackend()) {
            return@withContext Result.Success(queueCreateOffline(request))
        }
        return@withContext try {
            val response = apiaryApi.createApiary(request)
            if (response.isSuccessful && response.body() != null) {
                val server = response.body()!!
                apiaryDao.insert(server.toEntity())
                Result.Success(server)
            } else {
                Result.Error(response.errorBody()?.string() ?: "Nu s-a putut crea stupina")
            }
        } catch (e: IOException) {
            Result.Success(queueCreateOffline(request))
        } catch (e: Exception) {
            Result.Error(e.message ?: "Eroare de rețea")
        }
    }

    private suspend fun queueUpdateOffline(entity: ApiaryEntity, request: UpdateApiaryRequest): ApiaryResponse {
        val updated = entity.copy(
            name = request.name,
            description = request.description,
            location = request.location,
            syncStatus = SyncStatus.PENDING_UPDATE
        )
        apiaryDao.update(updated)
        syncQueueDao.insert(
            SyncQueueEntity(
                operationType = "UPDATE",
                entityType = "APIARY",
                entityLocalId = entity.localId,
                entityServerId = entity.serverId,
                payload = updateAdapter.toJson(request)
            )
        )
        return updated.toApiaryResponse()
    }

    suspend fun enqueueUpdateApiary(id: String, request: UpdateApiaryRequest): Result<ApiaryResponse> =
        withContext(Dispatchers.IO) {
            val entity = apiaryDao.getByLocalId(id) ?: apiaryDao.getByServerId(id)
            entity?.let { Result.Success(queueUpdateOffline(it, request)) }
                ?: Result.Error("Stupina negasita local")
        }

    suspend fun updateApiary(id: String, request: UpdateApiaryRequest): Result<ApiaryResponse> = withContext(Dispatchers.IO) {
        val entity = apiaryDao.getByLocalId(id) ?: apiaryDao.getByServerId(id)
        if (!canReachBackend()) {
            return@withContext entity?.let { Result.Success(queueUpdateOffline(it, request)) }
                ?: Result.Error("Nicio conexiune la internet")
        }
        return@withContext try {
            val response = apiaryApi.updateApiary(id, request)
            if (response.isSuccessful && response.body() != null) {
                val server = response.body()!!
                apiaryDao.insert(server.toEntity())
                Result.Success(server)
            } else {
                Result.Error(response.errorBody()?.string() ?: "Nu s-a putut actualiza stupina")
            }
        } catch (e: IOException) {
            entity?.let { Result.Success(queueUpdateOffline(it, request)) }
                ?: Result.Error("Stupină negăsită local", exception = e)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Eroare de rețea")
        }
    }

    private suspend fun queueDeleteOffline(entity: ApiaryEntity) {
        if (entity.serverId == null) {
            apiaryDao.deleteByLocalId(entity.localId)
            syncQueueDao.deleteByEntityLocalId(entity.localId)
        } else {
            apiaryDao.update(entity.copy(syncStatus = SyncStatus.PENDING_DELETE))
            syncQueueDao.deleteByEntityLocalId(entity.localId)
            syncQueueDao.insert(
                SyncQueueEntity(
                    operationType = "DELETE",
                    entityType = "APIARY",
                    entityLocalId = entity.localId,
                    entityServerId = entity.serverId,
                    payload = ""
                )
            )
        }
    }

    suspend fun enqueueDeleteApiary(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val entity = apiaryDao.getByLocalId(id) ?: apiaryDao.getByServerId(id)
        entity?.let {
            queueDeleteOffline(it)
            Result.Success(Unit)
        } ?: Result.Error("Stupina negasita local")
    }

    suspend fun deleteApiary(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val entity = apiaryDao.getByLocalId(id) ?: apiaryDao.getByServerId(id)
        if (!canReachBackend()) {
            return@withContext entity?.let {
                queueDeleteOffline(it)
                Result.Success(Unit)
            } ?: Result.Error("Nicio conexiune la internet")
        }
        return@withContext try {
            val response = apiaryApi.deleteApiary(id)
            if (response.isSuccessful) {
                entity?.let { apiaryDao.deleteByLocalId(it.localId) }
                Result.Success(Unit)
            } else {
                Result.Error(response.errorBody()?.string() ?: "Nu s-a putut șterge stupina")
            }
        } catch (e: IOException) {
            entity?.let {
                queueDeleteOffline(it)
                Result.Success(Unit)
            } ?: Result.Error("Stupină negăsită local", exception = e)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Eroare de rețea")
        }
    }
}

package com.example.beesmart.data.repository

import com.example.beesmart.data.local.dao.HiveDao
import com.example.beesmart.data.local.dao.SyncQueueDao
import com.example.beesmart.data.local.entity.HiveEntity
import com.example.beesmart.data.local.entity.SyncQueueEntity
import com.example.beesmart.data.local.entity.SyncStatus
import com.example.beesmart.data.local.entity.toEntity
import com.example.beesmart.network.BackendReachability
import com.example.beesmart.network.HiveApi
import com.example.beesmart.network.models.CreateHiveRequest
import com.example.beesmart.network.models.HiveResponse
import com.example.beesmart.network.models.UpdateHiveRequest
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
class HiveRepository @Inject constructor(
    private val hiveApi: HiveApi,
    private val hiveDao: HiveDao,
    private val syncQueueDao: SyncQueueDao,
    private val connectivity: ConnectivityObserver,
    private val backendReachability: BackendReachability,
    private val moshi: Moshi
) {
    private val createAdapter by lazy { moshi.adapter(CreateHiveRequest::class.java) }
    private val updateAdapter by lazy { moshi.adapter(UpdateHiveRequest::class.java) }

    private fun canReachBackend(): Boolean =
        connectivity.canReachBackend(backendReachability)

    suspend fun getCachedAllHives(): List<HiveResponse> = withContext(Dispatchers.IO) {
        hiveDao.getAll().map { it.toHiveResponse() }
    }

    suspend fun getCachedHivesByApiaryId(apiaryId: String): List<HiveResponse> = withContext(Dispatchers.IO) {
        hiveDao.getByApiaryId(apiaryId).map { it.toHiveResponse() }
    }

    fun observeCachedHivesByApiaryId(apiaryId: String): Flow<List<HiveResponse>> =
        hiveDao.observeByApiaryId(apiaryId).map { hives ->
            hives.map { it.toHiveResponse() }
        }

    suspend fun getCachedHiveById(id: String): HiveResponse? = withContext(Dispatchers.IO) {
        (hiveDao.getByLocalId(id) ?: hiveDao.getByServerId(id))?.toHiveResponse()
    }

    private fun List<HiveResponse>.toEntitiesPreservingLocalAliases(
        cached: List<HiveEntity>
    ): List<HiveEntity> {
        val syncedByServerId = cached
            .filter { it.syncStatus == SyncStatus.SYNCED && it.serverId != null }
            .associateBy { it.serverId!! }

        return map { serverHive ->
            val existing = syncedByServerId[serverHive.id]
            serverHive.toEntity().let { serverEntity ->
                if (existing == null) {
                    serverEntity
                } else {
                    serverEntity.copy(
                        localId = existing.localId,
                        apiaryLocalId = existing.apiaryLocalId
                    )
                }
            }
        }
    }

    suspend fun getAllHives(): Result<List<HiveResponse>> = withContext(Dispatchers.IO) {
        val cachedEntities = hiveDao.getAll()
        val cached = cachedEntities.map { it.toHiveResponse() }
        if (!canReachBackend()) {
            return@withContext Result.Success(cached)
        }
        return@withContext try {
            val response = hiveApi.getAllHives()
            if (response.isSuccessful && response.body() != null) {
                val serverData = response.body()!!
                hiveDao.deleteAllSynced()
                hiveDao.insertAll(serverData.toEntitiesPreservingLocalAliases(cachedEntities))
                // Return the merged Room view so locally-created PENDING_CREATE
                // entries remain visible until the sync queue drains.
                Result.Success(hiveDao.getAll().map { it.toHiveResponse() })
            } else if (cached.isNotEmpty()) {
                Result.Success(cached)
            } else {
                Result.Error(response.errorBody()?.string() ?: "Nu s-au putut încărca stupii", response.code())
            }
        } catch (e: Exception) {
            Result.Success(cached)
        }
    }

    suspend fun getHivesByApiaryId(apiaryId: String): Result<List<HiveResponse>> = withContext(Dispatchers.IO) {
        val cachedEntities = hiveDao.getByApiaryId(apiaryId)
        val cached = cachedEntities.map { it.toHiveResponse() }
        if (!canReachBackend()) {
            return@withContext Result.Success(cached)
        }
        return@withContext try {
            val response = hiveApi.getHivesByApiaryId(apiaryId)
            if (response.isSuccessful && response.body() != null) {
                val serverData = response.body()!!
                hiveDao.deleteSyncedByApiaryId(apiaryId)
                hiveDao.insertAll(serverData.toEntitiesPreservingLocalAliases(cachedEntities))
                // Merge: include PENDING_CREATE entries the server doesn't know about yet.
                Result.Success(hiveDao.getByApiaryId(apiaryId).map { it.toHiveResponse() })
            } else if (cached.isNotEmpty()) {
                Result.Success(cached)
            } else {
                Result.Error(response.errorBody()?.string() ?: "Nu s-au putut încărca stupii", response.code())
            }
        } catch (e: Exception) {
            Result.Success(cached)
        }
    }

    suspend fun getHiveById(id: String): Result<HiveResponse> = withContext(Dispatchers.IO) {
        val cached = hiveDao.getByLocalId(id) ?: hiveDao.getByServerId(id)
        if (!canReachBackend()) {
            return@withContext cached?.let { Result.Success(it.toHiveResponse()) }
                ?: Result.Error("Nicio conexiune la internet")
        }
        return@withContext try {
            val response = hiveApi.getHiveById(id)
            if (response.isSuccessful && response.body() != null) Result.Success(response.body()!!)
            else cached?.let { Result.Success(it.toHiveResponse()) }
                ?: Result.Error(response.errorBody()?.string() ?: "Nu s-a putut încărca stupul", response.code())
        } catch (e: Exception) {
            cached?.let { Result.Success(it.toHiveResponse()) }
                ?: Result.Error("Eroare de rețea: ${e.message}", null, e)
        }
    }

    private suspend fun queueCreateOffline(apiaryId: String, request: CreateHiveRequest): HiveResponse {
        val localId = UUID.randomUUID().toString()
        val entity = HiveEntity(
            localId = localId,
            serverId = null,
            apiaryLocalId = apiaryId,
            apiaryServerId = null,
            apiaryName = "",
            name = request.name,
            type = request.type.name,
            status = request.status.name,
            notes = request.notes,
            reginaPrezenta = request.reginaPrezenta,
            varstaRegina = request.varstaRegina,
            rameAlbine = request.rameAlbine,
            ramePuiet = request.ramePuiet,
            rameMiere = request.rameMiere,
            syncStatus = SyncStatus.PENDING_CREATE
        )
        hiveDao.insert(entity)
        syncQueueDao.insert(
            SyncQueueEntity(
                operationType = "CREATE",
                entityType = "HIVE",
                entityLocalId = localId,
                entityServerId = null,
                payload = createAdapter.toJson(request)
            )
        )
        return entity.toHiveResponse()
    }

    suspend fun enqueueCreateHive(apiaryId: String, request: CreateHiveRequest): Result<HiveResponse> =
        withContext(Dispatchers.IO) {
            Result.Success(queueCreateOffline(apiaryId, request))
        }

    suspend fun createHive(apiaryId: String, request: CreateHiveRequest): Result<HiveResponse> = withContext(Dispatchers.IO) {
        if (!canReachBackend()) {
            return@withContext Result.Success(queueCreateOffline(apiaryId, request))
        }
        return@withContext try {
            val response = hiveApi.createHive(apiaryId, request)
            if (response.isSuccessful && response.body() != null) {
                val server = response.body()!!
                hiveDao.insert(server.toEntity())
                Result.Success(server)
            } else {
                Result.Error(response.errorBody()?.string() ?: "Nu s-a putut crea stupul", response.code())
            }
        } catch (e: IOException) {
            Result.Success(queueCreateOffline(apiaryId, request))
        } catch (e: Exception) {
            Result.Error("Eroare de rețea: ${e.message}", null, e)
        }
    }

    private suspend fun queueUpdateOffline(entity: HiveEntity, request: UpdateHiveRequest): HiveResponse {
        val updated = entity.copy(
            name = request.name,
            type = request.type.name,
            status = request.status.name,
            notes = request.notes,
            reginaPrezenta = request.reginaPrezenta,
            varstaRegina = request.varstaRegina,
            rameAlbine = request.rameAlbine,
            ramePuiet = request.ramePuiet,
            rameMiere = request.rameMiere,
            syncStatus = SyncStatus.PENDING_UPDATE
        )
        hiveDao.update(updated)
        syncQueueDao.insert(
            SyncQueueEntity(
                operationType = "UPDATE",
                entityType = "HIVE",
                entityLocalId = entity.localId,
                entityServerId = entity.serverId,
                payload = updateAdapter.toJson(request)
            )
        )
        return updated.toHiveResponse()
    }

    suspend fun enqueueUpdateHive(id: String, request: UpdateHiveRequest): Result<HiveResponse> =
        withContext(Dispatchers.IO) {
            val entity = hiveDao.getByLocalId(id) ?: hiveDao.getByServerId(id)
            entity?.let { Result.Success(queueUpdateOffline(it, request)) }
                ?: Result.Error("Stup negasit local")
        }

    suspend fun updateHive(id: String, request: UpdateHiveRequest): Result<HiveResponse> = withContext(Dispatchers.IO) {
        val entity = hiveDao.getByLocalId(id) ?: hiveDao.getByServerId(id)
        if (!canReachBackend()) {
            return@withContext entity?.let { Result.Success(queueUpdateOffline(it, request)) }
                ?: Result.Error("Nicio conexiune la internet")
        }
        return@withContext try {
            val response = hiveApi.updateHive(id, request)
            if (response.isSuccessful && response.body() != null) {
                val server = response.body()!!
                hiveDao.insert(server.toEntity())
                Result.Success(server)
            } else {
                Result.Error(response.errorBody()?.string() ?: "Nu s-a putut actualiza stupul", response.code())
            }
        } catch (e: IOException) {
            entity?.let { Result.Success(queueUpdateOffline(it, request)) }
                ?: Result.Error("Stup negăsit local", exception = e)
        } catch (e: Exception) {
            Result.Error("Eroare de rețea: ${e.message}", null, e)
        }
    }

    private suspend fun queueDeleteOffline(entity: HiveEntity) {
        if (entity.serverId == null) {
            hiveDao.deleteByLocalId(entity.localId)
            syncQueueDao.deleteByEntityLocalId(entity.localId)
        } else {
            hiveDao.update(entity.copy(syncStatus = SyncStatus.PENDING_DELETE))
            syncQueueDao.deleteByEntityLocalId(entity.localId)
            syncQueueDao.insert(
                SyncQueueEntity(
                    operationType = "DELETE",
                    entityType = "HIVE",
                    entityLocalId = entity.localId,
                    entityServerId = entity.serverId,
                    payload = ""
                )
            )
        }
    }

    suspend fun enqueueDeleteHive(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val entity = hiveDao.getByLocalId(id) ?: hiveDao.getByServerId(id)
        entity?.let {
            queueDeleteOffline(it)
            Result.Success(Unit)
        } ?: Result.Error("Stup negasit local")
    }

    suspend fun deleteHive(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val entity = hiveDao.getByLocalId(id) ?: hiveDao.getByServerId(id)
        if (!canReachBackend()) {
            return@withContext entity?.let {
                queueDeleteOffline(it)
                Result.Success(Unit)
            } ?: Result.Error("Nicio conexiune la internet")
        }
        return@withContext try {
            val response = hiveApi.deleteHive(id)
            if (response.isSuccessful) {
                entity?.let { hiveDao.deleteByLocalId(it.localId) }
                Result.Success(Unit)
            } else {
                Result.Error(response.errorBody()?.string() ?: "Nu s-a putut șterge stupul", response.code())
            }
        } catch (e: IOException) {
            entity?.let {
                queueDeleteOffline(it)
                Result.Success(Unit)
            } ?: Result.Error("Stup negăsit local", exception = e)
        } catch (e: Exception) {
            Result.Error("Eroare de rețea: ${e.message}", null, e)
        }
    }
}

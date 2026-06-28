package com.example.beesmart.data.repository

import com.example.beesmart.data.local.dao.ExtractionDao
import com.example.beesmart.data.local.dao.SyncQueueDao
import com.example.beesmart.data.local.entity.ExtractionEntity
import com.example.beesmart.data.local.entity.SyncQueueEntity
import com.example.beesmart.data.local.entity.SyncStatus
import com.example.beesmart.data.local.entity.toEntity
import com.example.beesmart.network.BackendReachability
import com.example.beesmart.network.ExtractionApi
import com.example.beesmart.network.models.CreateExtractionRequest
import com.example.beesmart.network.models.HiveExtraction
import com.example.beesmart.network.models.UpdateExtractionRequest
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
class ExtractionRepository @Inject constructor(
    private val extractionApi: ExtractionApi,
    private val extractionDao: ExtractionDao,
    private val syncQueueDao: SyncQueueDao,
    private val connectivity: ConnectivityObserver,
    private val backendReachability: BackendReachability,
    private val moshi: Moshi
) {
    private val createAdapter by lazy { moshi.adapter(CreateExtractionRequest::class.java) }
    private val updateAdapter by lazy { moshi.adapter(UpdateExtractionRequest::class.java) }

    private fun canReachBackend(): Boolean =
        connectivity.canReachBackend(backendReachability)

    suspend fun getCachedAllExtractions(): List<HiveExtraction> = withContext(Dispatchers.IO) {
        extractionDao.getAll().map { it.toHiveExtraction() }
    }

    fun observeCachedAllExtractions(): Flow<List<HiveExtraction>> =
        extractionDao.observeAll().map { extractions -> extractions.map { it.toHiveExtraction() } }

    suspend fun getCachedExtractionsByHiveId(hiveId: String): List<HiveExtraction> = withContext(Dispatchers.IO) {
        extractionDao.getByHiveId(hiveId).map { it.toHiveExtraction() }
    }

    fun observeCachedExtractionsByHiveId(hiveId: String): Flow<List<HiveExtraction>> =
        extractionDao.observeByHiveId(hiveId).map { extractions -> extractions.map { it.toHiveExtraction() } }

    suspend fun getCachedExtractionById(id: String): HiveExtraction? = withContext(Dispatchers.IO) {
        extractionDao.getByLocalId(id)?.toHiveExtraction()
    }

    suspend fun getAllExtractions(): Result<List<HiveExtraction>> = withContext(Dispatchers.IO) {
        val cached = extractionDao.getAll().map { it.toHiveExtraction() }
        if (!canReachBackend()) return@withContext Result.Success(cached)
        return@withContext try {
            val response = extractionApi.getAllExtractions()
            if (response.isSuccessful && response.body() != null) {
                val serverData = response.body()!!
                extractionDao.deleteAllSynced()
                extractionDao.insertAll(serverData.map { it.toEntity() })
                Result.Success(extractionDao.getAll().map { it.toHiveExtraction() })
            } else if (cached.isNotEmpty()) Result.Success(cached)
            else Result.Error(response.errorBody()?.string() ?: "Nu s-au putut încărca extracțiile", response.code())
        } catch (e: Exception) { Result.Success(cached) }
    }

    suspend fun getExtractionsByHiveId(hiveId: String): Result<List<HiveExtraction>> = withContext(Dispatchers.IO) {
        val cached = extractionDao.getByHiveId(hiveId).map { it.toHiveExtraction() }
        if (!canReachBackend()) {
            return@withContext Result.Success(cached)
        }
        return@withContext try {
            val response = extractionApi.getExtractionsByHiveId(hiveId)
            if (response.isSuccessful && response.body() != null) {
                val serverData = response.body()!!
                extractionDao.deleteSyncedByHiveId(hiveId)
                extractionDao.insertAll(serverData.map { it.toEntity() })
                // Merge: include PENDING_CREATE entries that haven't synced yet.
                Result.Success(extractionDao.getByHiveId(hiveId).map { it.toHiveExtraction() })
            } else if (cached.isNotEmpty()) Result.Success(cached)
            else Result.Error(response.errorBody()?.string() ?: "Nu s-au putut încărca extracțiile", response.code())
        } catch (e: Exception) {
            Result.Success(cached)
        }
    }

    private suspend fun queueCreateOffline(request: CreateExtractionRequest): HiveExtraction {
        val localId = UUID.randomUUID().toString()
        val entity = ExtractionEntity(
            localId = localId,
            serverId = null,
            hiveLocalId = request.hiveId,
            hiveServerId = null,
            apiaryId = "",
            extractionDate = request.extractionDate,
            type = request.type.name,
            quantity = request.quantity,
            unit = request.unit,
            notes = request.notes,
            syncStatus = SyncStatus.PENDING_CREATE
        )
        extractionDao.insert(entity)
        syncQueueDao.insert(
            SyncQueueEntity(
                operationType = "CREATE",
                entityType = "EXTRACTION",
                entityLocalId = localId,
                entityServerId = null,
                payload = createAdapter.toJson(request)
            )
        )
        return entity.toHiveExtraction()
    }

    suspend fun enqueueCreateExtraction(request: CreateExtractionRequest): Result<HiveExtraction> =
        withContext(Dispatchers.IO) {
            Result.Success(queueCreateOffline(request))
        }

    suspend fun createExtraction(request: CreateExtractionRequest): Result<HiveExtraction> = withContext(Dispatchers.IO) {
        if (!canReachBackend()) {
            return@withContext Result.Success(queueCreateOffline(request))
        }
        return@withContext try {
            val response = extractionApi.createExtraction(request)
            if (response.isSuccessful && response.body() != null) {
                val server = response.body()!!
                extractionDao.insert(server.toEntity())
                Result.Success(server)
            } else {
                Result.Error(response.errorBody()?.string() ?: "Nu s-a putut crea extracția", response.code())
            }
        } catch (e: IOException) {
            Result.Success(queueCreateOffline(request))
        } catch (e: Exception) {
            Result.Error(e.message ?: "Eroare de rețea", exception = e)
        }
    }

    suspend fun getExtractionById(extractionId: String): Result<HiveExtraction> = withContext(Dispatchers.IO) {
        val cached = extractionDao.getByLocalId(extractionId)
        if (!canReachBackend()) {
            return@withContext cached?.let { Result.Success(it.toHiveExtraction()) }
                ?: Result.Error("Nicio conexiune la internet")
        }
        return@withContext try {
            val response = extractionApi.getExtractionById(extractionId)
            if (response.isSuccessful && response.body() != null) Result.Success(response.body()!!)
            else cached?.let { Result.Success(it.toHiveExtraction()) }
                ?: Result.Error(response.errorBody()?.string() ?: "Nu s-a putut încărca extracția", response.code())
        } catch (e: Exception) {
            cached?.let { Result.Success(it.toHiveExtraction()) }
                ?: Result.Error(e.message ?: "Eroare de rețea", exception = e)
        }
    }

    private suspend fun queueUpdateOffline(entity: ExtractionEntity, request: UpdateExtractionRequest): HiveExtraction {
        val updated = entity.copy(
            extractionDate = request.extractionDate,
            type = request.type.name,
            quantity = request.quantity,
            unit = request.unit,
            notes = request.notes,
            syncStatus = SyncStatus.PENDING_UPDATE
        )
        extractionDao.update(updated)
        syncQueueDao.insert(
            SyncQueueEntity(
                operationType = "UPDATE",
                entityType = "EXTRACTION",
                entityLocalId = entity.localId,
                entityServerId = entity.serverId,
                payload = updateAdapter.toJson(request)
            )
        )
        return updated.toHiveExtraction()
    }

    suspend fun enqueueUpdateExtraction(id: String, request: UpdateExtractionRequest): Result<HiveExtraction> =
        withContext(Dispatchers.IO) {
            val entity = extractionDao.getByLocalId(id) ?: extractionDao.getByServerId(id)
            entity?.let { Result.Success(queueUpdateOffline(it, request)) }
                ?: Result.Error("Extractie negasita local")
        }

    suspend fun updateExtraction(id: String, request: UpdateExtractionRequest): Result<HiveExtraction> = withContext(Dispatchers.IO) {
        val entity = extractionDao.getByLocalId(id) ?: extractionDao.getByServerId(id)
        if (!canReachBackend()) {
            return@withContext entity?.let { Result.Success(queueUpdateOffline(it, request)) }
                ?: Result.Error("Nicio conexiune la internet")
        }
        return@withContext try {
            val response = extractionApi.updateExtraction(id, request)
            if (response.isSuccessful && response.body() != null) {
                val server = response.body()!!
                extractionDao.insert(server.toEntity())
                Result.Success(server)
            } else {
                Result.Error(response.errorBody()?.string() ?: "Nu s-a putut actualiza extracția", response.code())
            }
        } catch (e: IOException) {
            entity?.let { Result.Success(queueUpdateOffline(it, request)) }
                ?: Result.Error("Recoltă negăsită local", exception = e)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Eroare de rețea", exception = e)
        }
    }

    private suspend fun queueDeleteOffline(entity: ExtractionEntity) {
        if (entity.serverId == null) {
            extractionDao.deleteByLocalId(entity.localId)
            syncQueueDao.deleteByEntityLocalId(entity.localId)
        } else {
            extractionDao.update(entity.copy(syncStatus = SyncStatus.PENDING_DELETE))
            syncQueueDao.deleteByEntityLocalId(entity.localId)
            syncQueueDao.insert(
                SyncQueueEntity(
                    operationType = "DELETE",
                    entityType = "EXTRACTION",
                    entityLocalId = entity.localId,
                    entityServerId = entity.serverId,
                    payload = ""
                )
            )
        }
    }

    suspend fun enqueueDeleteExtraction(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val entity = extractionDao.getByLocalId(id) ?: extractionDao.getByServerId(id)
        entity?.let {
            queueDeleteOffline(it)
            Result.Success(Unit)
        } ?: Result.Error("Extractie negasita local")
    }

    suspend fun deleteExtraction(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val entity = extractionDao.getByLocalId(id) ?: extractionDao.getByServerId(id)
        if (!canReachBackend()) {
            return@withContext entity?.let {
                queueDeleteOffline(it)
                Result.Success(Unit)
            } ?: Result.Error("Nicio conexiune la internet")
        }
        return@withContext try {
            val response = extractionApi.deleteExtraction(id)
            if (response.isSuccessful) {
                entity?.let { extractionDao.deleteByLocalId(it.localId) }
                Result.Success(Unit)
            } else {
                Result.Error(response.errorBody()?.string() ?: "Nu s-a putut șterge extracția", response.code())
            }
        } catch (e: IOException) {
            entity?.let {
                queueDeleteOffline(it)
                Result.Success(Unit)
            } ?: Result.Error("Recoltă negăsită local", exception = e)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Eroare de rețea", exception = e)
        }
    }
}

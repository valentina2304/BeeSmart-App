package com.example.beesmart.data.repository

import com.example.beesmart.data.local.dao.SyncQueueDao
import com.example.beesmart.data.local.dao.TreatmentDao
import com.example.beesmart.data.local.entity.SyncQueueEntity
import com.example.beesmart.data.local.entity.SyncStatus
import com.example.beesmart.data.local.entity.TreatmentEntity
import com.example.beesmart.data.local.entity.toEntity
import com.example.beesmart.network.BackendReachability
import com.example.beesmart.network.TreatmentApi
import com.example.beesmart.network.models.CreateTreatmentRequest
import com.example.beesmart.network.models.HiveTreatment
import com.example.beesmart.network.models.UpdateTreatmentRequest
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
class TreatmentRepository @Inject constructor(
    private val treatmentApi: TreatmentApi,
    private val treatmentDao: TreatmentDao,
    private val syncQueueDao: SyncQueueDao,
    private val connectivity: ConnectivityObserver,
    private val backendReachability: BackendReachability,
    private val moshi: Moshi
) {
    private val createAdapter by lazy { moshi.adapter(CreateTreatmentRequest::class.java) }
    private val updateAdapter by lazy { moshi.adapter(UpdateTreatmentRequest::class.java) }

    private fun canReachBackend(): Boolean =
        connectivity.canReachBackend(backendReachability)

    suspend fun getCachedAllTreatments(): List<HiveTreatment> = withContext(Dispatchers.IO) {
        treatmentDao.getAll().map { it.toHiveTreatment() }
    }

    fun observeCachedAllTreatments(): Flow<List<HiveTreatment>> =
        treatmentDao.observeAll().map { treatments -> treatments.map { it.toHiveTreatment() } }

    suspend fun getCachedTreatmentsByHiveId(hiveId: String): List<HiveTreatment> = withContext(Dispatchers.IO) {
        treatmentDao.getByHiveId(hiveId).map { it.toHiveTreatment() }
    }

    fun observeCachedTreatmentsByHiveId(hiveId: String): Flow<List<HiveTreatment>> =
        treatmentDao.observeByHiveId(hiveId).map { treatments -> treatments.map { it.toHiveTreatment() } }

    suspend fun getCachedTreatmentById(id: String): HiveTreatment? = withContext(Dispatchers.IO) {
        treatmentDao.getByLocalId(id)?.toHiveTreatment()
    }

    suspend fun getAllTreatments(): Result<List<HiveTreatment>> = withContext(Dispatchers.IO) {
        val cached = treatmentDao.getAll().map { it.toHiveTreatment() }
        if (!canReachBackend()) return@withContext Result.Success(cached)
        return@withContext try {
            val response = treatmentApi.getAllTreatments()
            if (response.isSuccessful && response.body() != null) {
                val serverData = response.body()!!
                treatmentDao.deleteAllSynced()
                treatmentDao.insertAll(serverData.map { it.toEntity() })
                Result.Success(treatmentDao.getAll().map { it.toHiveTreatment() })
            } else if (cached.isNotEmpty()) Result.Success(cached)
            else Result.Error(response.errorBody()?.string() ?: "Nu s-au putut încărca tratamentele", response.code())
        } catch (e: Exception) { Result.Success(cached) }
    }

    suspend fun getTreatmentsByHiveId(hiveId: String): Result<List<HiveTreatment>> = withContext(Dispatchers.IO) {
        val cached = treatmentDao.getByHiveId(hiveId).map { it.toHiveTreatment() }
        if (!canReachBackend()) {
            return@withContext Result.Success(cached)
        }
        return@withContext try {
            val response = treatmentApi.getTreatmentsByHiveId(hiveId)
            if (response.isSuccessful && response.body() != null) {
                val serverData = response.body()!!
                treatmentDao.deleteSyncedByHiveId(hiveId)
                treatmentDao.insertAll(serverData.map { it.toEntity() })
                // Merge: include PENDING_CREATE entries that haven't synced yet.
                Result.Success(treatmentDao.getByHiveId(hiveId).map { it.toHiveTreatment() })
            } else if (cached.isNotEmpty()) Result.Success(cached)
            else Result.Error(response.errorBody()?.string() ?: "Nu s-au putut încărca tratamentele", response.code())
        } catch (e: Exception) {
            Result.Success(cached)
        }
    }

    private suspend fun queueCreateOffline(request: CreateTreatmentRequest): HiveTreatment {
        val localId = UUID.randomUUID().toString()
        val entity = TreatmentEntity(
            localId = localId,
            serverId = null,
            hiveLocalId = request.hiveId,
            hiveServerId = null,
            apiaryId = "",
            treatmentDate = request.treatmentDate,
            type = request.type.name,
            productName = request.productName,
            substance = request.substance,
            dosage = request.dosage,
            notes = request.notes,
            nextTreatmentDate = request.nextTreatmentDate,
            syncStatus = SyncStatus.PENDING_CREATE
        )
        treatmentDao.insert(entity)
        syncQueueDao.insert(
            SyncQueueEntity(
                operationType = "CREATE",
                entityType = "TREATMENT",
                entityLocalId = localId,
                entityServerId = null,
                payload = createAdapter.toJson(request)
            )
        )
        return entity.toHiveTreatment()
    }

    suspend fun enqueueCreateTreatment(request: CreateTreatmentRequest): Result<HiveTreatment> =
        withContext(Dispatchers.IO) {
            Result.Success(queueCreateOffline(request))
        }

    suspend fun createTreatment(request: CreateTreatmentRequest): Result<HiveTreatment> = withContext(Dispatchers.IO) {
        if (!canReachBackend()) {
            return@withContext Result.Success(queueCreateOffline(request))
        }
        return@withContext try {
            val response = treatmentApi.createTreatment(request)
            if (response.isSuccessful && response.body() != null) {
                val server = response.body()!!
                treatmentDao.insert(server.toEntity())
                Result.Success(server)
            } else {
                Result.Error(response.errorBody()?.string() ?: "Nu s-a putut crea tratamentul", response.code())
            }
        } catch (e: IOException) {
            Result.Success(queueCreateOffline(request))
        } catch (e: Exception) {
            Result.Error(e.message ?: "Eroare de rețea", exception = e)
        }
    }

    suspend fun getTreatmentById(treatmentId: String): Result<HiveTreatment> = withContext(Dispatchers.IO) {
        val cached = treatmentDao.getByLocalId(treatmentId)
        if (!canReachBackend()) {
            return@withContext cached?.let { Result.Success(it.toHiveTreatment()) }
                ?: Result.Error("Nicio conexiune la internet")
        }
        return@withContext try {
            val response = treatmentApi.getTreatmentById(treatmentId)
            if (response.isSuccessful && response.body() != null) Result.Success(response.body()!!)
            else cached?.let { Result.Success(it.toHiveTreatment()) }
                ?: Result.Error(response.errorBody()?.string() ?: "Nu s-a putut încărca tratamentul", response.code())
        } catch (e: Exception) {
            cached?.let { Result.Success(it.toHiveTreatment()) }
                ?: Result.Error(e.message ?: "Eroare de rețea", exception = e)
        }
    }

    private suspend fun queueUpdateOffline(entity: TreatmentEntity, request: UpdateTreatmentRequest): HiveTreatment {
        val updated = entity.copy(
            treatmentDate = request.treatmentDate,
            type = request.type.name,
            productName = request.productName,
            substance = request.substance,
            dosage = request.dosage,
            notes = request.notes,
            nextTreatmentDate = request.nextTreatmentDate,
            syncStatus = SyncStatus.PENDING_UPDATE
        )
        treatmentDao.update(updated)
        syncQueueDao.insert(
            SyncQueueEntity(
                operationType = "UPDATE",
                entityType = "TREATMENT",
                entityLocalId = entity.localId,
                entityServerId = entity.serverId,
                payload = updateAdapter.toJson(request)
            )
        )
        return updated.toHiveTreatment()
    }

    suspend fun enqueueUpdateTreatment(id: String, request: UpdateTreatmentRequest): Result<HiveTreatment> =
        withContext(Dispatchers.IO) {
            val entity = treatmentDao.getByLocalId(id) ?: treatmentDao.getByServerId(id)
            entity?.let { Result.Success(queueUpdateOffline(it, request)) }
                ?: Result.Error("Tratament negasit local")
        }

    suspend fun updateTreatment(id: String, request: UpdateTreatmentRequest): Result<HiveTreatment> = withContext(Dispatchers.IO) {
        val entity = treatmentDao.getByLocalId(id) ?: treatmentDao.getByServerId(id)
        if (!canReachBackend()) {
            return@withContext entity?.let { Result.Success(queueUpdateOffline(it, request)) }
                ?: Result.Error("Nicio conexiune la internet")
        }
        return@withContext try {
            val response = treatmentApi.updateTreatment(id, request)
            if (response.isSuccessful && response.body() != null) {
                val server = response.body()!!
                treatmentDao.insert(server.toEntity())
                Result.Success(server)
            } else {
                Result.Error(response.errorBody()?.string() ?: "Nu s-a putut actualiza tratamentul", response.code())
            }
        } catch (e: IOException) {
            entity?.let { Result.Success(queueUpdateOffline(it, request)) }
                ?: Result.Error("Tratament negăsit local", exception = e)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Eroare de rețea", exception = e)
        }
    }

    private suspend fun queueDeleteOffline(entity: TreatmentEntity) {
        if (entity.serverId == null) {
            treatmentDao.deleteByLocalId(entity.localId)
            syncQueueDao.deleteByEntityLocalId(entity.localId)
        } else {
            treatmentDao.update(entity.copy(syncStatus = SyncStatus.PENDING_DELETE))
            syncQueueDao.deleteByEntityLocalId(entity.localId)
            syncQueueDao.insert(
                SyncQueueEntity(
                    operationType = "DELETE",
                    entityType = "TREATMENT",
                    entityLocalId = entity.localId,
                    entityServerId = entity.serverId,
                    payload = ""
                )
            )
        }
    }

    suspend fun enqueueDeleteTreatment(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val entity = treatmentDao.getByLocalId(id) ?: treatmentDao.getByServerId(id)
        entity?.let {
            queueDeleteOffline(it)
            Result.Success(Unit)
        } ?: Result.Error("Tratament negasit local")
    }

    suspend fun deleteTreatment(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val entity = treatmentDao.getByLocalId(id) ?: treatmentDao.getByServerId(id)
        if (!canReachBackend()) {
            return@withContext entity?.let {
                queueDeleteOffline(it)
                Result.Success(Unit)
            } ?: Result.Error("Nicio conexiune la internet")
        }
        return@withContext try {
            val response = treatmentApi.deleteTreatment(id)
            if (response.isSuccessful) {
                entity?.let { treatmentDao.deleteByLocalId(it.localId) }
                Result.Success(Unit)
            } else {
                Result.Error(response.errorBody()?.string() ?: "Nu s-a putut șterge tratamentul", response.code())
            }
        } catch (e: IOException) {
            entity?.let {
                queueDeleteOffline(it)
                Result.Success(Unit)
            } ?: Result.Error("Tratament negăsit local", exception = e)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Eroare de rețea", exception = e)
        }
    }
}

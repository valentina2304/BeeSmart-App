package com.example.beesmart.data.repository

import com.example.beesmart.BuildConfig
import com.example.beesmart.data.local.dao.HiveDao
import com.example.beesmart.data.local.dao.InspectionAiAnalysisDao
import com.example.beesmart.data.local.dao.InspectionDao
import com.example.beesmart.data.local.dao.InspectionPhotoDao
import com.example.beesmart.data.local.dao.SyncQueueDao
import com.example.beesmart.data.local.entity.InspectionAiAnalysisEntity
import com.example.beesmart.data.local.entity.InspectionEntity
import com.example.beesmart.data.local.entity.InspectionPhotoEntity
import com.example.beesmart.data.local.entity.SyncQueueEntity
import com.example.beesmart.data.local.entity.SyncStatus
import com.example.beesmart.data.local.entity.toEntity
import com.example.beesmart.network.BackendReachability
import com.example.beesmart.network.InspectionApi
import com.example.beesmart.network.models.AddInspectionPhotoRequest
import com.example.beesmart.network.models.AnalyzeCellsRequest
import com.example.beesmart.network.models.AnalyzeCellsResponse
import com.example.beesmart.network.models.CellDetection
import com.example.beesmart.network.models.CreateInspectionRequest
import com.example.beesmart.network.models.InspectionAiAnalysisResponse
import com.example.beesmart.network.models.InspectionDetailResponse
import com.example.beesmart.network.models.InspectionPhotoResponse
import com.example.beesmart.network.models.InspectionResponse
import com.example.beesmart.network.models.SaveInspectionAiAnalysisRequest
import com.example.beesmart.network.models.UpdateInspectionPhotoRequest
import com.example.beesmart.network.models.UpdateInspectionRequest
import com.example.beesmart.sync.ConnectivityObserver
import com.example.beesmart.sync.QueuedAiAnalysisCreate
import com.example.beesmart.sync.QueuedPhotoCreate
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InspectionRepository @Inject constructor(
    private val inspectionApi: InspectionApi,
    private val inspectionDao: InspectionDao,
    private val inspectionPhotoDao: InspectionPhotoDao,
    private val inspectionAiAnalysisDao: InspectionAiAnalysisDao,
    private val hiveDao: HiveDao,
    private val syncQueueDao: SyncQueueDao,
    private val connectivity: ConnectivityObserver,
    private val backendReachability: BackendReachability,
    private val moshi: Moshi
) {
    private fun canReachBackend(): Boolean =
        connectivity.canReachBackend(backendReachability)

    private val createAdapter by lazy { moshi.adapter(CreateInspectionRequest::class.java) }
    private val updateAdapter by lazy { moshi.adapter(UpdateInspectionRequest::class.java) }
    private val addPhotoAdapter by lazy { moshi.adapter(QueuedPhotoCreate::class.java) }
    private val addAiAnalysisAdapter by lazy { moshi.adapter(QueuedAiAnalysisCreate::class.java) }
    private val updatePhotoAdapter by lazy { moshi.adapter(UpdateInspectionPhotoRequest::class.java) }
    private val aiCountsAdapter by lazy {
        moshi.adapter<Map<String, Int>>(
            Types.newParameterizedType(Map::class.java, String::class.java, Integer::class.javaObjectType)
        )
    }
    private val aiCellDetectionsAdapter by lazy {
        moshi.adapter<List<CellDetection>>(
            Types.newParameterizedType(List::class.java, CellDetection::class.java)
        )
    }

    suspend fun getCachedAllInspections(): List<InspectionResponse> = withContext(Dispatchers.IO) {
        inspectionDao.getAll().map { it.toInspectionResponse() }
    }

    fun observeCachedAllInspections(): Flow<List<InspectionResponse>> =
        inspectionDao.observeAll().map { inspections -> inspections.map { it.toInspectionResponse() } }

    suspend fun getCachedInspectionsByApiaryId(apiaryId: String): List<InspectionResponse> =
        withContext(Dispatchers.IO) {
            inspectionDao.getByApiaryId(apiaryId).map { it.toInspectionResponse() }
        }

    fun observeCachedInspectionsByApiaryId(apiaryId: String): Flow<List<InspectionResponse>> =
        inspectionDao.observeByApiaryId(apiaryId).map { inspections -> inspections.map { it.toInspectionResponse() } }

    suspend fun getCachedInspectionsByHiveId(hiveId: String): List<InspectionResponse> =
        withContext(Dispatchers.IO) {
            inspectionDao.getByHiveId(hiveId).map { it.toInspectionResponse() }
        }

    fun observeCachedInspectionsByHiveId(hiveId: String): Flow<List<InspectionResponse>> =
        inspectionDao.observeByHiveId(hiveId).map { inspections -> inspections.map { it.toInspectionResponse() } }

    suspend fun getCachedInspectionById(id: String): InspectionDetailResponse? =
        withContext(Dispatchers.IO) {
            inspectionDao.getByLocalId(id)?.let { entity ->
                entity.toDetailResponse(inspectionPhotoDao.getByInspectionId(entity.localId))
            }
        }

    suspend fun getAllInspections(): Result<List<InspectionResponse>> = withContext(Dispatchers.IO) {
        val cached = inspectionDao.getAll().map { it.toInspectionResponse() }
        if (!canReachBackend()) {
            return@withContext Result.Success(cached)
        }
        return@withContext try {
            val response = inspectionApi.getAllInspections()
            if (response.isSuccessful && response.body() != null) {
                val serverData = response.body()!!
                inspectionDao.deleteAllSynced()
                inspectionDao.insertAll(serverData.map { it.toEntity() })
                // Merge: include PENDING_CREATE entries not yet on the server.
                Result.Success(inspectionDao.getAll().map { it.toInspectionResponse() })
            } else if (cached.isNotEmpty()) {
                Result.Success(cached)
            } else {
                Result.Error(
                    message = response.errorBody()?.string() ?: "Nu s-au putut încărca inspecțiile",
                    code = response.code()
                )
            }
        } catch (e: Exception) {
            Result.Success(cached)
        }
    }

    suspend fun getInspectionsByApiaryId(apiaryId: String): Result<List<InspectionResponse>> =
        withContext(Dispatchers.IO) {
            val cached = inspectionDao.getByApiaryId(apiaryId).map { it.toInspectionResponse() }
            if (!canReachBackend()) {
                return@withContext Result.Success(cached)
            }
            return@withContext try {
                val response = inspectionApi.getInspectionsByApiaryId(apiaryId)
                if (response.isSuccessful && response.body() != null) {
                    val serverData = response.body()!!
                    inspectionDao.deleteSyncedByApiaryId(apiaryId)
                    inspectionDao.insertAll(serverData.map { it.toEntity() })
                    // Merge: include PENDING_CREATE entries not yet on the server.
                    Result.Success(inspectionDao.getByApiaryId(apiaryId).map { it.toInspectionResponse() })
                } else if (cached.isNotEmpty()) Result.Success(cached)
                else Result.Error(
                    message = response.errorBody()?.string() ?: "Nu s-au putut încărca inspecțiile stupinei",
                    code = response.code()
                )
            } catch (e: Exception) {
                Result.Success(cached)
            }
        }

    suspend fun getInspectionsByHiveId(hiveId: String): Result<List<InspectionResponse>> =
        withContext(Dispatchers.IO) {
            val cached = inspectionDao.getByHiveId(hiveId).map { it.toInspectionResponse() }
            if (!canReachBackend()) {
                return@withContext Result.Success(cached)
            }
            return@withContext try {
                val response = inspectionApi.getInspectionsByHiveId(hiveId)
                if (response.isSuccessful && response.body() != null) {
                    val serverData = response.body()!!
                    inspectionDao.deleteSyncedByHiveId(hiveId)
                    inspectionDao.insertAll(serverData.map { it.toEntity() })
                    // Merge: include PENDING_CREATE entries not yet on the server.
                    Result.Success(inspectionDao.getByHiveId(hiveId).map { it.toInspectionResponse() })
                } else if (cached.isNotEmpty()) Result.Success(cached)
                else Result.Error(
                    message = response.errorBody()?.string() ?: "Nu s-au putut încărca inspecțiile stupului",
                    code = response.code()
                )
            } catch (e: Exception) {
                Result.Success(cached)
            }
        }

    suspend fun getInspectionById(id: String): Result<InspectionDetailResponse> =
        withContext(Dispatchers.IO) {
            if (!canReachBackend()) {
                return@withContext getCachedInspectionById(id)?.let { cached ->
                    Result.Success(cached)
                } ?: Result.Error("Nicio conexiune la internet")
            }
            return@withContext try {
                val response = inspectionApi.getInspectionById(id)
                if (response.isSuccessful && response.body() != null) {
                    val detail = response.body()!!
                    val cachedInspection = inspectionDao.getByLocalId(detail.id)
                    val inspectionLocalId = cachedInspection?.localId ?: detail.id
                    inspectionPhotoDao.deleteSyncedByInspectionId(inspectionLocalId)
                    inspectionPhotoDao.insertAll(
                        detail.photos.map { it.toEntity(inspectionLocalId, detail.id) }
                    )
                    Result.Success(detail)
                } else {
                    getCachedInspectionById(id)?.let { cached ->
                        Result.Success(cached)
                    } ?: Result.Error(
                        message = response.errorBody()?.string() ?: "Nu s-a putut încărca inspecția",
                        code = response.code()
                    )
                }
            } catch (e: Exception) {
                getCachedInspectionById(id)?.let { cached ->
                    Result.Success(cached)
                } ?: Result.Error(e.message ?: "Eroare de rețea", exception = e)
            }
        }

    private suspend fun queueCreateOffline(request: CreateInspectionRequest): InspectionResponse {
        val localId = UUID.randomUUID().toString()
        val hive = hiveDao.getByLocalId(request.hiveId) ?: hiveDao.getByServerId(request.hiveId)
        val entity = InspectionEntity(
            localId = localId,
            serverId = null,
            hiveLocalId = hive?.localId ?: request.hiveId,
            hiveServerId = hive?.serverId,
            hiveName = hive?.name ?: "",
            apiaryId = hive?.apiaryServerId ?: hive?.apiaryLocalId ?: "",
            apiaryName = hive?.apiaryName ?: "",
            inspectionDate = request.inspectionDate,
            temperature = request.temperature,
            framesCount = request.framesCount,
            broodFrames = request.broodFrames,
            honeyFrames = request.honeyFrames,
            pollenFrames = request.pollenFrames,
            queenSeen = request.queenSeen,
            eggsSeen = request.eggsSeen,
            larvaeSeen = request.larvaeSeen,
            photosCount = 0,
            createdAt = Instant.now().toString(),
            syncStatus = SyncStatus.PENDING_CREATE,
            queenCellsSeen = request.queenCellsSeen,
            queenCellsWithEggs = request.queenCellsWithEggs,
            beardingAtEntrance = request.beardingAtEntrance,
            spaceNeeded = request.spaceNeeded,
            broodPattern = request.broodPattern,
            honeyCappingPercent = request.honeyCappingPercent,
            feedingGiven = request.feedingGiven,
            waterAvailable = request.waterAvailable,
            moistureOrMold = request.moistureOrMold,
            deadBeesAtEntrance = request.deadBeesAtEntrance,
            unusualBehavior = request.unusualBehavior,
            temperament = request.temperament,
            oldCombsToReplace = request.oldCombsToReplace,
            notes = request.notes
        )
        inspectionDao.insert(entity)
        syncQueueDao.insert(
            SyncQueueEntity(
                operationType = "CREATE",
                entityType = "INSPECTION",
                entityLocalId = localId,
                entityServerId = null,
                payload = createAdapter.toJson(request)
            )
        )
        return entity.toInspectionResponse()
    }

    suspend fun enqueueCreateInspection(request: CreateInspectionRequest): Result<InspectionResponse> =
        withContext(Dispatchers.IO) {
            Result.Success(queueCreateOffline(request))
        }

    suspend fun createInspection(request: CreateInspectionRequest): Result<InspectionResponse> =
        withContext(Dispatchers.IO) {
            if (!canReachBackend()) {
                return@withContext Result.Success(queueCreateOffline(request))
            }
            return@withContext try {
                val response = inspectionApi.createInspection(request)
                if (response.isSuccessful && response.body() != null) {
                    val server = response.body()!!
                    inspectionDao.insert(server.toEntity())
                    Result.Success(server)
                } else {
                    Result.Error(
                        message = response.errorBody()?.string() ?: "Nu s-a putut crea inspecția",
                        code = response.code()
                    )
                }
            } catch (e: IOException) {
                Result.Success(queueCreateOffline(request))
            } catch (e: Exception) {
                Result.Error(e.message ?: "Eroare de rețea", exception = e)
            }
        }

    private suspend fun queueUpdateOffline(
        entity: InspectionEntity,
        request: UpdateInspectionRequest
    ): InspectionResponse {
        val updated = entity.copy(
            inspectionDate = request.inspectionDate,
            temperature = request.temperature,
            framesCount = request.framesCount,
            broodFrames = request.broodFrames,
            honeyFrames = request.honeyFrames,
            pollenFrames = request.pollenFrames,
            queenSeen = request.queenSeen,
            eggsSeen = request.eggsSeen,
            larvaeSeen = request.larvaeSeen,
            queenCellsSeen = request.queenCellsSeen,
            queenCellsWithEggs = request.queenCellsWithEggs,
            beardingAtEntrance = request.beardingAtEntrance,
            spaceNeeded = request.spaceNeeded,
            broodPattern = request.broodPattern,
            honeyCappingPercent = request.honeyCappingPercent,
            feedingGiven = request.feedingGiven,
            waterAvailable = request.waterAvailable,
            moistureOrMold = request.moistureOrMold,
            deadBeesAtEntrance = request.deadBeesAtEntrance,
            unusualBehavior = request.unusualBehavior,
            temperament = request.temperament,
            oldCombsToReplace = request.oldCombsToReplace,
            notes = request.notes,
            syncStatus = if (entity.syncStatus == SyncStatus.PENDING_CREATE) SyncStatus.PENDING_CREATE
                         else SyncStatus.PENDING_UPDATE
        )
        inspectionDao.update(updated)
        if (entity.syncStatus == SyncStatus.PENDING_CREATE) {
            syncQueueDao.getLatestForEntity(
                operationType = "CREATE",
                entityType = "INSPECTION",
                localId = entity.localId
            )?.let { createOp ->
                syncQueueDao.update(
                    createOp.copy(
                        payload = createAdapter.toJson(
                            request.toCreateInspectionRequest(entity.hiveServerId ?: entity.hiveLocalId)
                        ),
                        retryCount = 0
                    )
                )
            }
        } else {
            syncQueueDao.insert(
                SyncQueueEntity(
                    operationType = "UPDATE",
                    entityType = "INSPECTION",
                    entityLocalId = entity.localId,
                    entityServerId = entity.serverId,
                    payload = updateAdapter.toJson(request)
                )
            )
        }
        return updated.toInspectionResponse()
    }

    private fun UpdateInspectionRequest.toCreateInspectionRequest(hiveId: String) = CreateInspectionRequest(
        hiveId = hiveId,
        inspectionDate = inspectionDate,
        temperature = temperature,
        framesCount = framesCount,
        broodFrames = broodFrames,
        honeyFrames = honeyFrames,
        pollenFrames = pollenFrames,
        queenSeen = queenSeen,
        eggsSeen = eggsSeen,
        larvaeSeen = larvaeSeen,
        queenCellsSeen = queenCellsSeen,
        queenCellsWithEggs = queenCellsWithEggs,
        beardingAtEntrance = beardingAtEntrance,
        spaceNeeded = spaceNeeded,
        broodPattern = broodPattern,
        honeyCappingPercent = honeyCappingPercent,
        feedingGiven = feedingGiven,
        waterAvailable = waterAvailable,
        moistureOrMold = moistureOrMold,
        deadBeesAtEntrance = deadBeesAtEntrance,
        unusualBehavior = unusualBehavior,
        temperament = temperament,
        oldCombsToReplace = oldCombsToReplace,
        notes = notes
    )

    suspend fun enqueueUpdateInspection(id: String, request: UpdateInspectionRequest): Result<InspectionResponse> =
        withContext(Dispatchers.IO) {
            val entity = inspectionDao.getByLocalId(id)
            entity?.let { Result.Success(queueUpdateOffline(it, request)) }
                ?: Result.Error("Inspectie negasita local")
        }

    suspend fun updateInspection(id: String, request: UpdateInspectionRequest): Result<InspectionResponse> =
        withContext(Dispatchers.IO) {
            val entity = inspectionDao.getByLocalId(id)
            if (!canReachBackend()) {
                return@withContext entity?.let { Result.Success(queueUpdateOffline(it, request)) }
                    ?: Result.Error("Inspecția nu a fost găsită local")
            }
            return@withContext try {
                val response = inspectionApi.updateInspection(id, request)
                if (response.isSuccessful && response.body() != null) {
                    val server = response.body()!!
                    inspectionDao.insert(server.toEntity())
                    Result.Success(server)
                } else {
                    Result.Error(
                        message = response.errorBody()?.string() ?: "Nu s-a putut actualiza inspecția",
                        code = response.code()
                    )
                }
            } catch (e: IOException) {
                entity?.let { Result.Success(queueUpdateOffline(it, request)) }
                    ?: Result.Error("Inspecția nu a fost găsită local", exception = e)
            } catch (e: Exception) {
                Result.Error(e.message ?: "Eroare de rețea", exception = e)
            }
        }

    private suspend fun queueDeleteOffline(entity: InspectionEntity) {
        if (entity.serverId == null) {
            inspectionDao.deleteByLocalId(entity.localId)
            syncQueueDao.deleteByEntityLocalId(entity.localId)
        } else {
            inspectionDao.update(entity.copy(syncStatus = SyncStatus.PENDING_DELETE))
            syncQueueDao.deleteByEntityLocalId(entity.localId)
            syncQueueDao.insert(
                SyncQueueEntity(
                    operationType = "DELETE",
                    entityType = "INSPECTION",
                    entityLocalId = entity.localId,
                    entityServerId = entity.serverId,
                    payload = ""
                )
            )
        }
    }

    suspend fun enqueueDeleteInspection(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val entity = inspectionDao.getByLocalId(id)
        entity?.let {
            queueDeleteOffline(it)
            Result.Success(Unit)
        } ?: Result.Error("Inspectie negasita local")
    }

    suspend fun deleteInspection(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        val entity = inspectionDao.getByLocalId(id)
        if (!canReachBackend()) {
            return@withContext entity?.let {
                queueDeleteOffline(it)
                Result.Success(Unit)
            } ?: Result.Error("Inspecția nu a fost găsită local")
        }
        return@withContext try {
            val response = inspectionApi.deleteInspection(id)
            if (response.isSuccessful) {
                entity?.let { inspectionDao.deleteByLocalId(it.localId) }
                Result.Success(Unit)
            } else {
                Result.Error(
                    message = response.errorBody()?.string() ?: "Nu s-a putut șterge inspecția",
                    code = response.code()
                )
            }
        } catch (e: IOException) {
            entity?.let {
                queueDeleteOffline(it)
                Result.Success(Unit)
            } ?: Result.Error("Inspecția nu a fost găsită local", exception = e)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Eroare de rețea", exception = e)
        }
    }

    // ==================== PHOTO OPERATIONS ====================

    private suspend fun queueAddPhotoOffline(
        inspectionId: String,
        request: AddInspectionPhotoRequest
    ): Result<InspectionPhotoResponse> {
        val entity = inspectionDao.getByLocalId(inspectionId)
            ?: return Result.Error("Inspecția nu a fost găsită local")
        val photoLocalId = UUID.randomUUID().toString()
        val wrapper = QueuedPhotoCreate(inspectionLocalId = entity.localId, request = request)
        val createdAt = Instant.now().toString()
        inspectionPhotoDao.insert(
            InspectionPhotoEntity(
                localId = photoLocalId,
                serverId = null,
                inspectionLocalId = entity.localId,
                inspectionServerId = entity.serverId,
                photoUrl = request.photoUrl,
                description = request.description,
                createdAt = createdAt,
                syncStatus = SyncStatus.PENDING_CREATE
            )
        )
        syncQueueDao.insert(
            SyncQueueEntity(
                operationType = "CREATE",
                entityType = "INSPECTION_PHOTO",
                entityLocalId = photoLocalId,
                entityServerId = null,
                payload = addPhotoAdapter.toJson(wrapper)
            )
        )
        inspectionDao.update(entity.copy(photosCount = entity.photosCount + 1))
        return Result.Success(
            InspectionPhotoResponse(
                id = photoLocalId,
                inspectionId = entity.serverId ?: entity.localId,
                photoUrl = request.photoUrl,
                description = request.description,
                createdAt = createdAt
            )
        )
    }

    suspend fun enqueueAddPhoto(
        inspectionId: String,
        request: AddInspectionPhotoRequest
    ): Result<InspectionPhotoResponse> = withContext(Dispatchers.IO) {
        queueAddPhotoOffline(inspectionId, request)
    }

    suspend fun addPhoto(inspectionId: String, request: AddInspectionPhotoRequest): Result<InspectionPhotoResponse> =
        withContext(Dispatchers.IO) {
            val cachedInspection = inspectionDao.getByLocalId(inspectionId)
            if (cachedInspection?.serverId == null && cachedInspection != null) {
                return@withContext queueAddPhotoOffline(cachedInspection.localId, request)
            }

            if (!canReachBackend()) {
                return@withContext queueAddPhotoOffline(inspectionId, request)
            }
            try {
                val targetInspectionId = cachedInspection?.serverId ?: inspectionId
                val response = inspectionApi.addPhoto(targetInspectionId, request)
                if (response.isSuccessful && response.body() != null) {
                    val photo = response.body()!!
                    val inspection = cachedInspection
                    inspectionPhotoDao.insert(
                        photo.toEntity(
                            inspectionLocalId = inspection?.localId ?: photo.inspectionId,
                            inspectionServerId = inspection?.serverId ?: photo.inspectionId
                        )
                    )
                    if (inspection != null) {
                        inspectionDao.update(inspection.copy(photosCount = inspection.photosCount + 1))
                    }
                    Result.Success(photo)
                } else {
                    Result.Error(
                        message = response.errorBody()?.string() ?: "Nu s-a putut adăuga fotografia",
                        code = response.code()
                    )
                }
            } catch (e: IOException) {
                queueAddPhotoOffline(inspectionId, request)
            } catch (e: Exception) {
                Result.Error(e.message ?: "Eroare de rețea", exception = e)
            }
        }

    private suspend fun queueUpdatePhotoOffline(
        photoId: String,
        request: UpdateInspectionPhotoRequest
    ): Result<InspectionPhotoResponse> {
        val cached = inspectionPhotoDao.getById(photoId)
        if (cached?.serverId == null) {
            val updated = cached?.copy(
                description = request.description,
                syncStatus = SyncStatus.PENDING_CREATE,
                updatedAt = System.currentTimeMillis()
            )
            updated?.let { inspectionPhotoDao.update(it) }

            if (cached != null) {
                val createOp = syncQueueDao.getLatestForEntity(
                    operationType = "CREATE",
                    entityType = "INSPECTION_PHOTO",
                    localId = cached.localId
                )
                val existingPayload = createOp?.payload?.let { payload ->
                    runCatching { addPhotoAdapter.fromJson(payload) }.getOrNull()
                }
                val nextPayload = existingPayload?.copy(
                    request = existingPayload.request.copy(description = request.description)
                ) ?: QueuedPhotoCreate(
                    inspectionLocalId = cached.inspectionLocalId,
                    request = AddInspectionPhotoRequest(cached.photoUrl, request.description)
                )
                if (createOp != null) {
                    syncQueueDao.update(
                        createOp.copy(payload = addPhotoAdapter.toJson(nextPayload), retryCount = 0)
                    )
                } else {
                    syncQueueDao.insert(
                        SyncQueueEntity(
                            operationType = "CREATE",
                            entityType = "INSPECTION_PHOTO",
                            entityLocalId = cached.localId,
                            entityServerId = null,
                            payload = addPhotoAdapter.toJson(nextPayload)
                        )
                    )
                }
            } else {
                syncQueueDao.insert(
                    SyncQueueEntity(
                        operationType = "UPDATE",
                        entityType = "INSPECTION_PHOTO",
                        entityLocalId = photoId,
                        entityServerId = photoId,
                        payload = updatePhotoAdapter.toJson(request)
                    )
                )
            }
            return Result.Success(
                updated?.toResponse()
                    ?: InspectionPhotoResponse(
                        id = photoId,
                        inspectionId = "",
                        photoUrl = "",
                        description = request.description,
                        createdAt = Instant.now().toString()
                    )
            )
        }

        inspectionPhotoDao.update(
            cached.copy(
                description = request.description,
                syncStatus = SyncStatus.PENDING_UPDATE,
                updatedAt = System.currentTimeMillis()
            )
        )
        syncQueueDao.insert(
            SyncQueueEntity(
                operationType = "UPDATE",
                entityType = "INSPECTION_PHOTO",
                entityLocalId = cached.localId,
                entityServerId = cached.serverId,
                payload = updatePhotoAdapter.toJson(request)
            )
        )
        return Result.Success(cached.copy(description = request.description).toResponse())
    }

    suspend fun enqueueUpdatePhoto(
        photoId: String,
        request: UpdateInspectionPhotoRequest
    ): Result<InspectionPhotoResponse> = withContext(Dispatchers.IO) {
        queueUpdatePhotoOffline(photoId, request)
    }

    suspend fun updatePhoto(photoId: String, request: UpdateInspectionPhotoRequest): Result<InspectionPhotoResponse> =
        withContext(Dispatchers.IO) {
            if (!canReachBackend()) {
                return@withContext queueUpdatePhotoOffline(photoId, request)
            }
            try {
                val response = inspectionApi.updatePhoto(photoId, request)
                if (response.isSuccessful && response.body() != null) {
                    val photo = response.body()!!
                    inspectionPhotoDao.getById(photoId)?.let {
                        inspectionPhotoDao.update(
                            it.copy(
                                serverId = photo.id,
                                photoUrl = photo.photoUrl,
                                description = photo.description,
                                syncStatus = SyncStatus.SYNCED,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    Result.Success(photo)
                } else {
                    Result.Error(
                        message = response.errorBody()?.string() ?: "Nu s-a putut actualiza fotografia",
                        code = response.code()
                    )
                }
            } catch (e: IOException) {
                queueUpdatePhotoOffline(photoId, request)
            } catch (e: Exception) {
                Result.Error(e.message ?: "Eroare de rețea", exception = e)
            }
        }

    private suspend fun queueDeletePhotoOffline(photoId: String) {
        val photo = inspectionPhotoDao.getById(photoId) ?: return
        inspectionDao.getByLocalId(photo.inspectionLocalId)?.let { inspection ->
            inspectionDao.update(inspection.copy(photosCount = maxOf(0, inspection.photosCount - 1)))
        }
        if (photo.serverId == null) {
            inspectionPhotoDao.deleteById(photo.localId)
            syncQueueDao.deleteByEntityLocalId(photo.localId)
            return
        }
        inspectionPhotoDao.update(photo.copy(syncStatus = SyncStatus.PENDING_DELETE))
        syncQueueDao.deleteByEntityLocalId(photo.localId)
        syncQueueDao.insert(
            SyncQueueEntity(
                operationType = "DELETE",
                entityType = "INSPECTION_PHOTO",
                entityLocalId = photo.localId,
                entityServerId = photo.serverId,
                payload = ""
            )
        )
    }

    suspend fun enqueueDeletePhoto(photoId: String): Result<Unit> = withContext(Dispatchers.IO) {
        queueDeletePhotoOffline(photoId)
        Result.Success(Unit)
    }

    suspend fun deletePhoto(photoId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!canReachBackend()) {
            queueDeletePhotoOffline(photoId)
            return@withContext Result.Success(Unit)
        }
        try {
            val response = inspectionApi.deletePhoto(photoId)
            if (response.isSuccessful) {
                inspectionPhotoDao.deleteById(photoId)
                Result.Success(Unit)
            } else {
                Result.Error(
                    message = response.errorBody()?.string() ?: "Nu s-a putut șterge fotografia",
                    code = response.code()
                )
            }
        } catch (e: IOException) {
            queueDeletePhotoOffline(photoId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e.message ?: "Eroare de rețea", exception = e)
        }
    }

    // ==================== AI ANALYSIS OPERATIONS ====================

    suspend fun analyzeCells(request: AnalyzeCellsRequest): Result<AnalyzeCellsResponse> =
        withContext(Dispatchers.IO) {
            if (!canReachBackend()) {
                if (BuildConfig.AI_DEMO_FALLBACK_ENABLED) {
                    return@withContext Result.Success(
                        PresentationAiFallback.response("backend offline sau indisponibil")
                    )
                }
                return@withContext Result.Error("Analiza AI necesită conexiune la internet")
            }
            try {
                val response = if (BuildConfig.AI_DEMO_FALLBACK_ENABLED) {
                    withTimeout(BuildConfig.AI_DEMO_FALLBACK_TIMEOUT_MS) {
                        inspectionApi.analyzeCells(request)
                    }
                } else {
                    inspectionApi.analyzeCells(request)
                }
                if (response.isSuccessful && response.body() != null) {
                    Result.Success(response.body()!!)
                } else if (BuildConfig.AI_DEMO_FALLBACK_ENABLED && response.code().isTransientAiFailure()) {
                    Result.Success(
                        PresentationAiFallback.response("serverul a raspuns cu ${response.code()}")
                    )
                } else {
                    Result.Error(
                        message = response.errorBody()?.string() ?: "Nu s-a putut analiza imaginea",
                        code = response.code()
                    )
                }
            } catch (e: TimeoutCancellationException) {
                if (BuildConfig.AI_DEMO_FALLBACK_ENABLED) {
                    Result.Success(
                        PresentationAiFallback.response("timeout dupa ${BuildConfig.AI_DEMO_FALLBACK_TIMEOUT_MS / 1000}s")
                    )
                } else {
                    Result.Error(e.message ?: "Analiza AI a depasit timpul de asteptare", exception = e)
                }
            } catch (e: Exception) {
                if (BuildConfig.AI_DEMO_FALLBACK_ENABLED) {
                    Result.Success(
                        PresentationAiFallback.response(e.message ?: "eroare de retea")
                    )
                } else {
                    Result.Error(e.message ?: "Eroare de rețea", exception = e)
                }
            }
        }

    private fun Int.isTransientAiFailure(): Boolean = this in setOf(408, 429, 500, 502, 503, 504)

    suspend fun saveAiAnalysis(
        inspectionId: String,
        request: SaveInspectionAiAnalysisRequest
    ): Result<InspectionAiAnalysisResponse> =
        withContext(Dispatchers.IO) {
            val cachedInspection = inspectionDao.getByLocalId(inspectionId)
            if (cachedInspection?.serverId == null && cachedInspection != null) {
                return@withContext queueSaveAiAnalysisOffline(cachedInspection.localId, request)
            }

            if (!canReachBackend()) {
                return@withContext queueSaveAiAnalysisOffline(inspectionId, request)
            }
            try {
                val targetInspectionId = cachedInspection?.serverId ?: inspectionId
                val response = inspectionApi.saveAiAnalysis(targetInspectionId, request)
                if (response.isSuccessful && response.body() != null) {
                    Result.Success(response.body()!!)
                } else if (response.code() >= 500) {
                    queueSaveAiAnalysisOffline(inspectionId, request)
                } else {
                    Result.Error(
                        message = response.errorBody()?.string() ?: "Nu s-a putut salva analiza AI",
                        code = response.code()
                    )
                }
            } catch (e: IOException) {
                queueSaveAiAnalysisOffline(inspectionId, request)
            } catch (e: Exception) {
                Result.Error(e.message ?: "Eroare de rețea", exception = e)
            }
        }

    private suspend fun queueSaveAiAnalysisOffline(
        inspectionId: String,
        request: SaveInspectionAiAnalysisRequest
    ): Result<InspectionAiAnalysisResponse> {
        val inspection = inspectionDao.getByLocalId(inspectionId)
            ?: return Result.Error("Inspecția nu a fost găsită local")

        val queueLocalId = "ai-analysis-${inspection.localId}"
        val payload = addAiAnalysisAdapter.toJson(
            QueuedAiAnalysisCreate(
                inspectionLocalId = inspection.localId,
                request = request
            )
        )
        val existing = syncQueueDao.getLatestForEntity(
            operationType = "CREATE",
            entityType = "INSPECTION_AI_ANALYSIS",
            localId = queueLocalId
        )

        if (existing != null) {
            syncQueueDao.update(existing.copy(payload = payload, retryCount = 0))
        } else {
            syncQueueDao.insert(
                SyncQueueEntity(
                    operationType = "CREATE",
                    entityType = "INSPECTION_AI_ANALYSIS",
                    entityLocalId = queueLocalId,
                    entityServerId = inspection.serverId,
                    payload = payload
                )
            )
        }

        return Result.Success(request.toLocalAiAnalysisResponse(inspection))
    }

    suspend fun getAiAnalysesByHiveId(hiveId: String): Result<List<InspectionAiAnalysisResponse>> =
        withContext(Dispatchers.IO) {
            val local = getLocalAiAnalysesByHiveId(hiveId)
            if (!canReachBackend()) {
                return@withContext if (local.isNotEmpty()) {
                    Result.Success(local)
                } else {
                    Result.Error("Statisticile AI necesită conexiune la internet")
                }
            }
            try {
                val response = inspectionApi.getAiAnalysesByHiveId(hiveId)
                if (response.isSuccessful && response.body() != null) {
                    val server = response.body()!!
                    cacheServerAiAnalyses(server)
                    Result.Success(mergeServerAndLocalAnalyses(server, local))
                } else {
                    if (local.isNotEmpty()) {
                        Result.Success(local)
                    } else {
                        Result.Error(
                            message = response.errorBody()?.string() ?: "Nu s-a putut încărca istoricul analizelor AI",
                            code = response.code()
                        )
                    }
                }
            } catch (e: Exception) {
                if (local.isNotEmpty()) {
                    Result.Success(local)
                } else {
                    Result.Error(e.message ?: "Eroare de rețea", exception = e)
                }
            }
        }

    suspend fun getCachedAiAnalysesByHiveId(hiveId: String): List<InspectionAiAnalysisResponse> =
        withContext(Dispatchers.IO) {
            getLocalAiAnalysesByHiveId(hiveId)
        }

    private suspend fun getLocalAiAnalysesByHiveId(hiveId: String): List<InspectionAiAnalysisResponse> {
        return inspectionDao.getByHiveId(hiveId).mapNotNull { inspection ->
            val saved = inspectionAiAnalysisDao.getLatestForInspection(inspection.localId)
                ?: inspection.serverId?.let { inspectionAiAnalysisDao.getLatestForInspection(it) }
                ?: return@mapNotNull null
            saved.toAiAnalysisResponse(inspection)
        }
    }

    private fun mergeServerAndLocalAnalyses(
        server: List<InspectionAiAnalysisResponse>,
        local: List<InspectionAiAnalysisResponse>
    ): List<InspectionAiAnalysisResponse> {
        val serverInspectionIds = server.map { it.inspectionId }.toSet()
        return (server + local.filterNot { it.inspectionId in serverInspectionIds })
            .sortedByDescending { it.inspectionDate }
    }

    private suspend fun cacheServerAiAnalyses(server: List<InspectionAiAnalysisResponse>) {
        server.forEach { analysis ->
            val inspection = inspectionDao.getByLocalId(analysis.inspectionId) ?: return@forEach
            inspectionAiAnalysisDao.insert(
                InspectionAiAnalysisEntity(
                    inspectionLocalId = inspection.localId,
                    inspectionServerId = inspection.serverId ?: analysis.inspectionId,
                    rawCountsJson = aiCountsAdapter.toJson(analysis.results),
                    cellDetectionsJson = aiCellDetectionsAdapter.toJson(analysis.cellDetections),
                    message = analysis.message,
                    computedAt = analysis.cacheTimestamp()
                )
            )
        }
    }

    private fun InspectionAiAnalysisResponse.cacheTimestamp(): Long =
        runCatching { Instant.parse(createdAt).toEpochMilli() }
            .getOrElse {
                runCatching { java.time.OffsetDateTime.parse(inspectionDate).toInstant().toEpochMilli() }
                    .getOrDefault(System.currentTimeMillis())
            }

    private fun SaveInspectionAiAnalysisRequest.toLocalAiAnalysisResponse(
        inspection: InspectionEntity
    ): InspectionAiAnalysisResponse {
        val report = BroodAnalyzer.analyze(results)
        val totals = report.metrics.totals
        val capped = totals[BroodAnalyzer.Category.CAPPED_BROOD] ?: 0
        val larvae = totals[BroodAnalyzer.Category.LARVAE] ?: 0
        val eggs = totals[BroodAnalyzer.Category.EGGS] ?: 0
        val honey = totals[BroodAnalyzer.Category.HONEY] ?: 0
        val pollen = totals[BroodAnalyzer.Category.POLLEN] ?: 0
        val empty = totals[BroodAnalyzer.Category.EMPTY] ?: 0
        val other = totals[BroodAnalyzer.Category.OTHER] ?: 0

        return InspectionAiAnalysisResponse(
            id = "local-ai-${inspection.localId}",
            inspectionId = inspection.serverId ?: inspection.localId,
            hiveId = inspection.hiveServerId ?: inspection.hiveLocalId,
            apiaryId = inspection.apiaryId,
            inspectionDate = inspection.inspectionDate,
            status = status,
            results = results,
            message = message,
            totalCells = report.metrics.total,
            cappedBroodCells = capped,
            larvaeCells = larvae,
            eggsCells = eggs,
            honeyCells = honey,
            pollenCells = pollen,
            emptyCells = empty,
            otherCells = other,
            broodCells = report.metrics.broodTotal,
            storesCells = honey + pollen,
            broodDensity = report.metrics.broodDensity.finiteOrNull(),
            larvaeToCappedRatio = report.metrics.larvaeToCappedRatio.finiteOrNull(),
            storesRatio = report.metrics.storesRatio.finiteOrNull(),
            createdAt = Instant.now().toString(),
            cellDetections = cellDetections
        )
    }

    private fun InspectionAiAnalysisEntity.toAiAnalysisResponse(
        inspection: InspectionEntity
    ): InspectionAiAnalysisResponse? {
        val rawCounts = runCatching { aiCountsAdapter.fromJson(rawCountsJson) }
            .getOrNull()
            ?: return null
        val cellDetections = runCatching { aiCellDetectionsAdapter.fromJson(cellDetectionsJson) }
            .getOrNull()
            ?: emptyList()
        val report = BroodAnalyzer.analyze(rawCounts)
        val totals = report.metrics.totals
        val capped = totals[BroodAnalyzer.Category.CAPPED_BROOD] ?: 0
        val larvae = totals[BroodAnalyzer.Category.LARVAE] ?: 0
        val eggs = totals[BroodAnalyzer.Category.EGGS] ?: 0
        val honey = totals[BroodAnalyzer.Category.HONEY] ?: 0
        val pollen = totals[BroodAnalyzer.Category.POLLEN] ?: 0
        val empty = totals[BroodAnalyzer.Category.EMPTY] ?: 0
        val other = totals[BroodAnalyzer.Category.OTHER] ?: 0

        return InspectionAiAnalysisResponse(
            id = "local-$id",
            inspectionId = inspection.serverId ?: inspection.localId,
            hiveId = inspection.hiveServerId ?: inspection.hiveLocalId,
            apiaryId = inspection.apiaryId,
            inspectionDate = inspection.inspectionDate,
            status = "success",
            results = rawCounts,
            message = message,
            totalCells = report.metrics.total,
            cappedBroodCells = capped,
            larvaeCells = larvae,
            eggsCells = eggs,
            honeyCells = honey,
            pollenCells = pollen,
            emptyCells = empty,
            otherCells = other,
            broodCells = report.metrics.broodTotal,
            storesCells = honey + pollen,
            broodDensity = report.metrics.broodDensity.finiteOrNull(),
            larvaeToCappedRatio = report.metrics.larvaeToCappedRatio.finiteOrNull(),
            storesRatio = report.metrics.storesRatio.finiteOrNull(),
            createdAt = Instant.ofEpochMilli(computedAt).toString(),
            cellDetections = cellDetections
        )
    }

    private fun Double.finiteOrNull(): Double? = if (isFinite()) this else null
}

private fun InspectionEntity.toDetailResponse(photos: List<InspectionPhotoEntity>): InspectionDetailResponse {
    val summary = toInspectionResponse()
    return InspectionDetailResponse(
        id = summary.id,
        hiveId = summary.hiveId,
        hiveName = summary.hiveName,
        apiaryId = summary.apiaryId,
        apiaryName = summary.apiaryName,
        inspectionDate = summary.inspectionDate,
        temperature = summary.temperature,
        framesCount = summary.framesCount,
        broodFrames = summary.broodFrames,
        honeyFrames = summary.honeyFrames,
        pollenFrames = summary.pollenFrames,
        queenSeen = summary.queenSeen,
        eggsSeen = summary.eggsSeen,
        larvaeSeen = summary.larvaeSeen,
        photos = photos.map { it.toResponse() },
        createdAt = summary.createdAt,
        updatedAt = summary.updatedAt,
        queenCellsSeen = summary.queenCellsSeen,
        queenCellsWithEggs = summary.queenCellsWithEggs,
        beardingAtEntrance = summary.beardingAtEntrance,
        spaceNeeded = summary.spaceNeeded,
        broodPattern = summary.broodPattern,
        honeyCappingPercent = summary.honeyCappingPercent,
        feedingGiven = summary.feedingGiven,
        waterAvailable = summary.waterAvailable,
        moistureOrMold = summary.moistureOrMold,
        deadBeesAtEntrance = summary.deadBeesAtEntrance,
        unusualBehavior = summary.unusualBehavior,
        temperament = summary.temperament,
        oldCombsToReplace = summary.oldCombsToReplace,
        notes = summary.notes
    )
}

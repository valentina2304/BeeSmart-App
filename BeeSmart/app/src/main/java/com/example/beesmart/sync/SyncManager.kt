package com.example.beesmart.sync

import android.util.Log
import com.example.beesmart.data.local.dao.*
import com.example.beesmart.data.local.entity.*
import com.example.beesmart.di.AuthenticatedClient
import com.example.beesmart.network.ApiaryApi
import com.example.beesmart.network.AuthApi
import com.example.beesmart.network.ExtractionApi
import com.example.beesmart.network.HiveApi
import com.example.beesmart.network.InspectionApi
import com.example.beesmart.network.TaskApi
import com.example.beesmart.network.TreatmentApi
import com.example.beesmart.network.models.*
import com.example.beesmart.utils.SessionManager
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SyncManager"
private const val MAX_RETRIES = 3

@Singleton
class SyncManager @Inject constructor(
    private val apiaryDao: ApiaryDao,
    private val hiveDao: HiveDao,
    private val taskDao: TaskDao,
    private val treatmentDao: TreatmentDao,
    private val extractionDao: ExtractionDao,
    private val inspectionDao: InspectionDao,
    private val inspectionPhotoDao: InspectionPhotoDao,
    private val syncQueueDao: SyncQueueDao,
    private val inspectionAiAnalysisDao: com.example.beesmart.data.local.dao.InspectionAiAnalysisDao,
    private val apiaryApi: ApiaryApi,
    private val hiveApi: HiveApi,
    private val taskApi: TaskApi,
    private val treatmentApi: TreatmentApi,
    private val extractionApi: ExtractionApi,
    private val inspectionApi: InspectionApi,
    @AuthenticatedClient private val authApi: AuthApi,
    private val sessionManager: SessionManager,
    private val moshi: Moshi
) {
    private val createApiaryAdapter by lazy { moshi.adapter(CreateApiaryRequest::class.java) }
    private val updateApiaryAdapter by lazy { moshi.adapter(UpdateApiaryRequest::class.java) }
    private val createHiveAdapter by lazy { moshi.adapter(CreateHiveRequest::class.java) }
    private val updateHiveAdapter by lazy { moshi.adapter(UpdateHiveRequest::class.java) }
    private val createTaskAdapter by lazy { moshi.adapter(CreateTaskRequest::class.java) }
    private val updateTaskAdapter by lazy { moshi.adapter(UpdateTaskRequest::class.java) }
    private val createTreatmentAdapter by lazy { moshi.adapter(CreateTreatmentRequest::class.java) }
    private val updateTreatmentAdapter by lazy { moshi.adapter(UpdateTreatmentRequest::class.java) }
    private val createExtractionAdapter by lazy { moshi.adapter(CreateExtractionRequest::class.java) }
    private val updateExtractionAdapter by lazy { moshi.adapter(UpdateExtractionRequest::class.java) }
    private val createInspectionAdapter by lazy { moshi.adapter(CreateInspectionRequest::class.java) }
    private val updateInspectionAdapter by lazy { moshi.adapter(UpdateInspectionRequest::class.java) }
    private val addPhotoAdapter by lazy { moshi.adapter(QueuedPhotoCreate::class.java) }
    private val addAiAnalysisAdapter by lazy { moshi.adapter(QueuedAiAnalysisCreate::class.java) }
    private val updatePhotoAdapter by lazy { moshi.adapter(UpdateInspectionPhotoRequest::class.java) }
    private val updateProfileAdapter by lazy { moshi.adapter(UpdateProfileRequest::class.java) }
    private val userProfileAdapter by lazy { moshi.adapter(UserProfile::class.java) }

    suspend fun processQueue() = withContext(Dispatchers.IO) {
        val queue = syncQueueDao.getAll()
        if (queue.isEmpty()) return@withContext

        Log.d(TAG, "Processing ${queue.size} pending sync operations")

        // Process in dependency order: APIARY → HIVE → TASK/TREATMENT/EXTRACTION/INSPECTION → INSPECTION_PHOTO
        val createOrder = listOf(
            "APIARY",
            "HIVE",
            "TASK",
            "TREATMENT",
            "EXTRACTION",
            "INSPECTION",
            "INSPECTION_AI_ANALYSIS",
            "INSPECTION_PHOTO"
        )

        val creates = queue.filter { it.operationType == "CREATE" }
        for (entityType in createOrder) {
            creates.filter { it.entityType == entityType }.forEach { op ->
                processCreate(op)
            }
        }

        val updates = queue.filter { it.operationType == "UPDATE" }
        updates.forEach { op -> processUpdate(op) }

        val deletes = queue.filter { it.operationType == "DELETE" }
        deletes.forEach { op -> processDelete(op) }

        val completes = queue.filter { it.operationType == "COMPLETE" }
        completes.forEach { op -> processTaskAction(op, complete = true) }

        val uncompletes = queue.filter { it.operationType == "UNCOMPLETE" }
        uncompletes.forEach { op -> processTaskAction(op, complete = false) }

        if (syncQueueDao.getPendingCount() == 0) {
            sessionManager.markServerSyncNow()
        }
    }

    private suspend fun processCreate(op: SyncQueueEntity) {
        if (op.retryCount >= MAX_RETRIES) {
            markOpFailed(op)
            return
        }
        try {
            when (op.entityType) {
                "APIARY" -> {
                    val payload = parsePayload(op, createApiaryAdapter) ?: return
                    val response = apiaryApi.createApiary(payload)
                    successfulBodyOrRetry(op, response)?.let { server ->
                        apiaryDao.getByLocalId(op.entityLocalId)?.let {
                            apiaryDao.update(it.copy(serverId = server.id, syncStatus = SyncStatus.SYNCED))
                        }
                        syncQueueDao.deleteById(op.id)
                        Log.d(TAG, "Synced CREATE APIARY ${op.entityLocalId} → ${server.id}")
                    }
                }
                "HIVE" -> {
                    val hiveEntity = hiveDao.getByLocalId(op.entityLocalId) ?: run {
                        // Entity deleted locally before sync; drop the queue entry.
                        syncQueueDao.deleteById(op.id)
                        return
                    }
                    val apiaryServerId = resolveApiaryServerId(hiveEntity.apiaryLocalId) ?: run {
                        Log.d(TAG, "Deferring HIVE CREATE ${op.id} — parent apiary not yet synced")
                        return
                    }
                    val payload = parsePayload(op, createHiveAdapter) ?: return
                    val response = hiveApi.createHive(apiaryServerId, payload)
                    successfulBodyOrRetry(op, response)?.let { server ->
                        hiveDao.update(
                            hiveEntity.copy(
                                serverId = server.id,
                                apiaryServerId = server.apiaryId,
                                syncStatus = SyncStatus.SYNCED
                            )
                        )
                        syncQueueDao.deleteById(op.id)
                        Log.d(TAG, "Synced CREATE HIVE ${op.entityLocalId} → ${server.id}")
                    }
                }
                "TASK" -> {
                    val payload = parsePayload(op, createTaskAdapter) ?: return
                    // Defer when a parent id in the payload refers to a still-unsynced local entity.
                    val resolvedHiveId = payload.hiveId?.let { raw ->
                        resolveHiveServerId(raw) ?: run {
                            Log.d(TAG, "Deferring TASK CREATE ${op.id} — parent hive $raw not yet synced")
                            return
                        }
                    }
                    val resolvedApiaryId = payload.apiaryId?.let { raw ->
                        resolveApiaryServerId(raw) ?: run {
                            Log.d(TAG, "Deferring TASK CREATE ${op.id} — parent apiary $raw not yet synced")
                            return
                        }
                    }
                    val resolvedPayload = payload.copy(hiveId = resolvedHiveId, apiaryId = resolvedApiaryId)
                    val response = taskApi.createTask(resolvedPayload)
                    successfulBodyOrRetry(op, response)?.let { server ->
                        taskDao.getByLocalId(op.entityLocalId)?.let {
                            taskDao.update(it.copy(serverId = server.id, syncStatus = SyncStatus.SYNCED))
                        }
                        syncQueueDao.deleteById(op.id)
                    }
                }
                "TREATMENT" -> {
                    val entity = treatmentDao.getByLocalId(op.entityLocalId) ?: run {
                        syncQueueDao.deleteById(op.id); return
                    }
                    val payload = parsePayload(op, createTreatmentAdapter) ?: return
                    val resolvedHiveId = resolveHiveServerId(entity.hiveLocalId) ?: run {
                        Log.d(TAG, "Deferring TREATMENT CREATE ${op.id} — parent hive not yet synced")
                        return
                    }
                    val resolvedPayload = payload.copy(hiveId = resolvedHiveId)
                    val response = treatmentApi.createTreatment(resolvedPayload)
                    successfulBodyOrRetry(op, response)?.let { server ->
                        treatmentDao.update(
                            entity.copy(
                                serverId = server.id,
                                hiveServerId = server.hiveId,
                                syncStatus = SyncStatus.SYNCED
                            )
                        )
                        syncQueueDao.deleteById(op.id)
                    }
                }
                "EXTRACTION" -> {
                    val entity = extractionDao.getByLocalId(op.entityLocalId) ?: run {
                        syncQueueDao.deleteById(op.id); return
                    }
                    val payload = parsePayload(op, createExtractionAdapter) ?: return
                    val resolvedHiveId = resolveHiveServerId(entity.hiveLocalId) ?: run {
                        Log.d(TAG, "Deferring EXTRACTION CREATE ${op.id} — parent hive not yet synced")
                        return
                    }
                    val resolvedPayload = payload.copy(hiveId = resolvedHiveId)
                    val response = extractionApi.createExtraction(resolvedPayload)
                    successfulBodyOrRetry(op, response)?.let { server ->
                        extractionDao.update(
                            entity.copy(
                                serverId = server.id,
                                hiveServerId = server.hiveId,
                                syncStatus = SyncStatus.SYNCED
                            )
                        )
                        syncQueueDao.deleteById(op.id)
                    }
                }
                "INSPECTION" -> {
                    val entity = inspectionDao.getByLocalId(op.entityLocalId) ?: run {
                        syncQueueDao.deleteById(op.id); return
                    }
                    val payload = parsePayload(op, createInspectionAdapter) ?: return
                    val resolvedHiveId = resolveHiveServerId(entity.hiveLocalId) ?: run {
                        Log.d(TAG, "Deferring INSPECTION CREATE ${op.id} — parent hive not yet synced")
                        return
                    }
                    val resolvedPayload = payload.copy(hiveId = resolvedHiveId)
                    val response = inspectionApi.createInspection(resolvedPayload)
                    successfulBodyOrRetry(op, response)?.let { server ->
                        inspectionDao.update(
                            entity.copy(
                                serverId = server.id,
                                hiveServerId = server.hiveId,
                                syncStatus = SyncStatus.SYNCED
                            )
                        )
                        // Keep any locally-cached AI analysis findable after the server
                        // refetch replaces this row with one keyed by serverId.
                        inspectionAiAnalysisDao.bindServerId(op.entityLocalId, server.id)
                        inspectionPhotoDao.bindInspectionServerId(op.entityLocalId, server.id)
                        syncQueueDao.deleteById(op.id)
                        Log.d(TAG, "Synced CREATE INSPECTION ${op.entityLocalId} → ${server.id}")
                    }
                }
                "INSPECTION_AI_ANALYSIS" -> {
                    val wrapper = parsePayload(op, addAiAnalysisAdapter) ?: return
                    val inspection = inspectionDao.getByLocalId(wrapper.inspectionLocalId) ?: run {
                        // Parent inspection deleted locally before sync; drop the queued analysis.
                        syncQueueDao.deleteById(op.id)
                        return
                    }
                    val inspectionServerId = inspection.serverId ?: run {
                        Log.d(TAG, "Deferring INSPECTION_AI_ANALYSIS CREATE ${op.id} - parent inspection not yet synced")
                        return
                    }
                    val response = inspectionApi.saveAiAnalysis(inspectionServerId, wrapper.request)
                    if (response.isSuccessful && response.body() != null) {
                        syncQueueDao.deleteById(op.id)
                        Log.d(TAG, "Synced CREATE INSPECTION_AI_ANALYSIS ${op.entityLocalId} for inspection $inspectionServerId")
                    } else {
                        bumpRetry(op)
                    }
                }
                "INSPECTION_PHOTO" -> {
                    val wrapper = parsePayload(op, addPhotoAdapter) ?: return
                    val inspection = inspectionDao.getByLocalId(wrapper.inspectionLocalId) ?: run {
                        // Parent inspection deleted locally before sync — drop the queued photo.
                        syncQueueDao.deleteById(op.id)
                        return
                    }
                    val inspectionServerId = inspection.serverId ?: run {
                        Log.d(TAG, "Deferring INSPECTION_PHOTO CREATE ${op.id} — parent inspection not yet synced")
                        return
                    }
                    val response = inspectionApi.addPhoto(inspectionServerId, wrapper.request)
                    successfulBodyOrRetry(op, response)?.let { server ->
                        inspectionPhotoDao.getById(op.entityLocalId)?.let {
                            inspectionPhotoDao.update(
                                it.copy(
                                    serverId = server.id,
                                    inspectionServerId = server.inspectionId,
                                    photoUrl = server.photoUrl,
                                    description = server.description,
                                    createdAt = server.createdAt,
                                    syncStatus = SyncStatus.SYNCED
                                )
                            )
                        }
                        syncQueueDao.deleteById(op.id)
                        Log.d(TAG, "Synced CREATE INSPECTION_PHOTO ${op.entityLocalId} for inspection $inspectionServerId")
                    }
                }
            }
        } catch (e: IOException) {
            Log.d(TAG, "Backend unreachable for CREATE ${op.entityType} ${op.id}; will retry later")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing CREATE ${op.entityType}: ${e.message}", e)
            bumpRetry(op)
        }
    }

    private suspend fun processUpdate(op: SyncQueueEntity) {
        if (op.retryCount >= MAX_RETRIES) {
            markOpFailed(op)
            return
        }
        // USER_PROFILE has no entityServerId — the JWT identifies the user on the
        // backend. Handle it before the serverId guard.
        if (op.entityType == "USER_PROFILE") {
            try {
                val payload = parsePayload(op, updateProfileAdapter) ?: return
                val response = authApi.updateProfile(payload)
                successfulBodyOrRetry(op, response)?.let { userProfile ->
                    sessionManager.saveUserProfileJson(userProfileAdapter.toJson(userProfile))
                    syncQueueDao.deleteById(op.id)
                    sessionManager.markServerSyncNow()
                    Log.d(TAG, "Synced UPDATE USER_PROFILE")
                }
            } catch (e: IOException) {
                Log.d(TAG, "Backend unreachable for UPDATE USER_PROFILE; will retry later")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing UPDATE USER_PROFILE: ${e.message}", e)
                bumpRetry(op)
            }
            return
        }
        val serverId = op.entityServerId ?: resolveEntityServerId(op.entityType, op.entityLocalId) ?: run {
            // Update queued before the CREATE was synced; wait for the CREATE pass.
            Log.d(TAG, "Deferring UPDATE ${op.entityType} ${op.id} — serverId not resolved yet")
            return
        }
        try {
            when (op.entityType) {
                "APIARY" -> {
                    val payload = parsePayload(op, updateApiaryAdapter) ?: return
                    val response = apiaryApi.updateApiary(serverId, payload)
                    if (response.isSuccessful) {
                        apiaryDao.getByLocalId(op.entityLocalId)?.let {
                            apiaryDao.update(it.copy(syncStatus = SyncStatus.SYNCED))
                        }
                        syncQueueDao.deleteById(op.id)
                    } else {
                        bumpRetry(op)
                    }
                }
                "HIVE" -> {
                    val payload = parsePayload(op, updateHiveAdapter) ?: return
                    val response = hiveApi.updateHive(serverId, payload)
                    if (response.isSuccessful) {
                        hiveDao.getByLocalId(op.entityLocalId)?.let {
                            hiveDao.update(it.copy(syncStatus = SyncStatus.SYNCED))
                        }
                        syncQueueDao.deleteById(op.id)
                    } else {
                        bumpRetry(op)
                    }
                }
                "TASK" -> {
                    val payload = parsePayload(op, updateTaskAdapter) ?: return
                    val response = taskApi.updateTask(serverId, payload)
                    if (response.isSuccessful) {
                        taskDao.getByLocalId(op.entityLocalId)?.let {
                            taskDao.update(it.copy(syncStatus = SyncStatus.SYNCED))
                        }
                        syncQueueDao.deleteById(op.id)
                    } else {
                        bumpRetry(op)
                    }
                }
                "TREATMENT" -> {
                    val payload = parsePayload(op, updateTreatmentAdapter) ?: return
                    val response = treatmentApi.updateTreatment(serverId, payload)
                    if (response.isSuccessful) {
                        treatmentDao.getByLocalId(op.entityLocalId)?.let {
                            treatmentDao.update(it.copy(syncStatus = SyncStatus.SYNCED))
                        }
                        syncQueueDao.deleteById(op.id)
                    } else {
                        bumpRetry(op)
                    }
                }
                "EXTRACTION" -> {
                    val payload = parsePayload(op, updateExtractionAdapter) ?: return
                    val response = extractionApi.updateExtraction(serverId, payload)
                    if (response.isSuccessful) {
                        extractionDao.getByLocalId(op.entityLocalId)?.let {
                            extractionDao.update(it.copy(syncStatus = SyncStatus.SYNCED))
                        }
                        syncQueueDao.deleteById(op.id)
                    } else {
                        bumpRetry(op)
                    }
                }
                "INSPECTION" -> {
                    val payload = parsePayload(op, updateInspectionAdapter) ?: return
                    val response = inspectionApi.updateInspection(serverId, payload)
                    if (response.isSuccessful) {
                        inspectionDao.getByLocalId(op.entityLocalId)?.let {
                            inspectionDao.update(it.copy(syncStatus = SyncStatus.SYNCED))
                        }
                        syncQueueDao.deleteById(op.id)
                    } else {
                        bumpRetry(op)
                    }
                }
                "INSPECTION_PHOTO" -> {
                    val payload = parsePayload(op, updatePhotoAdapter) ?: return
                    val photoServerId = inspectionPhotoDao.getById(op.entityLocalId)?.serverId ?: serverId
                    val response = inspectionApi.updatePhoto(photoServerId, payload)
                    successfulBodyOrRetry(op, response)?.let { server ->
                        inspectionPhotoDao.getById(op.entityLocalId)?.let {
                            inspectionPhotoDao.update(
                                it.copy(
                                    serverId = server.id,
                                    photoUrl = server.photoUrl,
                                    description = server.description,
                                    syncStatus = SyncStatus.SYNCED
                                )
                            )
                        }
                        syncQueueDao.deleteById(op.id)
                    }
                }
            }
        } catch (e: IOException) {
            Log.d(TAG, "Backend unreachable for UPDATE ${op.entityType} ${op.id}; will retry later")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing UPDATE ${op.entityType}: ${e.message}", e)
            bumpRetry(op)
        }
    }

    private suspend fun processDelete(op: SyncQueueEntity) {
        if (op.retryCount >= MAX_RETRIES) {
            markOpFailed(op)
            return
        }
        val serverId = op.entityServerId
        try {
            if (serverId == null) {
                // Never synced to server — just clean up local queue entry
                syncQueueDao.deleteById(op.id)
                return
            }
            val response = when (op.entityType) {
                "APIARY" -> apiaryApi.deleteApiary(serverId)
                "HIVE" -> hiveApi.deleteHive(serverId)
                "TASK" -> taskApi.deleteTask(serverId)
                "TREATMENT" -> treatmentApi.deleteTreatment(serverId)
                "EXTRACTION" -> extractionApi.deleteExtraction(serverId)
                "INSPECTION" -> inspectionApi.deleteInspection(serverId)
                "INSPECTION_PHOTO" -> {
                    val photoServerId = inspectionPhotoDao.getById(op.entityLocalId)?.serverId ?: serverId
                    inspectionApi.deletePhoto(photoServerId)
                }
                else -> return
            }
            if (response.isSuccessful) {
                deleteLocalEntity(op.entityType, op.entityLocalId)
                syncQueueDao.deleteById(op.id)
            } else {
                bumpRetry(op)
            }
        } catch (e: IOException) {
            Log.d(TAG, "Backend unreachable for DELETE ${op.entityType} ${op.id}; will retry later")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing DELETE ${op.entityType}: ${e.message}", e)
            bumpRetry(op)
        }
    }

    private suspend fun deleteLocalEntity(entityType: String, localId: String) {
        when (entityType) {
            "APIARY" -> apiaryDao.deleteByLocalId(localId)
            "HIVE" -> hiveDao.deleteByLocalId(localId)
            "TASK" -> taskDao.deleteByLocalId(localId)
            "TREATMENT" -> treatmentDao.deleteByLocalId(localId)
            "EXTRACTION" -> extractionDao.deleteByLocalId(localId)
            "INSPECTION" -> inspectionDao.deleteByLocalId(localId)
            "INSPECTION_PHOTO" -> inspectionPhotoDao.deleteById(localId)
        }
    }

    private suspend fun processTaskAction(op: SyncQueueEntity, complete: Boolean) {
        if (op.retryCount >= MAX_RETRIES) {
            markOpFailed(op)
            return
        }
        val serverId = op.entityServerId ?: resolveEntityServerId(op.entityType, op.entityLocalId) ?: run {
            // Task was created offline; defer until CREATE is synced.
            Log.d(TAG, "Deferring TASK action ${op.id} — serverId not resolved yet")
            return
        }
        try {
            val response = if (complete) taskApi.completeTask(serverId)
                           else taskApi.uncompleteTask(serverId)
            if (response.isSuccessful) {
                taskDao.getByLocalId(op.entityLocalId)?.let {
                    taskDao.update(it.copy(syncStatus = SyncStatus.SYNCED))
                }
                syncQueueDao.deleteById(op.id)
            } else {
                bumpRetry(op)
            }
        } catch (e: IOException) {
            Log.d(TAG, "Backend unreachable for TASK action ${op.id}; will retry later")
        } catch (e: Exception) {
            Log.e(TAG, "Error processing task action: ${e.message}", e)
            bumpRetry(op)
        }
    }

    private suspend fun resolveApiaryServerId(id: String): String? {
        // `id` may already be a server id (e.g., child created after parent synced), or a local id.
        return apiaryDao.getByLocalId(id)?.serverId
            ?: apiaryDao.getByServerId(id)?.serverId
    }

    private suspend fun resolveHiveServerId(id: String): String? {
        return hiveDao.getByLocalId(id)?.serverId
            ?: hiveDao.getByServerId(id)?.serverId
    }

    private suspend fun resolveEntityServerId(entityType: String, localId: String): String? =
        when (entityType) {
            "APIARY" -> apiaryDao.getByLocalId(localId)?.serverId
                ?: apiaryDao.getByServerId(localId)?.serverId
            "HIVE" -> hiveDao.getByLocalId(localId)?.serverId
                ?: hiveDao.getByServerId(localId)?.serverId
            "TASK" -> taskDao.getByLocalId(localId)?.serverId
            "TREATMENT" -> treatmentDao.getByLocalId(localId)?.serverId
            "EXTRACTION" -> extractionDao.getByLocalId(localId)?.serverId
            "INSPECTION" -> inspectionDao.getByLocalId(localId)?.serverId
            "INSPECTION_PHOTO" -> inspectionPhotoDao.getById(localId)?.serverId
            else -> null
        }

    /** Deserialize a queue payload; on corruption, log, drop the queue entry, and return null. */
    private suspend fun <T> parsePayload(op: SyncQueueEntity, adapter: JsonAdapter<T>): T? {
        return try {
            adapter.fromJson(op.payload) ?: run {
                Log.w(TAG, "Null payload after parse for op ${op.id} (${op.operationType} ${op.entityType}); dropping")
                syncQueueDao.deleteById(op.id)
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Malformed payload for op ${op.id} (${op.operationType} ${op.entityType}); dropping", e)
            syncQueueDao.deleteById(op.id)
            null
        }
    }

    private suspend fun <T> successfulBodyOrRetry(
        op: SyncQueueEntity,
        response: retrofit2.Response<T>
    ): T? {
        if (!response.isSuccessful) {
            bumpRetry(op)
            return null
        }

        val body = response.body()
        if (body == null) {
            Log.w(TAG, "Empty successful response for op ${op.id} (${op.operationType} ${op.entityType}); retrying")
            bumpRetry(op)
        }
        return body
    }

    /** Bump retry counter; if this attempt was the last allowed one, mark the entity SYNC_FAILED. */
    private suspend fun bumpRetry(op: SyncQueueEntity) {
        val next = op.retryCount + 1
        syncQueueDao.update(op.copy(retryCount = next))
        if (next >= MAX_RETRIES) {
            Log.w(TAG, "Op ${op.id} (${op.operationType} ${op.entityType}) exhausted retries — marking entity failed")
            markEntitySyncFailed(op)
        }
    }

    /** Called once when an op is first seen at/past MAX_RETRIES, to ensure the entity is flagged even if the queue row survived a restart. */
    private suspend fun markOpFailed(op: SyncQueueEntity) {
        markEntitySyncFailed(op)
    }

    private suspend fun markEntitySyncFailed(op: SyncQueueEntity) {
        when (op.entityType) {
            "APIARY" -> apiaryDao.getByLocalId(op.entityLocalId)?.let {
                apiaryDao.update(it.copy(syncStatus = SyncStatus.SYNC_FAILED))
            }
            "HIVE" -> hiveDao.getByLocalId(op.entityLocalId)?.let {
                hiveDao.update(it.copy(syncStatus = SyncStatus.SYNC_FAILED))
            }
            "TASK" -> taskDao.getByLocalId(op.entityLocalId)?.let {
                taskDao.update(it.copy(syncStatus = SyncStatus.SYNC_FAILED))
            }
            "TREATMENT" -> treatmentDao.getByLocalId(op.entityLocalId)?.let {
                treatmentDao.update(it.copy(syncStatus = SyncStatus.SYNC_FAILED))
            }
            "EXTRACTION" -> extractionDao.getByLocalId(op.entityLocalId)?.let {
                extractionDao.update(it.copy(syncStatus = SyncStatus.SYNC_FAILED))
            }
            "INSPECTION" -> inspectionDao.getByLocalId(op.entityLocalId)?.let {
                inspectionDao.update(it.copy(syncStatus = SyncStatus.SYNC_FAILED))
            }
        }
    }
}

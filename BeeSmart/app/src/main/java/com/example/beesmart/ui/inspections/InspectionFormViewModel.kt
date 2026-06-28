package com.example.beesmart.ui.inspections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beesmart.data.local.dao.InspectionAiAnalysisDao
import com.example.beesmart.data.local.entity.InspectionAiAnalysisEntity
import com.example.beesmart.data.repository.BroodAnalyzer
import com.example.beesmart.data.repository.HiveRepository
import com.example.beesmart.data.repository.InspectionRepository
import com.example.beesmart.data.repository.PresentationAiFallback
import com.example.beesmart.network.models.*
import com.example.beesmart.data.repository.Result
import com.example.beesmart.sync.SyncScheduler
import com.example.beesmart.utils.PhotoManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class InspectionFormViewModel @Inject constructor(
    private val inspectionRepository: InspectionRepository,
    private val hiveRepository: HiveRepository,
    private val photoManager: PhotoManager,
    private val analysisDao: InspectionAiAnalysisDao,
    private val moshi: Moshi,
    private val syncScheduler: SyncScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val rawCountsAdapter = moshi.adapter<Map<String, Int>>(
        Types.newParameterizedType(Map::class.java, String::class.java, Integer::class.javaObjectType)
    )
    private val cellDetectionsAdapter = moshi.adapter<List<CellDetection>>(
        Types.newParameterizedType(List::class.java, CellDetection::class.java)
    )

    /** Latest analysis to persist when the inspection is saved. */
    private var pendingAnalysis: AnalyzeCellsUiState.Success? = null

    private val _uiState = MutableStateFlow<InspectionFormUiState>(InspectionFormUiState.Idle)
    val uiState: StateFlow<InspectionFormUiState> = _uiState.asStateFlow()

    private val _hives = MutableStateFlow<List<HiveResponse>>(emptyList())
    val hives: StateFlow<List<HiveResponse>> = _hives.asStateFlow()

    private val _localPhotos = MutableStateFlow<List<PhotoItem.Local>>(emptyList())
    private val _remotePhotos = MutableStateFlow<List<PhotoItem.Remote>>(emptyList())

    val allPhotos: StateFlow<List<PhotoItem>> = combine(_localPhotos, _remotePhotos) { local, remote ->
        remote + local
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Aggregate analysis across all analyzed photos — this is what gets persisted
    // and what drives the smart suggestions.
    private val _cellsAnalysisState = MutableStateFlow<AnalyzeCellsUiState>(AnalyzeCellsUiState.Idle)
    val cellsAnalysisState: StateFlow<AnalyzeCellsUiState> = _cellsAnalysisState.asStateFlow()

    // Per-photo analysis results, keyed by PhotoItem.key. Lives only for the session
    // so the beekeeper can inspect each photo individually.
    private val _photoAnalyses = MutableStateFlow<Map<String, AnalyzeCellsUiState>>(emptyMap())
    val photoAnalyses: StateFlow<Map<String, AnalyzeCellsUiState>> = _photoAnalyses.asStateFlow()

    private var saveInProgress = false

    private val inspectionId: String? = savedStateHandle["inspectionId"]
    val isEditMode: Boolean = !inspectionId.isNullOrEmpty()

    init {
        loadHives()
        if (isEditMode) {
            loadInspectionDetails()
        }
    }

    private fun loadHives() {
        viewModelScope.launch {
            hiveRepository.getCachedAllHives()
                .takeIf { it.isNotEmpty() }
                ?.let { _hives.value = it }

            when (val result = hiveRepository.getAllHives()) {
                is Result.Success -> _hives.value = result.data
                else -> {}
            }
        }
    }

    private fun loadInspectionDetails() {
        inspectionId ?: return
        viewModelScope.launch {
            val cached = inspectionRepository.getCachedInspectionById(inspectionId)
            if (cached != null) {
                publishInspection(cached)
                return@launch
            }

            _uiState.value = InspectionFormUiState.Loading
            when (val result = inspectionRepository.getInspectionById(inspectionId)) {
                is Result.Success -> {
                    publishInspection(result.data)
                }
                is Result.Error -> {
                    _uiState.value = InspectionFormUiState.Error(result.message)
                    saveInProgress = false
                }
                else -> {
                    saveInProgress = false
                }
            }
        }
    }

    private suspend fun publishInspection(inspection: InspectionDetailResponse) {
        _uiState.value = InspectionFormUiState.LoadedData(inspection)
        _remotePhotos.value = inspection.photos.map { PhotoItem.Remote(it) }
        inspectionId?.let { loadSavedAnalysis(it) }
    }

    private suspend fun loadSavedAnalysis(id: String) {
        val saved = analysisDao.getLatestForInspection(id) ?: return
        val rawCounts = runCatching { rawCountsAdapter.fromJson(saved.rawCountsJson) }
            .getOrNull()
            ?: return
        val cellDetections = runCatching { cellDetectionsAdapter.fromJson(saved.cellDetectionsJson) }
            .getOrNull()
            ?: emptyList()
        val report = BroodAnalyzer.analyze(rawCounts, cellDetections)
        _cellsAnalysisState.value = AnalyzeCellsUiState.Success(
            results = rawCounts,
            report = report,
            message = saved.message,
            cellDetections = cellDetections
        )
    }

    fun processAndAddPhoto(rawFile: File) {
        viewModelScope.launch {
            try {
                val processedFile = withContext(Dispatchers.IO) {
                    photoManager.processPhoto(rawFile)
                }

                val newItem = PhotoItem.Local(
                    file = processedFile,
                    tempId = UUID.randomUUID().toString(),
                    description = null
                )

                _localPhotos.value = _localPhotos.value + newItem
                android.util.Log.d("PhotoCapture", "Photo added. Total local photos: ${_localPhotos.value.size}")
                android.util.Log.d("PhotoCapture", "Photo file exists: ${processedFile.exists()}, path: ${processedFile.absolutePath}")

            } catch (e: Exception) {
                _uiState.value = InspectionFormUiState.Error("Eroare procesare foto: ${e.message}")
            }
        }
    }

    fun removePhoto(item: PhotoItem) {
        when (item) {
            is PhotoItem.Local -> {
                _localPhotos.value = _localPhotos.value.filter { it.tempId != item.tempId }
                try { item.file.delete() } catch (e: Exception) {}
            }
            is PhotoItem.Remote -> {
                if (isEditMode) {
                    deleteRemotePhoto(item.photo.id)
                }
            }
        }
    }

    private fun deleteRemotePhoto(photoId: String) {
        viewModelScope.launch {
            when (inspectionRepository.enqueueDeletePhoto(photoId)) {
                is Result.Success -> {
                    syncScheduler.requestSync()
                    _remotePhotos.value = _remotePhotos.value.filter { it.photo.id != photoId }
                }
                is Result.Error -> _uiState.value = InspectionFormUiState.Error("Eroare ștergere foto")
                else -> {}
            }
        }
    }

    fun saveInspection(
        hiveId: String?,
        date: LocalDateTime,
        frames: Int?,
        brood: Int?,
        honey: Int?,
        pollen: Int?,
        queen: Boolean,
        eggs: Boolean,
        larvae: Boolean,
        queenCellsSeen: Boolean,
        queenCellsWithEggs: Boolean,
        beardingAtEntrance: Boolean,
        spaceNeeded: Boolean,
        broodPattern: String?,
        feedingGiven: Boolean,
        waterAvailable: Boolean,
        moistureOrMold: Boolean,
        deadBeesAtEntrance: Boolean,
        unusualBehavior: Boolean,
        temperament: String?,
        oldCombsToReplace: Int?,
        notes: String?
    ) {
        if (saveInProgress || _uiState.value is InspectionFormUiState.Loading) return

        if (hiveId == null) {
            _uiState.value = InspectionFormUiState.Error("Selectează un stup")
            return
        }

        saveInProgress = true
        viewModelScope.launch {
            _uiState.value = InspectionFormUiState.Loading

            val dateStr = date.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

            val result = if (isEditMode && inspectionId != null) {
                val req = UpdateInspectionRequest(
                    inspectionDate = dateStr,
                    temperature = null,
                    framesCount = frames,
                    broodFrames = brood,
                    honeyFrames = honey,
                    pollenFrames = pollen,
                    queenSeen = queen,
                    eggsSeen = eggs,
                    larvaeSeen = larvae,
                    queenCellsSeen = queenCellsSeen,
                    queenCellsWithEggs = queenCellsWithEggs,
                    beardingAtEntrance = beardingAtEntrance,
                    spaceNeeded = spaceNeeded,
                    broodPattern = broodPattern,
                    honeyCappingPercent = null,
                    feedingGiven = feedingGiven,
                    waterAvailable = waterAvailable,
                    moistureOrMold = moistureOrMold,
                    deadBeesAtEntrance = deadBeesAtEntrance,
                    unusualBehavior = unusualBehavior,
                    temperament = temperament,
                    oldCombsToReplace = oldCombsToReplace,
                    notes = notes
                )
                inspectionRepository.enqueueUpdateInspection(inspectionId, req)
            } else {
                val req = CreateInspectionRequest(
                    hiveId = hiveId,
                    inspectionDate = dateStr,
                    temperature = null,
                    framesCount = frames,
                    broodFrames = brood,
                    honeyFrames = honey,
                    pollenFrames = pollen,
                    queenSeen = queen,
                    eggsSeen = eggs,
                    larvaeSeen = larvae,
                    queenCellsSeen = queenCellsSeen,
                    queenCellsWithEggs = queenCellsWithEggs,
                    beardingAtEntrance = beardingAtEntrance,
                    spaceNeeded = spaceNeeded,
                    broodPattern = broodPattern,
                    honeyCappingPercent = null,
                    feedingGiven = feedingGiven,
                    waterAvailable = waterAvailable,
                    moistureOrMold = moistureOrMold,
                    deadBeesAtEntrance = deadBeesAtEntrance,
                    unusualBehavior = unusualBehavior,
                    temperament = temperament,
                    oldCombsToReplace = oldCombsToReplace,
                    notes = notes
                )
                inspectionRepository.enqueueCreateInspection(req)
            }

            when (result) {
                is Result.Success -> {
                    val targetId = if (isEditMode) inspectionId!! else result.data.id
                    // For a freshly-created inspection, attach the pending AI analysis
                    // (if any) using the new id so it can be retrieved on next view.
                    if (!isEditMode) {
                        pendingAnalysis?.let { persistAnalysis(targetId, it) }
                    }
                    uploadPendingPhotos(targetId)
                }
                is Result.Error -> _uiState.value = InspectionFormUiState.Error(result.message)
                else -> {}
            }
        }
    }

    private suspend fun uploadPendingPhotos(inspectionId: String) {
        val photosToUpload = _localPhotos.value
        android.util.Log.d("PhotoUpload", "Starting upload of ${photosToUpload.size} photos for inspection $inspectionId")

        if (photosToUpload.isEmpty()) {
            syncScheduler.requestSync()
            _uiState.value = InspectionFormUiState.Success("Salvat cu succes!")
            return
        }

        var failCount = 0

        photosToUpload.forEachIndexed { index, photo ->
            android.util.Log.d("PhotoUpload", "Processing photo ${index + 1}/${photosToUpload.size}")
            android.util.Log.d("PhotoUpload", "File exists: ${photo.file.exists()}, Size: ${photo.file.length()} bytes")

            try {
                val base64 = withContext(Dispatchers.IO) {
                    photoManager.imageToBase64(photo.file)
                }

                val base64Length = base64.length
                val dataUri = "data:image/jpeg;base64,$base64"
                android.util.Log.d("PhotoUpload", "Base64 length: $base64Length chars, Full URI length: ${dataUri.length} chars")

                val req = AddInspectionPhotoRequest(dataUri, photo.description)

                val uploadRes = inspectionRepository.enqueueAddPhoto(inspectionId, req)
                when (uploadRes) {
                    is Result.Success -> {
                        android.util.Log.d("PhotoUpload", "Photo ${index + 1} uploaded successfully! Photo ID: ${uploadRes.data.id}")
                        android.util.Log.d("PhotoUpload", "Response photoUrl length: ${uploadRes.data.photoUrl.length}")
                    }
                    is Result.Error -> {
                        failCount++
                        android.util.Log.e("PhotoUpload", "Photo ${index + 1} upload failed: ${uploadRes.message}")
                    }
                    is Result.Loading -> {
                        android.util.Log.w("PhotoUpload", "Unexpected Loading state")
                    }
                }
            } catch (e: Exception) {
                failCount++
                android.util.Log.e("PhotoUpload", "Photo ${index + 1} could not be prepared: ${e.message}", e)
            }
        }

        if (failCount == 0) {
            android.util.Log.d("PhotoUpload", "All photos uploaded successfully!")
            syncScheduler.requestSync()
            _uiState.value = InspectionFormUiState.Success("Salvat cu succes!")
        } else {
            android.util.Log.e("PhotoUpload", "$failCount photos failed to upload")
            syncScheduler.requestSync()
            _uiState.value = InspectionFormUiState.Success("Salvat, dar $failCount poze au eșuat.")
        }
    }

    /** Analyze a single photo (local or already-saved) and keep its result for in-session viewing. */
    fun analyzePhoto(item: PhotoItem) {
        if (_photoAnalyses.value[item.key] is AnalyzeCellsUiState.Loading) return
        viewModelScope.launch {
            analyzePhotoInternal(item)
            recomputeAggregate()
        }
    }

    /** Analyze the given (selected) photos, then aggregate. Re-runs even if already analyzed. */
    fun analyzePhotos(items: List<PhotoItem>) {
        viewModelScope.launch {
            val toRun = items.filter { _photoAnalyses.value[it.key] !is AnalyzeCellsUiState.Loading }
            if (toRun.isEmpty()) return@launch
            for (photo in toRun) analyzePhotoInternal(photo)
            recomputeAggregate()
        }
    }

    /** Analyze every attached photo (local + saved) that hasn't succeeded yet, then aggregate. */
    fun analyzeAllPhotos() {
        viewModelScope.launch {
            val photos = allPhotos.value
            if (photos.isEmpty()) {
                _cellsAnalysisState.value = AnalyzeCellsUiState.Error(
                    "Adaugă cel puțin o fotografie pentru analiză.",
                    isRetryable = false
                )
                return@launch
            }
            for (photo in photos) {
                if (_photoAnalyses.value[photo.key] is AnalyzeCellsUiState.Success) continue
                analyzePhotoInternal(photo)
            }
            recomputeAggregate()
        }
    }

    private suspend fun analyzePhotoInternal(item: PhotoItem) {
        val key = item.key
        _photoAnalyses.value = _photoAnalyses.value + (key to AnalyzeCellsUiState.Loading)

        val dataUri = try {
            resolveAnalysisDataUri(item)
        } catch (e: Exception) {
            android.util.Log.e("DeepBeeAI", "Could not prepare image for AI analysis: ${e.message}", e)
            _photoAnalyses.value = _photoAnalyses.value + (key to AnalyzeCellsUiState.Error(
                "Fotografia nu poate fi pregătită pentru analiză.",
                isRetryable = false
            ))
            return
        }

        val request = AnalyzeCellsRequest(imageBase64 = dataUri)
        val newState: AnalyzeCellsUiState = when (val result = inspectionRepository.analyzeCells(request)) {
            is Result.Success -> {
                val response = result.data
                if (response.isReliableSuccess() || response.isUsableUncertain() || response.isDemoFallback()) {
                    val report = BroodAnalyzer.analyze(response.results, response.cellDetections)
                    AnalyzeCellsUiState.Success(
                        results = response.results,
                        report = report,
                        status = response.status,
                        message = response.message,
                        cellDetections = response.cellDetections
                    )
                } else {
                    AnalyzeCellsUiState.Error(
                        analyzeCellsErrorMessage(response.status, response.message),
                        isRetryable = false
                    )
                }
            }
            is Result.Error -> mapAnalyzeError(result)
            is Result.Loading -> AnalyzeCellsUiState.Loading
        }
        _photoAnalyses.value = _photoAnalyses.value + (key to newState)
    }

    private suspend fun resolveAnalysisDataUri(item: PhotoItem): String = when (item) {
        is PhotoItem.Local -> {
            val base64 = withContext(Dispatchers.IO) { photoManager.imageToAnalysisBase64(item.file) }
            "data:image/jpeg;base64,$base64"
        }
        is PhotoItem.Remote -> {
            val url = item.photo.photoUrl
            if (url.startsWith("data:")) url else "data:image/jpeg;base64,$url"
        }
    }

    /**
     * Combine all successful per-photo analyses into a single inspection-level result
     * (summed cell counts + concatenated detections). This aggregate is what gets
     * persisted on the server and what feeds the smart suggestions.
     */
    private suspend fun recomputeAggregate() {
        val successes = _photoAnalyses.value.values.filterIsInstance<AnalyzeCellsUiState.Success>()
        if (successes.isEmpty()) {
            _cellsAnalysisState.value = AnalyzeCellsUiState.Idle
            pendingAnalysis = null
            return
        }

        val mergedCounts = mutableMapOf<String, Int>()
        val mergedDetections = mutableListOf<CellDetection>()
        successes.forEach { s ->
            s.results.forEach { (cls, count) -> mergedCounts[cls] = (mergedCounts[cls] ?: 0) + count }
            mergedDetections.addAll(s.cellDetections)
        }

        val report = BroodAnalyzer.analyze(mergedCounts, mergedDetections)
        val photoLabel = if (successes.size == 1) "o poză" else "${successes.size} poze"
        val hasDemoFallback = successes.any {
            it.status.equals(PresentationAiFallback.STATUS, ignoreCase = true)
        }
        val aggregate = AnalyzeCellsUiState.Success(
            results = mergedCounts,
            report = report,
            status = if (hasDemoFallback) PresentationAiFallback.STATUS else "success",
            message = if (hasDemoFallback) {
                "Rezultat demonstrativ agregat din $photoLabel; nu se salveaza ca analiza reala."
            } else {
                "Agregat din $photoLabel analizate"
            },
            cellDetections = mergedDetections
        )
        _cellsAnalysisState.value = aggregate
        pendingAnalysis = aggregate.takeIf { it.isPersistableAnalysis() }

        // In edit mode persist immediately; for a new inspection we wait until save.
        if (isEditMode && inspectionId != null && aggregate.isPersistableAnalysis()) {
            persistAnalysis(inspectionId, aggregate)
        }
    }

    private suspend fun persistAnalysis(inspectionLocalId: String, state: AnalyzeCellsUiState.Success) {
        if (!state.isPersistableAnalysis()) {
            return
        }
        val json = rawCountsAdapter.toJson(state.results)
        val cellDetectionsJson = cellDetectionsAdapter.toJson(state.cellDetections)
        analysisDao.insert(
            InspectionAiAnalysisEntity(
                inspectionLocalId = inspectionLocalId,
                inspectionServerId = null,
                rawCountsJson = json,
                cellDetectionsJson = cellDetectionsJson,
                message = state.message,
                computedAt = System.currentTimeMillis()
            )
        )

        val request = SaveInspectionAiAnalysisRequest(
            results = state.results,
            status = state.status,
            message = state.message,
            cellDetections = state.cellDetections
        )
        when (val result = inspectionRepository.saveAiAnalysis(inspectionLocalId, request)) {
            is Result.Error -> android.util.Log.w("DeepBeeAI", "AI analysis saved locally but not on server: ${result.message}")
            else -> {}
        }
    }

    private fun AnalyzeCellsResponse.hasPositiveCells(): Boolean =
        results.values.any { it > 0 }

    private fun AnalyzeCellsResponse.isReliableSuccess(): Boolean =
        status.equals("success", ignoreCase = true) && hasPositiveCells()

    private fun AnalyzeCellsResponse.isUsableUncertain(): Boolean =
        status.equals("uncertain_analysis", ignoreCase = true) && hasPositiveCells()

    private fun AnalyzeCellsResponse.isDemoFallback(): Boolean =
        status.equals(PresentationAiFallback.STATUS, ignoreCase = true) && hasPositiveCells()

    private fun AnalyzeCellsUiState.Success.isPersistableAnalysis(): Boolean =
        status.equals("success", ignoreCase = true) && results.values.any { it > 0 }

    private fun mapAnalyzeError(error: Result.Error): AnalyzeCellsUiState.Error {
        return when (error.code) {
            400 -> AnalyzeCellsUiState.Error("Imaginea nu este validă. Încearcă altă fotografie.")
            401 -> AnalyzeCellsUiState.Error("Sesiune expirată. Reautentifică-te.")
            502 -> AnalyzeCellsUiState.Error(
                "Serviciul AI este indisponibil momentan. Încearcă din nou.",
                isRetryable = true
            )
            504 -> AnalyzeCellsUiState.Error(
                "Analiza dureaza prea mult pentru fotografia curenta. Incearca din nou sau refotografiaza rama mai aproape si mai clar.",
                isRetryable = true
            )
            else -> AnalyzeCellsUiState.Error(
                "A apărut o eroare la analiză. Încearcă din nou.",
                isRetryable = error.code == null
            )
        }
    }

    fun resetState() {
        saveInProgress = false
        _uiState.value = InspectionFormUiState.Idle
    }
}

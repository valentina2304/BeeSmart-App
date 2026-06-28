package com.example.beesmart.ui.inspections

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beesmart.data.local.dao.InspectionAiAnalysisDao
import com.example.beesmart.data.repository.BroodAnalyzer
import com.example.beesmart.data.repository.InspectionRepository
import com.example.beesmart.network.models.CellDetection
import com.example.beesmart.network.models.UpdateInspectionPhotoRequest
import com.example.beesmart.data.repository.Result
import com.example.beesmart.sync.SyncScheduler
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InspectionDetailViewModel @Inject constructor(
    private val repository: InspectionRepository,
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

    private val _uiState = MutableStateFlow<InspectionDetailUiState>(InspectionDetailUiState.Loading)
    val uiState: StateFlow<InspectionDetailUiState> = _uiState.asStateFlow()

    private val _aiAnalysis = MutableStateFlow<AnalyzeCellsUiState.Success?>(null)
    val aiAnalysis: StateFlow<AnalyzeCellsUiState.Success?> = _aiAnalysis.asStateFlow()

    private val inspectionId: String = checkNotNull(savedStateHandle["inspectionId"])
    private var loadDetailsJob: Job? = null

    init {
        loadDetails()
    }

    fun loadDetails(forceRefresh: Boolean = false) {
        if (loadDetailsJob?.isActive == true && !forceRefresh) return
        if (forceRefresh) loadDetailsJob?.cancel()

        loadDetailsJob = viewModelScope.launch {
            repository.getCachedInspectionById(inspectionId)?.let { cached ->
                publishInspection(cached)
            } ?: run {
                if (_uiState.value !is InspectionDetailUiState.Success) {
                    _uiState.value = InspectionDetailUiState.Loading
                }
            }

            when (val result = repository.getInspectionById(inspectionId)) {
                is Result.Success -> publishInspection(result.data)
                is Result.Error -> {
                    if (_uiState.value !is InspectionDetailUiState.Success) {
                        _uiState.value = InspectionDetailUiState.Error(result.message)
                    }
                }
                else -> {}
            }
        }
    }

    private suspend fun publishInspection(inspection: com.example.beesmart.network.models.InspectionDetailResponse) {
        Log.d("InspectionDetail", "Loaded inspection with ${inspection.photos.size} photos")
        inspection.photos.forEachIndexed { index, photo ->
            val urlPreview = if (photo.photoUrl.length > 100) {
                "${photo.photoUrl.substring(0, 100)}... (${photo.photoUrl.length} chars)"
            } else {
                photo.photoUrl
            }
            Log.d("InspectionDetail", "Photo $index: ID=${photo.id}, URL=$urlPreview")
        }
        _uiState.value = InspectionDetailUiState.Success(inspection)
        loadSavedAnalysis(inspectionId)
    }

    private suspend fun loadSavedAnalysis(id: String) {
        val saved = analysisDao.getLatestForInspection(id)
        if (saved == null) {
            _aiAnalysis.value = null
            return
        }
        val rawCounts = runCatching { rawCountsAdapter.fromJson(saved.rawCountsJson) }.getOrNull()
        if (rawCounts == null) {
            _aiAnalysis.value = null
            return
        }
        val cellDetections = runCatching { cellDetectionsAdapter.fromJson(saved.cellDetectionsJson) }
            .getOrNull() ?: emptyList()
        val report = BroodAnalyzer.analyze(rawCounts, cellDetections)
        _aiAnalysis.value = AnalyzeCellsUiState.Success(
            results = rawCounts,
            report = report,
            message = saved.message,
            cellDetections = cellDetections
        )
    }

    fun deletePhoto(photoId: String) {
        viewModelScope.launch {
            when (val result = repository.enqueueDeletePhoto(photoId)) {
                is Result.Success -> {
                    syncScheduler.requestSync()
                    loadDetails(forceRefresh = true)
                }
                is Result.Error -> _uiState.value = InspectionDetailUiState.Error("Eroare la ștergerea fotografiei: ${result.message}")
                else -> {}
            }
        }
    }

    fun updatePhotoDescription(photoId: String, description: String?) {
        viewModelScope.launch {
            val request = UpdateInspectionPhotoRequest(description)
            when (val result = repository.enqueueUpdatePhoto(photoId, request)) {
                is Result.Success -> {
                    syncScheduler.requestSync()
                    loadDetails(forceRefresh = true)
                }
                is Result.Error -> _uiState.value = InspectionDetailUiState.Error("Eroare la actualizare: ${result.message}")
                else -> {}
            }
        }
    }
}

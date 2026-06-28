package com.example.beesmart.ui.inspections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beesmart.data.repository.InspectionRepository
import com.example.beesmart.data.repository.Result
import com.example.beesmart.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InspectionListViewModel @Inject constructor(
    private val repository: InspectionRepository,
    private val syncScheduler: SyncScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow<InspectionListUiState>(InspectionListUiState.Loading)
    val uiState: StateFlow<InspectionListUiState> = _uiState.asStateFlow()

    private val apiaryId: String? = savedStateHandle["apiaryId"]
    private val hiveId: String? = savedStateHandle["hiveId"]
    private var loadInspectionsJob: Job? = null
    private var observeInspectionsJob: Job? = null

    init {
        observeCachedInspections()
        loadInspections()
    }

    private fun observeCachedInspections() {
        observeInspectionsJob?.cancel()
        observeInspectionsJob = viewModelScope.launch {
            val flow = when {
                hiveId != null -> repository.observeCachedInspectionsByHiveId(hiveId)
                apiaryId != null -> repository.observeCachedInspectionsByApiaryId(apiaryId)
                else -> repository.observeCachedAllInspections()
            }
            flow.collect { inspections ->
                _uiState.value = InspectionListUiState.Success(inspections)
            }
        }
    }

    fun loadInspections(forceRefresh: Boolean = false) {
        if (loadInspectionsJob?.isActive == true && !forceRefresh) return
        if (forceRefresh) loadInspectionsJob?.cancel()

        loadInspectionsJob = viewModelScope.launch {
            val cached = when {
                hiveId != null -> repository.getCachedInspectionsByHiveId(hiveId)
                apiaryId != null -> repository.getCachedInspectionsByApiaryId(apiaryId)
                else -> repository.getCachedAllInspections()
            }
            if (cached.isNotEmpty()) {
                _uiState.value = InspectionListUiState.Success(cached)
            } else if (_uiState.value !is InspectionListUiState.Success) {
                _uiState.value = InspectionListUiState.Loading
            }

            val result = when {
                hiveId != null -> repository.getInspectionsByHiveId(hiveId)
                apiaryId != null -> repository.getInspectionsByApiaryId(apiaryId)
                else -> repository.getAllInspections()
            }

            when (result) {
                is Result.Success -> _uiState.value = InspectionListUiState.Success(result.data)
                is Result.Error -> {
                    if (_uiState.value !is InspectionListUiState.Success) {
                        _uiState.value = InspectionListUiState.Error(result.message)
                    }
                }
                else -> {}
            }
        }
    }

    fun deleteInspection(inspectionId: String) {
        viewModelScope.launch {
            when (val result = repository.enqueueDeleteInspection(inspectionId)) {
                is Result.Success -> {
                    syncScheduler.requestSync()
                }
                is Result.Error -> {
                    _uiState.value = InspectionListUiState.Error(result.message)
                }
                else -> {}
            }
        }
    }
}

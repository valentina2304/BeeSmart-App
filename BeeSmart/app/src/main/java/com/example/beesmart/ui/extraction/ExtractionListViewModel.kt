package com.example.beesmart.ui.extraction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beesmart.data.repository.ExtractionRepository
import com.example.beesmart.data.repository.HiveRepository
import com.example.beesmart.data.repository.Result
import com.example.beesmart.network.models.HiveExtraction
import com.example.beesmart.network.models.HiveResponse
import com.example.beesmart.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ExtractionWithHive(
    val extraction: HiveExtraction,
    val hiveName: String?
)

data class ExtractionListUiState(
    val items: List<ExtractionWithHive> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class ExtractionListViewModel @Inject constructor(
    private val extractionRepository: ExtractionRepository,
    private val hiveRepository: HiveRepository,
    private val syncScheduler: SyncScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val hiveId: String? = savedStateHandle["hiveId"]

    private val _uiState = MutableStateFlow(ExtractionListUiState())
    val uiState: StateFlow<ExtractionListUiState> = _uiState.asStateFlow()

    private var loadExtractionsJob: Job? = null
    private var observeExtractionsJob: Job? = null

    init {
        observeCachedExtractions()
        loadExtractions()
    }

    private fun observeCachedExtractions() {
        observeExtractionsJob?.cancel()
        observeExtractionsJob = viewModelScope.launch {
            val flow = if (hiveId != null) {
                extractionRepository.observeCachedExtractionsByHiveId(hiveId)
            } else {
                extractionRepository.observeCachedAllExtractions()
            }
            flow.collect { extractions ->
                val cachedHives = hiveRepository.getCachedAllHives()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        items = extractions.map { extraction ->
                            ExtractionWithHive(
                                extraction,
                                cachedHives.firstOrNull { hive -> hive.id == extraction.hiveId }?.name
                            )
                        }
                    )
                }
            }
        }
    }

    fun loadExtractions(forceRefresh: Boolean = false) {
        if (loadExtractionsJob?.isActive == true && !forceRefresh) return
        if (forceRefresh) loadExtractionsJob?.cancel()

        loadExtractionsJob = viewModelScope.launch {
            val cachedHives = hiveRepository.getCachedAllHives()
            val cachedExtractions = if (hiveId != null) {
                extractionRepository.getCachedExtractionsByHiveId(hiveId)
            } else {
                extractionRepository.getCachedAllExtractions()
            }
            if (cachedExtractions.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        items = cachedExtractions.map { extraction ->
                            ExtractionWithHive(extraction, cachedHives.firstOrNull { hive -> hive.id == extraction.hiveId }?.name)
                        }
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = it.items.isEmpty(), error = null) }
            }

            val hivesById: Map<String, HiveResponse> = when (val r = hiveRepository.getAllHives()) {
                is Result.Success -> r.data.associateBy { it.id }
                else -> cachedHives.associateBy { it.id }
            }

            val result = if (hiveId != null) {
                extractionRepository.getExtractionsByHiveId(hiveId)
            } else {
                extractionRepository.getAllExtractions()
            }

            when (result) {
                is Result.Success -> {
                    val items = result.data.map { e ->
                        ExtractionWithHive(e, hivesById[e.hiveId]?.name)
                    }
                    _uiState.update { it.copy(isLoading = false, items = items) }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun deleteExtraction(extractionId: String) {
        viewModelScope.launch {
            when (val result = extractionRepository.enqueueDeleteExtraction(extractionId)) {
                is Result.Success -> syncScheduler.requestSync()
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
                is Result.Loading -> {}
            }
        }
    }
}

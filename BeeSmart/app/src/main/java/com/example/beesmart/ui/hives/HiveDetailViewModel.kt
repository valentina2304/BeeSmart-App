package com.example.beesmart.ui.hives

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beesmart.BuildConfig
import com.example.beesmart.data.repository.HiveRepository
import com.example.beesmart.data.repository.InspectionRepository
import com.example.beesmart.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HiveDetailViewModel @Inject constructor(
    private val repository: HiveRepository,
    private val inspectionRepository: InspectionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val hiveId: String = checkNotNull(savedStateHandle["hiveId"])
    private val qrDeepLink = "${BuildConfig.DEEP_LINK_SCHEME}://${BuildConfig.DEEP_LINK_HOST}/hive/$hiveId"

    private val _uiState = MutableStateFlow<HiveDetailUiState>(HiveDetailUiState.Loading)
    val uiState: StateFlow<HiveDetailUiState> = _uiState.asStateFlow()
    private var loadHiveJob: Job? = null

    init {
        loadHive()
    }

    fun refresh() {
        loadHive(forceRefresh = true)
    }

    private fun loadHive(forceRefresh: Boolean = false) {
        if (loadHiveJob?.isActive == true && !forceRefresh) return
        if (forceRefresh) loadHiveJob?.cancel()

        loadHiveJob = viewModelScope.launch {
            repository.getCachedHiveById(hiveId)?.let { cached ->
                publishHive(cached, refreshStats = false)
            } ?: run {
                if (_uiState.value !is HiveDetailUiState.Success) {
                    _uiState.value = HiveDetailUiState.Loading
                }
            }

            when (val result = repository.getHiveById(hiveId)) {
                is Result.Success -> publishHive(result.data, refreshStats = true)
                is Result.Error -> {
                    val currentState = _uiState.value as? HiveDetailUiState.Success
                    if (currentState != null) {
                        _uiState.value = currentState.copy(
                            statsMessage = result.message.takeIf { currentState.aiAnalyses.isEmpty() }
                                ?: currentState.statsMessage,
                            isStatsLoading = false
                        )
                    } else {
                        _uiState.value = HiveDetailUiState.Error(result.message)
                    }
                }
                else -> {}
            }
        }
    }

    private suspend fun publishHive(
        hive: com.example.beesmart.network.models.HiveResponse,
        refreshStats: Boolean
    ) {
        val currentState = _uiState.value as? HiveDetailUiState.Success

        if (refreshStats) {
            val (inspections, statsResult) = coroutineScope {
                val inspectionsDeferred = async { inspectionRepository.getInspectionsByHiveId(hiveId) }
                val statsDeferred = async { inspectionRepository.getAiAnalysesByHiveId(hiveId) }
                val inspections = (inspectionsDeferred.await() as? Result.Success)?.data.orEmpty()
                inspections to statsDeferred.await()
            }
            publishHive(
                hive = hive,
                inspections = inspections,
                statsResult = statsResult,
                fallbackAnalyses = currentState?.aiAnalyses.orEmpty()
            )
        } else {
            val inspections = inspectionRepository.getCachedInspectionsByHiveId(hiveId)
            val cachedAnalyses = inspectionRepository.getCachedAiAnalysesByHiveId(hiveId)
            _uiState.value = HiveDetailUiState.Success(
                hive = hive.copy(
                    ultimaInspectie = inspections.firstOrNull()?.inspectionDate ?: hive.ultimaInspectie
                ),
                qrContent = qrDeepLink,
                aiAnalyses = cachedAnalyses,
                statsMessage = null,
                isStatsLoading = true
            )
        }
    }

    private fun publishHive(
        hive: com.example.beesmart.network.models.HiveResponse,
        inspections: List<com.example.beesmart.network.models.InspectionResponse>,
        statsResult: Result<List<com.example.beesmart.network.models.InspectionAiAnalysisResponse>>,
        fallbackAnalyses: List<com.example.beesmart.network.models.InspectionAiAnalysisResponse>
    ) {
        val latestInspectionDate = inspections.firstOrNull()?.inspectionDate
        val analyses = when (statsResult) {
            is Result.Success -> statsResult.data
            is Result.Error -> fallbackAnalyses
            else -> fallbackAnalyses
        }
        val message = (statsResult as? Result.Error)?.message
        _uiState.value = HiveDetailUiState.Success(
            hive = hive.copy(
                ultimaInspectie = latestInspectionDate ?: hive.ultimaInspectie
            ),
            qrContent = qrDeepLink,
            aiAnalyses = analyses,
            statsMessage = message,
            isStatsLoading = false
        )
    }
}

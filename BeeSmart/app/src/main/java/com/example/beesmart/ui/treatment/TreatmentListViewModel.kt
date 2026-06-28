package com.example.beesmart.ui.treatment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beesmart.data.repository.HiveRepository
import com.example.beesmart.data.repository.Result
import com.example.beesmart.data.repository.TreatmentRepository
import com.example.beesmart.network.models.HiveTreatment
import com.example.beesmart.network.models.HiveResponse
import com.example.beesmart.notifications.TreatmentNotificationScheduler
import com.example.beesmart.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TreatmentWithHive(
    val treatment: HiveTreatment,
    val hiveName: String?
)

data class TreatmentListUiState(
    val items: List<TreatmentWithHive> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TreatmentListViewModel @Inject constructor(
    private val treatmentRepository: TreatmentRepository,
    private val hiveRepository: HiveRepository,
    private val notificationScheduler: TreatmentNotificationScheduler,
    private val syncScheduler: SyncScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val hiveId: String? = savedStateHandle["hiveId"]

    private val _uiState = MutableStateFlow(TreatmentListUiState())
    val uiState: StateFlow<TreatmentListUiState> = _uiState.asStateFlow()

    private var loadTreatmentsJob: Job? = null
    private var observeTreatmentsJob: Job? = null

    init {
        observeCachedTreatments()
        loadTreatments()
    }

    private fun observeCachedTreatments() {
        observeTreatmentsJob?.cancel()
        observeTreatmentsJob = viewModelScope.launch {
            val flow = if (hiveId != null) {
                treatmentRepository.observeCachedTreatmentsByHiveId(hiveId)
            } else {
                treatmentRepository.observeCachedAllTreatments()
            }
            flow.collect { treatments ->
                val cachedHives = hiveRepository.getCachedAllHives()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        items = treatments.map { treatment ->
                            TreatmentWithHive(
                                treatment,
                                cachedHives.firstOrNull { hive -> hive.id == treatment.hiveId }?.name
                            )
                        }
                    )
                }
            }
        }
    }

    fun loadTreatments(forceRefresh: Boolean = false) {
        if (loadTreatmentsJob?.isActive == true && !forceRefresh) return
        if (forceRefresh) loadTreatmentsJob?.cancel()

        loadTreatmentsJob = viewModelScope.launch {
            val cachedHives = hiveRepository.getCachedAllHives()
            val cachedTreatments = if (hiveId != null) {
                treatmentRepository.getCachedTreatmentsByHiveId(hiveId)
            } else {
                treatmentRepository.getCachedAllTreatments()
            }
            if (cachedTreatments.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = null,
                        items = cachedTreatments.map { treatment ->
                            TreatmentWithHive(treatment, cachedHives.firstOrNull { hive -> hive.id == treatment.hiveId }?.name)
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

            val treatmentsResult = if (hiveId != null) {
                treatmentRepository.getTreatmentsByHiveId(hiveId)
            } else {
                treatmentRepository.getAllTreatments()
            }

            when (treatmentsResult) {
                is Result.Success -> {
                    val items = treatmentsResult.data.map { t ->
                        TreatmentWithHive(t, hivesById[t.hiveId]?.name)
                    }
                    _uiState.update { it.copy(isLoading = false, items = items) }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = treatmentsResult.message) }
                }
                is Result.Loading -> {}
            }
        }
    }

    fun deleteTreatment(treatmentId: String) {
        viewModelScope.launch {
            when (val result = treatmentRepository.enqueueDeleteTreatment(treatmentId)) {
                is Result.Success -> {
                    notificationScheduler.cancelTreatmentReminder(treatmentId)
                    syncScheduler.requestSync()
                }
                is Result.Error -> {
                    _uiState.update { it.copy(isLoading = false, error = result.message) }
                }
                is Result.Loading -> {}
            }
        }
    }
}

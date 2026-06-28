package com.example.beesmart.ui.treatment

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beesmart.data.repository.HiveRepository
import com.example.beesmart.data.repository.Result
import com.example.beesmart.data.repository.TreatmentRepository
import com.example.beesmart.network.models.CreateTreatmentRequest
import com.example.beesmart.network.models.HiveTreatment
import com.example.beesmart.network.models.HiveResponse
import com.example.beesmart.network.models.TreatmentType
import com.example.beesmart.network.models.UpdateTreatmentRequest
import com.example.beesmart.notifications.TreatmentNotificationScheduler
import com.example.beesmart.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class TreatmentFormUiState(
    val treatmentId: String? = null,
    val selectedHiveId: String = "",
    val treatmentType: TreatmentType = TreatmentType.Other,
    val productName: String = "",
    val substance: String = "",
    val dosage: String = "",
    val notes: String = "",
    val treatmentDate: LocalDate = LocalDate.now(),
    val nextTreatmentDate: LocalDate? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class TreatmentFormViewModel @Inject constructor(
    private val treatmentRepository: TreatmentRepository,
    private val hiveRepository: HiveRepository,
    private val notificationScheduler: TreatmentNotificationScheduler,
    private val syncScheduler: SyncScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val preselectedHiveId: String? = savedStateHandle["hiveId"]
    private val treatmentId: String? = savedStateHandle["treatmentId"]
    val isEditMode: Boolean = !treatmentId.isNullOrEmpty()
    private var saveInProgress = false

    private val _uiState = MutableStateFlow(
        TreatmentFormUiState(
            treatmentId = treatmentId,
            selectedHiveId = preselectedHiveId ?: ""
        )
    )
    val uiState: StateFlow<TreatmentFormUiState> = _uiState.asStateFlow()

    private val _hives = MutableStateFlow<List<HiveResponse>>(emptyList())
    val hives: StateFlow<List<HiveResponse>> = _hives.asStateFlow()

    init {
        loadHives()
        if (isEditMode) loadTreatment(treatmentId!!)
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

    private fun loadTreatment(id: String) {
        viewModelScope.launch {
            val cached = treatmentRepository.getCachedTreatmentById(id)
            if (cached != null) {
                publishTreatment(cached)
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = treatmentRepository.getTreatmentById(id)) {
                is Result.Success -> {
                    publishTreatment(result.data)
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                Result.Loading -> {}
            }
        }
    }

    private fun publishTreatment(t: HiveTreatment) {
        val date = t.treatmentDate.toLocalDateOrNull() ?: LocalDate.now()
        _uiState.update { state ->
            state.copy(
                isLoading = false,
                selectedHiveId = t.hiveId,
                treatmentType = t.type,
                productName = t.productName,
                substance = t.substance ?: "",
                dosage = t.dosage ?: "",
                notes = t.notes ?: "",
                treatmentDate = date,
                nextTreatmentDate = t.nextTreatmentDate.toLocalDateOrNull()
            )
        }
    }

    fun onHiveSelected(hiveId: String) { _uiState.update { it.copy(selectedHiveId = hiveId) } }
    fun onDateChange(date: LocalDate) { _uiState.update { it.copy(treatmentDate = date) } }
    fun onNextTreatmentDateChange(date: LocalDate?) { _uiState.update { it.copy(nextTreatmentDate = date) } }
    fun onTypeChange(type: TreatmentType) { _uiState.update { it.copy(treatmentType = type) } }
    fun onProductNameChange(name: String) { _uiState.update { it.copy(productName = name) } }
    fun onSubstanceChange(s: String) { _uiState.update { it.copy(substance = s) } }
    fun onDosageChange(d: String) { _uiState.update { it.copy(dosage = d) } }
    fun onNotesChange(n: String) { _uiState.update { it.copy(notes = n) } }

    fun saveTreatment() {
        val state = _uiState.value
        if (saveInProgress || state.isLoading || state.isSuccess) return

        if (state.selectedHiveId.isBlank()) {
            _uiState.update { it.copy(error = "Selectează stupul") }
            return
        }
        if (state.productName.isBlank()) {
            _uiState.update { it.copy(error = "Numele produsului este obligatoriu") }
            return
        }
        saveInProgress = true
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            if (state.treatmentId == null) createTreatment(state) else updateTreatment(state)
        }
    }

    private suspend fun createTreatment(state: TreatmentFormUiState) {
        val request = CreateTreatmentRequest(
            hiveId = state.selectedHiveId,
            treatmentDate = state.treatmentDate.toApiTimestamp(),
            type = state.treatmentType,
            productName = state.productName,
            substance = state.substance.takeIf { it.isNotBlank() },
            dosage = state.dosage.takeIf { it.isNotBlank() },
            notes = state.notes.takeIf { it.isNotBlank() },
            nextTreatmentDate = state.nextTreatmentDate?.toApiTimestamp()
        )
        when (val result = treatmentRepository.enqueueCreateTreatment(request)) {
            is Result.Success -> {
                syncTreatmentReminder(result.data)
                syncScheduler.requestSync()
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            }
            is Result.Error -> {
                saveInProgress = false
                _uiState.update { it.copy(isLoading = false, error = result.message) }
            }
            Result.Loading -> {
                saveInProgress = false
            }
        }
    }

    private suspend fun updateTreatment(state: TreatmentFormUiState) {
        val request = UpdateTreatmentRequest(
            treatmentDate = state.treatmentDate.toApiTimestamp(),
            type = state.treatmentType,
            productName = state.productName,
            substance = state.substance.takeIf { it.isNotBlank() },
            dosage = state.dosage.takeIf { it.isNotBlank() },
            notes = state.notes.takeIf { it.isNotBlank() },
            nextTreatmentDate = state.nextTreatmentDate?.toApiTimestamp()
        )
        when (val result = treatmentRepository.enqueueUpdateTreatment(state.treatmentId!!, request)) {
            is Result.Success -> {
                notificationScheduler.cancelTreatmentReminder(state.treatmentId)
                syncTreatmentReminder(result.data)
                syncScheduler.requestSync()
                _uiState.update { it.copy(isLoading = false, isSuccess = true) }
            }
            is Result.Error -> {
                saveInProgress = false
                _uiState.update { it.copy(isLoading = false, error = result.message) }
            }
            Result.Loading -> {
                saveInProgress = false
            }
        }
    }

    private fun syncTreatmentReminder(treatment: com.example.beesmart.network.models.HiveTreatment) {
        notificationScheduler.cancelTreatmentReminder(treatment.id)
        treatment.nextTreatmentDate?.let { nextDate ->
            notificationScheduler.scheduleTreatmentReminder(
                treatment.id,
                treatment.productName,
                nextDate
            )
        }
    }

    private fun LocalDate.toApiTimestamp(): String =
        atStartOfDay(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    private fun String?.toLocalDateOrNull(): LocalDate? {
        if (this == null) return null
        return runCatching { ZonedDateTime.parse(this).toLocalDate() }
            .recoverCatching { OffsetDateTime.parse(this).toLocalDate() }
            .recoverCatching { LocalDate.parse(take(10)) }
            .getOrNull()
    }
}

package com.example.beesmart.ui.extraction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beesmart.data.repository.ExtractionRepository
import com.example.beesmart.data.repository.HiveRepository
import com.example.beesmart.data.repository.Result
import com.example.beesmart.network.models.CreateExtractionRequest
import com.example.beesmart.network.models.HiveExtraction
import com.example.beesmart.network.models.ExtractionType
import com.example.beesmart.network.models.HiveResponse
import com.example.beesmart.network.models.UpdateExtractionRequest
import com.example.beesmart.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ExtractionFormUiState(
    val extractionId: String? = null,
    val selectedHiveId: String = "",
    val extractionType: ExtractionType = ExtractionType.Honey,
    val quantity: String = "",
    val unit: String = "kg",
    val notes: String = "",
    val extractionDate: LocalDate = LocalDate.now(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val quantityError: String? = null,
    val unitError: String? = null,
    val isSuccess: Boolean = false
)

@HiltViewModel
class ExtractionFormViewModel @Inject constructor(
    private val extractionRepository: ExtractionRepository,
    private val hiveRepository: HiveRepository,
    private val syncScheduler: SyncScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val preselectedHiveId: String? = savedStateHandle["hiveId"]
    private val extractionId: String? = savedStateHandle["extractionId"]
    val isEditMode: Boolean = !extractionId.isNullOrEmpty()
    private var saveInProgress = false

    private val _uiState = MutableStateFlow(
        ExtractionFormUiState(
            extractionId = extractionId,
            selectedHiveId = preselectedHiveId ?: ""
        )
    )
    val uiState: StateFlow<ExtractionFormUiState> = _uiState.asStateFlow()

    private val _hives = MutableStateFlow<List<HiveResponse>>(emptyList())
    val hives: StateFlow<List<HiveResponse>> = _hives.asStateFlow()

    init {
        loadHives()
        if (isEditMode) loadExtraction(extractionId!!)
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

    private fun loadExtraction(id: String) {
        viewModelScope.launch {
            val cached = extractionRepository.getCachedExtractionById(id)
            if (cached != null) {
                publishExtraction(cached)
                return@launch
            }

            _uiState.update { it.copy(isLoading = true, error = null) }
            when (val result = extractionRepository.getExtractionById(id)) {
                is Result.Success -> {
                    publishExtraction(result.data)
                }
                is Result.Error -> _uiState.update { it.copy(isLoading = false, error = result.message) }
                Result.Loading -> {}
            }
        }
    }

    private fun publishExtraction(e: HiveExtraction) {
        val date = try {
            ZonedDateTime.parse(e.extractionDate).toLocalDate()
        } catch (ex: Exception) {
            LocalDate.now()
        }
        _uiState.update { state ->
            state.copy(
                isLoading = false,
                selectedHiveId = e.hiveId,
                extractionType = e.type,
                quantity = e.quantity.toString(),
                unit = e.unit,
                notes = e.notes ?: "",
                extractionDate = date
            )
        }
    }

    fun onHiveSelected(hiveId: String) { _uiState.update { it.copy(selectedHiveId = hiveId) } }
    fun onDateChange(date: LocalDate) { _uiState.update { it.copy(extractionDate = date) } }
    fun onTypeChange(type: ExtractionType) { _uiState.update { it.copy(extractionType = type) } }

    fun onQuantityChange(quantity: String) {
        // Accept comma as decimal separator and keep only digits + a single dot.
        val sanitized = quantity.replace(',', '.').filterIndexed { index, c ->
            c.isDigit() || (c == '.' && quantity.replace(',', '.').indexOf('.') == index)
        }
        _uiState.update { it.copy(quantity = sanitized, quantityError = validateQuantity(sanitized)) }
    }

    /** Adjust quantity with the +/- stepper, clamped to the allowed range. */
    fun stepQuantity(delta: Double) {
        val current = _uiState.value.quantity.toDoubleOrNull() ?: 0.0
        val next = (current + delta).coerceIn(0.0, MAX_QUANTITY)
        val formatted = if (next <= 0.0) "" else formatQuantity(next)
        _uiState.update { it.copy(quantity = formatted, quantityError = validateQuantity(formatted)) }
    }

    fun onUnitChange(unit: String) {
        _uiState.update {
            it.copy(unit = unit, unitError = if (unit.isBlank()) "Selectează unitatea de măsură" else null)
        }
    }

    fun onNotesChange(notes: String) { _uiState.update { it.copy(notes = notes.take(MAX_NOTES)) } }

    /** Inline validation: ignores empty input (required check happens on save). */
    private fun validateQuantity(value: String): String? {
        if (value.isBlank()) return null
        val parsed = value.toDoubleOrNull() ?: return "Introdu un număr valid"
        return when {
            parsed < MIN_QUANTITY -> "Cantitatea minimă este $MIN_QUANTITY"
            parsed > MAX_QUANTITY -> "Cantitatea maximă este $MAX_QUANTITY"
            else -> null
        }
    }

    private fun formatQuantity(value: Double): String =
        if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            java.math.BigDecimal(value)
                .setScale(2, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()
        }

    fun saveExtraction() {
        val state = _uiState.value
        if (saveInProgress || state.isLoading || state.isSuccess) return

        val quantityValue = state.quantity.toDoubleOrNull()
        val quantityError = when {
            state.quantity.isBlank() -> "Cantitatea este obligatorie"
            quantityValue == null -> "Cantitatea trebuie să fie un număr valid"
            quantityValue < MIN_QUANTITY -> "Cantitatea minimă este $MIN_QUANTITY"
            quantityValue > MAX_QUANTITY -> "Cantitatea maximă este $MAX_QUANTITY"
            else -> null
        }
        val unitError = if (state.unit.isBlank()) "Selectează unitatea de măsură" else null
        val hiveError = if (state.selectedHiveId.isBlank()) "Selectează stupul" else null

        if (hiveError != null || quantityError != null || unitError != null) {
            _uiState.update {
                it.copy(
                    error = hiveError ?: quantityError ?: unitError,
                    quantityError = quantityError,
                    unitError = unitError
                )
            }
            return
        }
        // Safe: quantityValue is non-null here because quantityError == null.
        val quantity = quantityValue!!
        saveInProgress = true
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            if (state.extractionId == null) createExtraction(state, quantity)
            else updateExtraction(state, quantity)
        }
    }

    private suspend fun createExtraction(state: ExtractionFormUiState, quantity: Double) {
        val request = CreateExtractionRequest(
            hiveId = state.selectedHiveId,
            extractionDate = state.extractionDate.atStartOfDay(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            type = state.extractionType,
            quantity = quantity,
            unit = state.unit,
            notes = state.notes.takeIf { it.isNotBlank() }
        )
        when (val result = extractionRepository.enqueueCreateExtraction(request)) {
            is Result.Success -> {
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

    private suspend fun updateExtraction(state: ExtractionFormUiState, quantity: Double) {
        val request = UpdateExtractionRequest(
            extractionDate = state.extractionDate.atStartOfDay(ZoneOffset.UTC)
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
            type = state.extractionType,
            quantity = quantity,
            unit = state.unit,
            notes = state.notes.takeIf { it.isNotBlank() }
        )
        when (val result = extractionRepository.enqueueUpdateExtraction(state.extractionId!!, request)) {
            is Result.Success -> {
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

    companion object {
        const val MIN_QUANTITY = 0.01
        const val MAX_QUANTITY = 999_999.99
        const val QUANTITY_STEP = 0.5
        const val MAX_NOTES = 1000

        /** All measurement units offered in the unit dropdown. */
        val UNITS = listOf("kg", "g", "L", "ml", "borcane", "rame", "bucăți")
    }
}

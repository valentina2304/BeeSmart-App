package com.example.beesmart.ui.apiaries

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beesmart.data.repository.ApiaryRepository
import com.example.beesmart.network.models.ApiaryDetailResponse
import com.example.beesmart.network.models.CreateApiaryRequest
import com.example.beesmart.network.models.UpdateApiaryRequest
import com.example.beesmart.data.repository.Result
import com.example.beesmart.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CreateApiaryViewModel @Inject constructor(
    private val repository: ApiaryRepository,
    private val syncScheduler: SyncScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow<CreateApiaryUiState>(CreateApiaryUiState.Idle)
    val uiState: StateFlow<CreateApiaryUiState> = _uiState.asStateFlow()

    private val _validationState = MutableStateFlow(ApiaryValidationState())
    val validationState: StateFlow<ApiaryValidationState> = _validationState.asStateFlow()

    private val apiaryId: String? = savedStateHandle["apiaryId"]
    val isEditMode: Boolean = !apiaryId.isNullOrEmpty()
    private var saveInProgress = false

    init {
        if (isEditMode) {
            loadApiaryDetails()
        }
    }

    private fun loadApiaryDetails() {
        apiaryId ?: return

        viewModelScope.launch {
            val cached = repository.getCachedApiaryById(apiaryId)
            if (cached != null) {
                publishApiary(cached)
                return@launch
            }

            _uiState.value = CreateApiaryUiState.Loading
            when (val result = repository.getApiaryById(apiaryId)) {
                is Result.Success -> {
                    publishApiary(result.data)
                }
                is Result.Error -> {
                    _uiState.value = CreateApiaryUiState.Error("Nu s-au putut încărca datele: ${result.message}")
                }
                else -> {}
            }
        }
    }

    private fun publishApiary(apiary: ApiaryDetailResponse) {
        _uiState.value = CreateApiaryUiState.LoadedData(
            name = apiary.name,
            description = apiary.description,
            location = apiary.location
        )
    }

    fun validateName(name: String) {
        val error = when {
            name.isEmpty() -> "Numele este obligatoriu"
            name.length < 2 -> "Numele este prea scurt (minim 2 caractere)"
            name.length > 100 -> "Numele este prea lung (maxim 100 caractere)"
            else -> null
        }
        _validationState.value = ApiaryValidationState(nameError = error)
    }

    fun saveApiary(name: String, description: String?, location: String?) {
        if (saveInProgress || _uiState.value is CreateApiaryUiState.Loading) return

        validateName(name)
        if (!_validationState.value.isValid) return

        saveInProgress = true
        viewModelScope.launch {
            _uiState.value = CreateApiaryUiState.Loading

            val duplicate = repository.getCachedApiaries().any {
                it.name.equals(name.trim(), ignoreCase = true) && it.id != apiaryId
            }
            if (duplicate) {
                _uiState.value = CreateApiaryUiState.Error("Există deja o stupină cu numele \"${name.trim()}\"")
                saveInProgress = false
                return@launch
            }

            val result = if (isEditMode && apiaryId != null) {
                val request = UpdateApiaryRequest(name, description, location)
                repository.enqueueUpdateApiary(apiaryId, request)
            } else {
                val request = CreateApiaryRequest(name, description, location)
                repository.enqueueCreateApiary(request)
            }

            when (result) {
                is Result.Success -> {
                    syncScheduler.requestSync()
                    val msg = if (isEditMode) "Stupina actualizată" else "Stupina creată"
                    _uiState.value = CreateApiaryUiState.Success(msg)
                }
                is Result.Error -> {
                    _uiState.value = CreateApiaryUiState.Error(result.message)
                    saveInProgress = false
                }
                else -> {
                    saveInProgress = false
                }
            }
        }
    }

    fun resetState() {
        saveInProgress = false
        _uiState.value = CreateApiaryUiState.Idle
    }
}

package com.example.beesmart.ui.hives

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beesmart.data.repository.HiveRepository
import com.example.beesmart.data.repository.Result
import com.example.beesmart.network.models.CreateHiveRequest
import com.example.beesmart.network.models.HiveResponse
import com.example.beesmart.network.models.HiveStatus
import com.example.beesmart.network.models.HiveType
import com.example.beesmart.network.models.UpdateHiveRequest
import com.example.beesmart.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HiveFormViewModel @Inject constructor(
    private val repository: HiveRepository,
    private val syncScheduler: SyncScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow<HiveFormUiState>(HiveFormUiState.Idle)
    val uiState: StateFlow<HiveFormUiState> = _uiState.asStateFlow()

    private val _validationState = MutableStateFlow(HiveValidationState())
    val validationState: StateFlow<HiveValidationState> = _validationState.asStateFlow()

    private val hiveId: String? = savedStateHandle["hiveId"]
    private val apiaryId: String? = savedStateHandle["apiaryId"]
    private var resolvedApiaryId: String? = apiaryId
    private var saveInProgress = false

    val isEditMode: Boolean = !hiveId.isNullOrEmpty()

    init {
        if (isEditMode) {
            loadHiveDetails()
        }
    }

    private fun loadHiveDetails() {
        hiveId ?: return
        viewModelScope.launch {
            val cached = repository.getCachedHiveById(hiveId)
            if (cached != null) {
                publishHive(cached)
                return@launch
            }

            _uiState.value = HiveFormUiState.Loading
            when (val result = repository.getHiveById(hiveId)) {
                is Result.Success -> {
                    publishHive(result.data)
                }
                is Result.Error -> {
                    _uiState.value = HiveFormUiState.Error("Eroare la încărcare: ${result.message}")
                }
                else -> {}
            }
        }
    }

    private fun publishHive(hive: HiveResponse) {
        resolvedApiaryId = hive.apiaryId
        _uiState.value = HiveFormUiState.LoadedData(
            name = hive.name,
            type = hive.type,
            status = hive.status,
            notes = hive.notes,
            reginaPrezenta = hive.reginaPrezenta,
            varstaRegina = hive.varstaRegina,
            rameAlbine = hive.rameAlbine,
            ramePuiet = hive.ramePuiet,
            rameMiere = hive.rameMiere
        )
    }

    fun validateName(name: String) {
        val error = when {
            name.isEmpty() -> "Numele este obligatoriu"
            name.length < 2 -> "Prea scurt (minim 2 caractere)"
            name.length > 100 -> "Prea lung (maxim 100 caractere)"
            else -> null
        }
        _validationState.value = HiveValidationState(nameError = error)
    }

    fun saveHive(
        name: String,
        type: HiveType,
        status: HiveStatus,
        notes: String?,
        reginaPrezenta: Boolean,
        varstaRegina: Int,
        rameAlbine: Int,
        ramePuiet: Int,
        rameMiere: Int
    ) {
        if (saveInProgress || _uiState.value is HiveFormUiState.Loading) return

        validateName(name)
        if (!_validationState.value.isValid) return

        saveInProgress = true
        viewModelScope.launch {
            _uiState.value = HiveFormUiState.Loading

            val aid = resolvedApiaryId
            if (aid != null) {
                val duplicate = repository.getCachedHivesByApiaryId(aid).any {
                    it.name.equals(name.trim(), ignoreCase = true) && it.id != hiveId
                }
                if (duplicate) {
                    _uiState.value = HiveFormUiState.Error("Există deja un stup cu numele \"${name.trim()}\" în această stupină")
                    saveInProgress = false
                    return@launch
                }
            }

            val result = if (isEditMode && hiveId != null) {
                val request = UpdateHiveRequest(
                    name = name,
                    type = type,
                    status = status,
                    notes = notes,
                    reginaPrezenta = reginaPrezenta,
                    varstaRegina = varstaRegina,
                    rameAlbine = rameAlbine,
                    ramePuiet = ramePuiet,
                    rameMiere = rameMiere
                )
                repository.enqueueUpdateHive(hiveId, request)
            } else if (apiaryId != null) {
                val request = CreateHiveRequest(
                    name = name,
                    type = type,
                    status = status,
                    notes = notes,
                    reginaPrezenta = reginaPrezenta,
                    varstaRegina = varstaRegina,
                    rameAlbine = rameAlbine,
                    ramePuiet = ramePuiet,
                    rameMiere = rameMiere
                )
                repository.enqueueCreateHive(apiaryId, request)
            } else {
                Result.Error("ID-uri lipsă (eroare internă)")
            }

            when (result) {
                is Result.Success -> {
                    syncScheduler.requestSync()
                    val msg = if (isEditMode) "Stup actualizat" else "Stup creat"
                    _uiState.value = HiveFormUiState.Success(msg)
                }
                is Result.Error -> {
                    _uiState.value = HiveFormUiState.Error(result.message)
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
        _uiState.value = HiveFormUiState.Idle
    }
}

package com.example.beesmart.ui.hives

import com.example.beesmart.network.models.HiveStatus
import com.example.beesmart.network.models.HiveType

sealed class HiveFormUiState {
    object Idle : HiveFormUiState()
    object Loading : HiveFormUiState()
    data class Success(val message: String) : HiveFormUiState()
    data class Error(val message: String) : HiveFormUiState()

    data class LoadedData(
        val name: String,
        val type: HiveType,
        val status: HiveStatus,
        val notes: String?,
        val reginaPrezenta: Boolean,
        val varstaRegina: Int,
        val rameAlbine: Int,
        val ramePuiet: Int,
        val rameMiere: Int
    ) : HiveFormUiState()
}

data class HiveValidationState(
    val nameError: String? = null
) {
    val isValid: Boolean get() = nameError == null
}

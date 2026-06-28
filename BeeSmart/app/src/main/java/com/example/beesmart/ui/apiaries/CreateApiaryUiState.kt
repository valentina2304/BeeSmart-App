package com.example.beesmart.ui.apiaries

sealed class CreateApiaryUiState {
    object Idle : CreateApiaryUiState()
    object Loading : CreateApiaryUiState()
    data class Success(val message: String) : CreateApiaryUiState()
    data class Error(val message: String) : CreateApiaryUiState()

    data class LoadedData(
        val name: String,
        val description: String?,
        val location: String?
    ) : CreateApiaryUiState()
}

data class ApiaryValidationState(
    val nameError: String? = null
) {
    val isValid: Boolean get() = nameError == null
}
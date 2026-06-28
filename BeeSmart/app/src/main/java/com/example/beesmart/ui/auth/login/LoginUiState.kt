package com.example.beesmart.ui.auth.login
sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()

    data class Success(val message: String) : LoginUiState()

    data class Error(val message: String) : LoginUiState()
    data class EmailNotConfirmed(val email: String) : LoginUiState()
}
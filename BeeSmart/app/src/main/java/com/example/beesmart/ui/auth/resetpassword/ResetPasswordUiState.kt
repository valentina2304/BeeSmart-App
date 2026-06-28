package com.example.beesmart.ui.auth.resetpassword

sealed class ResetPasswordUiState {
    object Idle : ResetPasswordUiState()

    object Loading : ResetPasswordUiState()

    data class Success(val message: String) : ResetPasswordUiState()

    data class Error(val message: String) : ResetPasswordUiState()

    object InvalidToken : ResetPasswordUiState()
}
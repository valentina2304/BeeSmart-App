package com.example.beesmart.ui.auth.forgotpassword

sealed class ForgotPasswordUiState {
    object Idle : ForgotPasswordUiState()

    object Loading : ForgotPasswordUiState()

    data class Success(val message: String) : ForgotPasswordUiState()

    data class Error(val message: String) : ForgotPasswordUiState()
}
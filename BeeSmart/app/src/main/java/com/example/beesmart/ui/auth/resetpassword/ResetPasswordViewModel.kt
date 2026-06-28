package com.example.beesmart.ui.auth.resetpassword

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beesmart.data.repository.AuthRepository
import com.example.beesmart.data.repository.NetworkException
import com.example.beesmart.data.repository.ResetPasswordException
import com.example.beesmart.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResetPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ResetPasswordUiState>(ResetPasswordUiState.Idle)
    val uiState: StateFlow<ResetPasswordUiState> = _uiState.asStateFlow()

    private val _validationState = MutableStateFlow(ResetPasswordValidation())
    val validationState: StateFlow<ResetPasswordValidation> = _validationState.asStateFlow()

    private var currentNewPassword: String = ""
    private var token: String = ""
    private var email: String = ""

    fun setTokenAndEmail(token: String, email: String) {
        this.token = token
        this.email = email
        if (token.isEmpty() || email.isEmpty()) {
            _uiState.value = ResetPasswordUiState.InvalidToken
        }
    }

    fun validateNewPassword(password: String) {
        currentNewPassword = password
        val error = if (password.isNotEmpty() && password.length < 8) "Minim 8 caractere" else null
        _validationState.value = _validationState.value.copy(newPasswordError = error)
    }

    fun validateConfirmPassword(confirmPassword: String) {
        val error = if (confirmPassword.isNotEmpty() && confirmPassword != currentNewPassword) "Parolele nu coincid" else null
        _validationState.value = _validationState.value.copy(confirmPasswordError = error)
    }

    fun resetPassword(newPassword: String, confirmPassword: String) {
        if (token.isEmpty() || email.isEmpty()) {
            _uiState.value = ResetPasswordUiState.InvalidToken
            return
        }
        if (newPassword != confirmPassword) {
            _validationState.value = _validationState.value.copy(confirmPasswordError = "Parolele nu coincid")
            return
        }

        _uiState.value = ResetPasswordUiState.Loading

        viewModelScope.launch {
            when (val result = authRepository.resetPassword(token, email, newPassword)) {
                is Result.Success -> {
                    _uiState.value = ResetPasswordUiState.Success(result.data)
                }
                is Result.Error -> {
                    val exception = result.exception
                    val errorMessage = when (exception) {
                        is ResetPasswordException -> exception.message ?: "Token expirat"
                        is NetworkException -> "Eroare conexiune"
                        else -> result.message
                    }
                    _uiState.value = ResetPasswordUiState.Error(errorMessage)
                }
                is Result.Loading -> {}
            }
        }
    }

    fun resetState() {
        _uiState.value = ResetPasswordUiState.Idle
    }
}
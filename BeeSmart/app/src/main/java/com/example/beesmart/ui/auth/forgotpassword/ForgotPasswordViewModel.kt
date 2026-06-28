package com.example.beesmart.ui.auth.forgotpassword

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beesmart.data.repository.AuthRepository
import com.example.beesmart.data.repository.ForgotPasswordException
import com.example.beesmart.data.repository.NetworkException
import com.example.beesmart.data.repository.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ForgotPasswordUiState>(ForgotPasswordUiState.Idle)
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    private val _validationState = MutableStateFlow(ForgotPasswordValidation())
    val validationState: StateFlow<ForgotPasswordValidation> = _validationState.asStateFlow()

    fun validateEmail(email: String) {
        val error = if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) "Email invalid" else null
        _validationState.value = _validationState.value.copy(emailError = error)
    }

    fun sendResetEmail(email: String) {
        if (email.isBlank()) {
            _validationState.value = _validationState.value.copy(emailError = "Introdu email-ul")
            return
        }

        _uiState.value = ForgotPasswordUiState.Loading

        viewModelScope.launch {
            when (val result = authRepository.forgotPassword(email)) {
                is Result.Success -> {
                    _uiState.value = ForgotPasswordUiState.Success(result.data)
                }
                is Result.Error -> {
                    val exception = result.exception
                    val errorMessage = when (exception) {
                        is ForgotPasswordException -> exception.message ?: "Email neînregistrat"
                        is NetworkException -> "Eroare conexiune"
                        else -> result.message
                    }
                    _uiState.value = ForgotPasswordUiState.Error(errorMessage)
                }
                is Result.Loading -> {}
            }
        }
    }

    fun resetState() {
        _uiState.value = ForgotPasswordUiState.Idle
    }
}
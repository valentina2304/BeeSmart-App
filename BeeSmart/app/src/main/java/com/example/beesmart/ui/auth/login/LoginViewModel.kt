package com.example.beesmart.ui.auth.login

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beesmart.data.repository.AuthRepository
import com.example.beesmart.data.repository.EmailNotConfirmedException
import com.example.beesmart.data.repository.Result
import com.example.beesmart.sync.ConnectivityObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    connectivity: ConnectivityObserver
) : ViewModel() {
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()
    private val _validationState = MutableStateFlow(LoginValidation())
    val validationState: StateFlow<LoginValidation> = _validationState.asStateFlow()

    val isOnline: StateFlow<Boolean> = connectivity.isOnline
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = connectivity.isCurrentlyOnline()
        )

    fun validateEmail(email: String) {
        val error = if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) "Email invalid" else null
        _validationState.value = _validationState.value.copy(emailError = error)
    }

    fun validatePassword(password: String) {
        val error = if (password.isNotEmpty() && password.length < 6) "Minim 6 caractere" else null
        _validationState.value = _validationState.value.copy(passwordError = error)
    }

    fun login(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("Completează toate câmpurile")
            return
        }

        _uiState.value = LoginUiState.Loading

        viewModelScope.launch {
            when (val result = authRepository.login(email, password)) {
                is Result.Success -> {
                    _uiState.value = LoginUiState.Success("Autentificare reușită!")
                }
                is Result.Error -> {
                    val ex = result.exception
                    if (ex is EmailNotConfirmedException) {
                        _uiState.value = LoginUiState.EmailNotConfirmed(ex.email)
                    } else {
                        _uiState.value = LoginUiState.Error(result.message)
                    }
                }
                else -> {}
            }
        }
    }

    fun resendConfirmationEmail(email: String) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            when (val result = authRepository.resendConfirmationEmail(email)) {
                is Result.Success -> _uiState.value = LoginUiState.Success("Email trimis!")
                is Result.Error -> _uiState.value = LoginUiState.Error(result.message)
                else -> {}
            }
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }
}
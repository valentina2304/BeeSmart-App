package com.example.beesmart.ui.auth.register

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beesmart.data.repository.AuthRepository
import com.example.beesmart.data.repository.NetworkException
import com.example.beesmart.data.repository.RegisterException
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
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    connectivity: ConnectivityObserver
) : ViewModel() {

    val isOnline: StateFlow<Boolean> = connectivity.isOnline
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = connectivity.isCurrentlyOnline()
        )

    private val _uiState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    private val _validationState = MutableStateFlow(RegisterValidation())
    val validationState: StateFlow<RegisterValidation> = _validationState.asStateFlow()

    private val _passwordStrength = MutableStateFlow<PasswordStrength?>(null)
    val passwordStrength: StateFlow<PasswordStrength?> = _passwordStrength.asStateFlow()

    private var currentPassword: String = ""

    fun validateFirstName(firstName: String) {
        val error = if (firstName.isNotEmpty() && firstName.length < 2) "Prenumele este prea scurt" else null
        _validationState.value = _validationState.value.copy(firstNameError = error)
    }

    fun validateLastName(lastName: String) {
        val error = if (lastName.isNotEmpty() && lastName.length < 2) "Numele este prea scurt" else null
        _validationState.value = _validationState.value.copy(lastNameError = error)
    }

    fun validatePhoneNumber(phone: String) {
        val error = if (phone.isNotEmpty() && phone.length < 10) "Număr de telefon invalid" else null
        _validationState.value = _validationState.value.copy(phoneNumberError = error)
    }

    fun validateEmail(email: String) {
        val error = if (email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) "Email invalid" else null
        _validationState.value = _validationState.value.copy(emailError = error)
    }

    fun validatePassword(password: String) {
        currentPassword = password
        if (password.isEmpty()) {
            _validationState.value = _validationState.value.copy(passwordError = null)
            _passwordStrength.value = null
        } else {
            _passwordStrength.value = calculatePasswordStrength(password)
            val error = if (password.length < 8) "Minim 8 caractere" else null
            _validationState.value = _validationState.value.copy(passwordError = error)
        }
    }

    fun validateConfirmPassword(confirmPassword: String) {
        val error = if (confirmPassword.isNotEmpty() && currentPassword != confirmPassword) "Parolele nu se potrivesc" else null
        _validationState.value = _validationState.value.copy(confirmPasswordError = error)
    }

    private fun calculatePasswordStrength(password: String): PasswordStrength {
        var strength = 0
        if (password.length >= 8) strength++
        if (password.length >= 12) strength++
        if (password.any { it.isDigit() }) strength++
        if (password.any { it.isUpperCase() }) strength++
        if (password.any { !it.isLetterOrDigit() }) strength++

        val level = when {
            strength <= 2 -> 1
            strength <= 4 -> 2
            else -> 3
        }
        val label = when (level) {
            1 -> "Slabă"
            2 -> "Medie"
            else -> "Puternică"
        }
        return PasswordStrength(level, label)
    }

    fun register(
        firstName: String,
        lastName: String,
        phoneNumber: String,
        email: String,
        password: String,
        confirmPassword: String,
        birthDate: String?
    ) {
        if (firstName.isBlank() || lastName.isBlank() || email.isBlank() || password.isBlank()) {
            _uiState.value = RegisterUiState.Error("Completează câmpurile obligatorii")
            return
        }
        if (password != confirmPassword) {
            _validationState.value = _validationState.value.copy(confirmPasswordError = "Parolele nu se potrivesc")
            return
        }

        _uiState.value = RegisterUiState.Loading

        viewModelScope.launch {
            val result = authRepository.register(
                email = email,
                password = password,
                firstName = firstName,
                lastName = lastName,
                phone = phoneNumber.ifEmpty { null },
                birthDate = birthDate
            )

            when (result) {
                is Result.Success -> {
                    _uiState.value = RegisterUiState.Success("Cont creat cu succes.")
                }
                is Result.Error -> {
                    val exception = result.exception
                    val errorMessage = when (exception) {
                        is RegisterException -> exception.message ?: "Înregistrare eșuată"
                        is NetworkException -> "Eroare de rețea."
                        else -> result.message
                    }
                    _uiState.value = RegisterUiState.Error(errorMessage)
                }
                is Result.Loading -> {}
            }
        }
    }

    fun resetState() {
        _uiState.value = RegisterUiState.Idle
    }
}

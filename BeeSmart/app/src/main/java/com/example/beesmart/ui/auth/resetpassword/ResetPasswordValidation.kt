package com.example.beesmart.ui.auth.resetpassword

data class ResetPasswordValidation(
    val newPasswordError: String? = null,
    val confirmPasswordError: String? = null
) {
    val isValid: Boolean
        get() = newPasswordError == null && confirmPasswordError == null
}
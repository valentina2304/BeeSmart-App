package com.example.beesmart.ui.auth.forgotpassword

data class ForgotPasswordValidation(
    val emailError: String? = null
) {
    val isValid: Boolean
        get() = emailError == null
}
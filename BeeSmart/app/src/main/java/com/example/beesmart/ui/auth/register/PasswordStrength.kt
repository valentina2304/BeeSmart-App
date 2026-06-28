package com.example.beesmart.ui.auth.register

data class PasswordStrength(
    val level: Int, // 1 = Weak, 2 = Medium, 3 = Strong
    val label: String
)
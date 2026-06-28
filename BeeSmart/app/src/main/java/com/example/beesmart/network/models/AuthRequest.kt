package com.example.beesmart.network.models

data class AuthRequest(
    val email: String,
    val password: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val phoneNumber: String? = null,
    val birthDate: String? = null,
    val deviceInfo: String? = null
)
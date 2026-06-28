package com.example.beesmart.network.models

data class UserProfile(
    val id: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val phoneNumber: String?,
    val birthDate: String?,
    val emailConfirmed: Boolean,
    val createdAt: String,
    val updatedAt: String?
)

data class UpdateProfileRequest(
    val firstName: String?,
    val lastName: String?,
    val phoneNumber: String?,
    val birthDate: String?
)
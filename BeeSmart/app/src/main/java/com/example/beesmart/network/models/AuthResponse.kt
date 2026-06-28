package com.example.beesmart.network.models

data class AuthResponse(
    val success: Boolean? = null,
    val message: String? = null,
    val token: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresIn: Int? = null
) {
    val effectiveToken: String?
        get() = token ?: accessToken
}

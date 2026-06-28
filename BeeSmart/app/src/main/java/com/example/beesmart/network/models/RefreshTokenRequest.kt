package com.example.beesmart.network.models

import com.squareup.moshi.Json

data class RefreshTokenRequest(
    @Json(name = "refreshToken")
    val refreshToken: String
)

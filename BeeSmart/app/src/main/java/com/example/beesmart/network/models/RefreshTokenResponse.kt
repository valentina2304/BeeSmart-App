package com.example.beesmart.network.models

import com.squareup.moshi.Json

data class RefreshTokenResponse(
    @Json(name = "accessToken")
    val accessToken: String,
    @Json(name = "refreshToken")
    val refreshToken: String,
    @Json(name = "expiresIn")
    val expiresIn: Int
)

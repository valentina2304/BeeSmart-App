package com.example.beesmart.network.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Generic response for success messages from the server.
 */
@JsonClass(generateAdapter = true)
data class MessageResponse(
    @Json(name = "message")
    val message: String
)

/**
 * Request for email confirmation.
 */
@JsonClass(generateAdapter = true)
data class ConfirmEmailRequest(
    @Json(name = "token")
    val token: String,

    @Json(name = "email")
    val email: String
)

/**
 * Request for forgot password (reset request).
 */
@JsonClass(generateAdapter = true)
data class ForgotPasswordRequest(
    @Json(name = "email")
    val email: String
)

/**
 * Request for reset password (the actual reset).
 */
@JsonClass(generateAdapter = true)
data class ResetPasswordRequest(
    @Json(name = "token")
    val token: String,

    @Json(name = "email")
    val email: String,

    @Json(name = "newPassword")
    val newPassword: String
)

/**
 * Request to resend the confirmation email.
 */
@JsonClass(generateAdapter = true)
data class ResendConfirmationRequest(
    @Json(name = "email")
    val email: String
)
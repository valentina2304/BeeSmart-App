package com.example.beesmart.network

import com.example.beesmart.network.models.AuthRequest
import com.example.beesmart.network.models.AuthResponse
import com.example.beesmart.network.models.RefreshRequest
import com.example.beesmart.network.models.ConfirmEmailRequest
import com.example.beesmart.network.models.ForgotPasswordRequest
import com.example.beesmart.network.models.ResetPasswordRequest
import com.example.beesmart.network.models.ResendConfirmationRequest
import com.example.beesmart.network.models.MessageResponse
import com.example.beesmart.network.models.UserProfile
import com.example.beesmart.network.models.UpdateProfileRequest
import retrofit2.Call
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT

interface AuthApi {
    @POST("auth/login")
    suspend fun login(@Body req: AuthRequest): Response<AuthResponse>

    @POST("auth/register")
    suspend fun register(@Body req: AuthRequest): Response<AuthResponse>

    /**
     * Refreshes the access token using the refresh token.
     */
    @POST("auth/refresh")
    suspend fun refresh(@Body req: RefreshRequest): Response<AuthResponse>

    /**
     * Logs out by revoking the refresh token.
     */
    @POST("auth/logout")
    suspend fun logout(@Body req: RefreshRequest): Response<Unit>

    /**
     * Confirms the email address using the token.
     */
    @POST("auth/confirm-email")
    suspend fun confirmEmail(@Body req: ConfirmEmailRequest): Response<MessageResponse>

    /**
     * Sends a password reset email.
     */
    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body req: ForgotPasswordRequest): Response<MessageResponse>

    /**
     * Resets the password using the token.
     */
    @POST("auth/reset-password")
    suspend fun resetPassword(@Body req: ResetPasswordRequest): Response<MessageResponse>

    /**
     * Resends the confirmation email.
     */
    @POST("auth/resend-confirmation")
    suspend fun resendConfirmation(@Body req: ResendConfirmationRequest): Response<MessageResponse>

    /**
     * Retrieves the current user's profile.
     */
    @GET("auth/profile")
    suspend fun getProfile(): Response<UserProfile>

    /**
     * Updates the current user's profile.
     */
    @PUT("auth/profile")
    suspend fun updateProfile(@Body req: UpdateProfileRequest): Response<UserProfile>

    /**
     * Refresh token endpoint - used by AuthInterceptor for token refresh
     * Uses synchronous Call instead of suspend for use in OkHttp interceptor
     */
    @POST("auth/refresh")
    fun refreshTokenSync(@Body request: RefreshRequest): Call<AuthResponse>
}
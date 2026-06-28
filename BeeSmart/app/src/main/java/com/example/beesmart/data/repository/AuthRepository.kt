package com.example.beesmart.data.repository

import com.example.beesmart.data.local.AppDatabase
import com.example.beesmart.network.AuthApi
import com.example.beesmart.network.models.AuthRequest
import com.example.beesmart.network.models.AuthResponse
import com.example.beesmart.network.models.ResendConfirmationRequest
import com.example.beesmart.network.models.ForgotPasswordRequest
import com.example.beesmart.network.models.ResetPasswordRequest
import com.example.beesmart.utils.SessionManager
import com.example.beesmart.data.repository.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private const val SERVER_UNREACHABLE_MESSAGE =
    "Serverul nu este disponibil. Verifică conexiunea și încearcă din nou."

@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val sessionManager: SessionManager,
    private val appDatabase: AppDatabase,
    private val userProfileRepository: UserProfileRepository
) {

    suspend fun login(email: String, password: String): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val response = authApi.login(AuthRequest(email, password))

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                // Clear local cache when a different account logs in (handles force-quit without logout)
                val incomingUserId = extractUserIdFromJwt(body.accessToken ?: "")
                val storedUserId = sessionManager.getCurrentUserId()
                if (incomingUserId != null && incomingUserId != storedUserId) {
                    appDatabase.clearAllTables()
                    // Drop the previous account's cached profile so it can't surface offline.
                    sessionManager.clearUserProfileJson()
                }

                sessionManager.saveTokens(
                    body.accessToken ?: "",
                    body.refreshToken ?: "",
                    body.expiresIn?.toLong() ?: 3600L
                )
                if (incomingUserId != null) sessionManager.saveUserId(incomingUserId)

                // Cache the profile now, while we're guaranteed online. Without this the
                // profile is only stored after the profile screen successfully loads online,
                // so a user who goes offline right after login would see placeholder values
                // ("Profil BeeSmart") instead of their real name. Best-effort: a failure here
                // is non-fatal because the profile screen also caches on its first online load.
                cacheProfileBestEffort()

                Result.Success(body)
            } else {
                val errorBody = response.errorBody()?.string()
                val exception = when (response.code()) {
                    401 -> if (errorBody?.contains("email", true) == true) {
                        EmailNotConfirmedException(email)
                    } else {
                        InvalidCredentialsException("Credentiale invalide")
                    }
                    403 -> if (
                        errorBody?.contains("email", true) == true ||
                        errorBody?.contains("confirm", true) == true
                    ) {
                        EmailNotConfirmedException(email)
                    } else {
                        NetworkException("Nu ai acces pentru aceasta actiune.")
                    }
                    else -> NetworkException("A aparut o eroare la autentificare. Incearca din nou.")
                }
                Result.Error(exception.message ?: "Eroare login", response.code(), exception)
            }
        } catch (e: IOException) {
            Result.Error(SERVER_UNREACHABLE_MESSAGE, null, e)
        } catch (e: Exception) {
            Result.Error(SERVER_UNREACHABLE_MESSAGE, null, e)
        }
    }

    suspend fun register(email: String, password: String, firstName: String?, lastName: String?, phone: String?, birthDate: String?): Result<AuthResponse> = withContext(Dispatchers.IO) {
        try {
            val request = AuthRequest(email, password, firstName, lastName, phone, birthDate)
            val response = authApi.register(request)

            if (response.isSuccessful && response.body() != null) {
                Result.Success(response.body()!!)
            } else {
                val msg = if (response.code() == 409) "Email deja existent" else "Eroare înregistrare"
                Result.Error(msg, response.code())
            }
        } catch (e: IOException) {
            Result.Error(SERVER_UNREACHABLE_MESSAGE, null, e)
        } catch (e: Exception) {
            Result.Error(SERVER_UNREACHABLE_MESSAGE, null, e)
        }
    }

    suspend fun resendConfirmationEmail(email: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val response = authApi.resendConfirmation(ResendConfirmationRequest(email))
            if (response.isSuccessful) Result.Success(true)
            else Result.Error("Eroare trimitere email", response.code())
        } catch (e: Exception) {
            Result.Error(SERVER_UNREACHABLE_MESSAGE, null, e)
        }
    }

    suspend fun forgotPassword(email: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = authApi.forgotPassword(ForgotPasswordRequest(email))
            if (response.isSuccessful) Result.Success(response.body()?.message ?: "Email trimis")
            else Result.Error("Eroare trimitere resetare", response.code())
        } catch (e: Exception) {
            Result.Error(SERVER_UNREACHABLE_MESSAGE, null, e)
        }
    }

    suspend fun resetPassword(token: String, email: String, pass: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = authApi.resetPassword(ResetPasswordRequest(token, email, pass))
            if (response.isSuccessful) Result.Success(response.body()?.message ?: "Parolă schimbată")
            else Result.Error("Token invalid sau expirat", response.code())
        } catch (e: Exception) {
            Result.Error(SERVER_UNREACHABLE_MESSAGE, null, e)
        }
    }

    suspend fun clearSession() = withContext(Dispatchers.IO) {
        sessionManager.clearTokens()
    }

    /**
     * Warms the local profile cache via UserProfileRepository, which uses the authenticated
     * client and persists the result. Swallows all errors — caching is best-effort.
     */
    private suspend fun cacheProfileBestEffort() {
        try {
            userProfileRepository.getUserProfile()
        } catch (e: Exception) {
            // Non-fatal: profile will be cached lazily on the first online profile-screen load.
        }
    }

    private fun extractUserIdFromJwt(jwt: String): String? {
        return try {
            val payload = jwt.split(".").getOrNull(1) ?: return null
            val decoded = android.util.Base64.decode(payload, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING)
            val json = String(decoded, Charsets.UTF_8)
            Regex(""""sub"\s*:\s*"([^"]+)"""").find(json)?.groupValues?.get(1)
        } catch (e: Exception) {
            null
        }
    }
}

sealed class AuthException(message: String, cause: Throwable? = null) : Exception(message, cause)
class InvalidCredentialsException(message: String) : AuthException(message)
class EmailNotConfirmedException(val email: String) : AuthException("Email neconfirmat")
class RegisterException(message: String) : AuthException(message)
class ForgotPasswordException(message: String) : AuthException(message)
class ResetPasswordException(message: String) : AuthException(message)
class NetworkException(message: String, cause: Throwable? = null) : AuthException(message, cause)

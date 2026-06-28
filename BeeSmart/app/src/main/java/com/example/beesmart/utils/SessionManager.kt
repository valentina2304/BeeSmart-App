package com.example.beesmart.utils

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "beesmart_prefs")

class SessionManager(private val context: Context) {
    companion object {
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_TOKEN_EXPIRY = longPreferencesKey("token_expiry")
        private val KEY_USER_PROFILE_JSON = stringPreferencesKey("user_profile_json")
        private val KEY_USER_ID = stringPreferencesKey("current_user_id")
        private val KEY_LAST_SERVER_SYNC = longPreferencesKey("last_server_sync")
    }

    suspend fun saveUserId(userId: String) {
        context.dataStore.edit { it[KEY_USER_ID] = userId }
    }

    suspend fun getCurrentUserId(): String? {
        return context.dataStore.data.map { it[KEY_USER_ID] }.first()
    }

    /** Full session clear used on explicit logout — removes tokens AND the stored user ID. */
    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
            prefs.remove(KEY_TOKEN_EXPIRY)
            prefs.remove(KEY_USER_PROFILE_JSON)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_LAST_SERVER_SYNC)
        }
    }

    suspend fun markServerSyncNow() {
        context.dataStore.edit { it[KEY_LAST_SERVER_SYNC] = System.currentTimeMillis() }
    }

    suspend fun getLastServerSyncMillis(): Long? {
        return context.dataStore.data.map { it[KEY_LAST_SERVER_SYNC] }.first()
    }

    val lastServerSyncFlow: Flow<Long?> = context.dataStore.data
        .map { it[KEY_LAST_SERVER_SYNC] }

    suspend fun saveUserProfileJson(json: String) {
        context.dataStore.edit { it[KEY_USER_PROFILE_JSON] = json }
    }

    suspend fun getUserProfileJson(): String? {
        return context.dataStore.data.map { it[KEY_USER_PROFILE_JSON] }.first()
    }

    /** Removes the cached profile so a different account never sees the previous user's data. */
    suspend fun clearUserProfileJson() {
        context.dataStore.edit { it.remove(KEY_USER_PROFILE_JSON) }
    }

    // Flows for observing the tokens
    val accessTokenFlow: Flow<String?> = context.dataStore.data
        .map { it[KEY_ACCESS_TOKEN] }

    val refreshTokenFlow: Flow<String?> = context.dataStore.data
        .map { it[KEY_REFRESH_TOKEN] }

    /**
     * Saves both tokens and the expiry time.
     *
     * @param accessToken JWT token for authentication
     * @param refreshToken Token used for refresh
     * @param expiresIn Number of seconds until expiry
     */
    suspend fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_REFRESH_TOKEN] = refreshToken

            // Compute the exact expiry time (current time + expiresIn seconds),
            // subtracting 5 minutes (300 seconds) as a safety margin.
            val effectiveExpiresIn = (expiresIn - 300L).coerceAtLeast(30L)
            val expiryTime = System.currentTimeMillis() + (effectiveExpiresIn * 1000L)
            prefs[KEY_TOKEN_EXPIRY] = expiryTime
        }
    }


    /**
     * Returns the current access token.
     */
    suspend fun getAccessToken(): String? {
        return context.dataStore.data.map { it[KEY_ACCESS_TOKEN] }.first()
    }

    /**
     * Returns the current refresh token.
     */
    suspend fun getRefreshToken(): String? {
        return context.dataStore.data.map { it[KEY_REFRESH_TOKEN] }.first()
    }

    /**
     * Checks whether the token has expired or will expire soon.
     */
    suspend fun isTokenExpired(): Boolean {
        val expiryTime = context.dataStore.data.map { it[KEY_TOKEN_EXPIRY] }.first() ?: 0L
        return System.currentTimeMillis() >= expiryTime
    }

    /**
     * Returns the time remaining until expiry (in seconds).
     */
    suspend fun getTimeUntilExpiry(): Long {
        val expiryTime = context.dataStore.data.map { it[KEY_TOKEN_EXPIRY] }.first() ?: 0L
        val timeRemaining = (expiryTime - System.currentTimeMillis()) / 1000
        return if (timeRemaining > 0) timeRemaining else 0
    }

    /**
     * Removes all tokens (logout).
     */
    suspend fun clearTokens() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
            prefs.remove(KEY_TOKEN_EXPIRY)
            prefs.remove(KEY_USER_PROFILE_JSON)
            prefs.remove(KEY_LAST_SERVER_SYNC)
        }
    }

    /**
     * Checks whether the user is authenticated.
     */
    suspend fun isAuthenticated(): Boolean {
        val accessToken = getAccessToken()
        return !accessToken.isNullOrEmpty() && !isTokenExpired()
    }
}

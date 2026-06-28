package com.example.beesmart.data.repository

import android.util.Log
import com.example.beesmart.data.local.AppDatabase
import com.example.beesmart.data.local.dao.SyncQueueDao
import com.example.beesmart.data.local.entity.SyncQueueEntity
import com.example.beesmart.network.AuthApi
import com.example.beesmart.network.BackendReachability
import com.example.beesmart.network.models.ResendConfirmationRequest
import com.example.beesmart.network.models.UpdateProfileRequest
import com.example.beesmart.network.models.UserProfile
import com.example.beesmart.sync.ConnectivityObserver
import com.example.beesmart.utils.SessionManager
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserProfileRepository @Inject constructor(
    private val authApi: AuthApi,
    private val sessionManager: SessionManager,
    private val appDatabase: AppDatabase,
    private val syncQueueDao: SyncQueueDao,
    private val connectivity: ConnectivityObserver,
    private val backendReachability: BackendReachability,
    private val moshi: Moshi
) {

    private val profileAdapter by lazy { moshi.adapter(UserProfile::class.java) }
    private val updateAdapter by lazy { moshi.adapter(UpdateProfileRequest::class.java) }

    private fun canReachBackend(): Boolean =
        connectivity.canReachBackend(backendReachability)

    suspend fun getCachedUserProfile(): UserProfile? = withContext(Dispatchers.IO) {
        readCachedProfile()
    }

    suspend fun getUserProfile(): Result<UserProfile> = withContext(Dispatchers.IO) {
        val cached = readCachedProfile()
        // If a USER_PROFILE update is queued, the cache holds the latest user-intended
        // values. Returning server data here would mask the offline edit until sync runs.
        val hasPendingUpdate = syncQueueDao.getAll().any { it.entityType == "USER_PROFILE" }
        if (hasPendingUpdate && cached != null) {
            return@withContext Result.Success(cached)
        }
        if (!canReachBackend()) {
            return@withContext cached?.let { Result.Success(it) }
                ?: Result.Error("Profilul nu este disponibil offline")
        }
        return@withContext try {
            val response = authApi.getProfile()
            if (response.isSuccessful && response.body() != null) {
                val fresh = response.body()!!
                writeCachedProfile(fresh)
                sessionManager.markServerSyncNow()
                Result.Success(fresh)
            } else if (cached != null) {
                Result.Success(cached)
            } else {
                Result.Error("Nu s-a putut încărca profilul: ${response.code()}", response.code())
            }
        } catch (e: Exception) {
            if (cached != null) Result.Success(cached)
            else Result.Error("Eroare de rețea: ${e.message}", null, e)
        }
    }

    suspend fun updateUserProfile(
        firstName: String?,
        lastName: String?,
        phoneNumber: String?,
        birthDate: String?
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        val request = UpdateProfileRequest(
            firstName = firstName?.ifEmpty { null },
            lastName = lastName?.ifEmpty { null },
            phoneNumber = phoneNumber?.ifEmpty { null },
            birthDate = birthDate
        )
        // Optimistic local update so the UI reflects the edit immediately, regardless of
        // whether the network call below succeeds or gets queued for later sync.
        val cached = readCachedProfile()
        val optimistic = cached?.copy(
            firstName = request.firstName ?: cached.firstName,
            lastName = request.lastName ?: cached.lastName,
            phoneNumber = request.phoneNumber ?: cached.phoneNumber,
            birthDate = request.birthDate ?: cached.birthDate,
            updatedAt = Instant.now().toString()
        )
        if (optimistic != null) writeCachedProfile(optimistic)

        if (!canReachBackend()) {
            queueProfileUpdate(request)
            return@withContext optimistic?.let { Result.Success(it) }
                ?: Result.Error("Profilul nu este disponibil offline")
        }
        return@withContext try {
            val response = authApi.updateProfile(request)
            if (response.isSuccessful && response.body() != null) {
                val fresh = response.body()!!
                writeCachedProfile(fresh)
                sessionManager.markServerSyncNow()
                Result.Success(fresh)
            } else {
                Result.Error("Nu s-a putut actualiza profilul: ${response.code()}", response.code())
            }
        } catch (e: IOException) {
            queueProfileUpdate(request)
            optimistic?.let { Result.Success(it) }
                ?: Result.Error("Profilul nu este disponibil offline", null, e)
        } catch (e: Exception) {
            Result.Error("Eroare de rețea: ${e.message}", null, e)
        }
    }

    suspend fun enqueueUpdateUserProfile(
        firstName: String?,
        lastName: String?,
        phoneNumber: String?,
        birthDate: String?
    ): Result<UserProfile> = withContext(Dispatchers.IO) {
        val request = UpdateProfileRequest(
            firstName = firstName?.ifEmpty { null },
            lastName = lastName?.ifEmpty { null },
            phoneNumber = phoneNumber?.ifEmpty { null },
            birthDate = birthDate
        )
        val cached = readCachedProfile()
        val optimistic = cached?.copy(
            firstName = request.firstName ?: cached.firstName,
            lastName = request.lastName ?: cached.lastName,
            phoneNumber = request.phoneNumber ?: cached.phoneNumber,
            birthDate = request.birthDate ?: cached.birthDate,
            updatedAt = Instant.now().toString()
        )
        if (optimistic != null) {
            writeCachedProfile(optimistic)
            queueProfileUpdate(request)
            Result.Success(optimistic)
        } else {
            Result.Error("Profilul nu este disponibil local")
        }
    }

    suspend fun resendConfirmationEmail(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (!canReachBackend()) {
            return@withContext Result.Error("Ai nevoie de conexiune la internet pentru retrimiterea emailului.")
        }

        return@withContext try {
            val response = authApi.resendConfirmation(ResendConfirmationRequest(email))
            if (response.isSuccessful) {
                Result.Success(Unit)
            } else {
                Result.Error("Nu am putut retrimite emailul: ${response.code()}", response.code())
            }
        } catch (e: Exception) {
            Result.Error("Serverul nu este disponibil. Verifică conexiunea și încearcă din nou.", null, e)
        }
    }

    suspend fun logout(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            appDatabase.clearAllTables()
            sessionManager.clearSession()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error("Deconectarea a eșuat: ${e.message}", null, e)
        }
    }

    private suspend fun queueProfileUpdate(request: UpdateProfileRequest) {
        // Coalesce — only the latest edit needs to reach the server. Any queued
        // earlier USER_PROFILE update is dropped.
        syncQueueDao.deleteByEntityLocalId(USER_PROFILE_LOCAL_ID)
        syncQueueDao.insert(
            SyncQueueEntity(
                operationType = "UPDATE",
                entityType = "USER_PROFILE",
                entityLocalId = USER_PROFILE_LOCAL_ID,
                entityServerId = null,
                payload = updateAdapter.toJson(request)
            )
        )
    }

    private suspend fun readCachedProfile(): UserProfile? {
        val json = sessionManager.getUserProfileJson() ?: return null
        return try {
            profileAdapter.fromJson(json)
        } catch (e: Exception) {
            Log.w(TAG, "Cached profile JSON unreadable; ignoring", e)
            null
        }
    }

    private suspend fun writeCachedProfile(profile: UserProfile) {
        try {
            sessionManager.saveUserProfileJson(profileAdapter.toJson(profile))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cache profile", e)
        }
    }

    companion object {
        private const val TAG = "UserProfileRepository"
        const val USER_PROFILE_LOCAL_ID = "self"
    }
}

package com.example.beesmart.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.beesmart.data.local.dao.SyncQueueDao
import com.example.beesmart.data.repository.UserProfileRepository
import com.example.beesmart.network.models.UserProfile
import com.example.beesmart.data.repository.Result
import com.example.beesmart.sync.ConnectivityObserver
import com.example.beesmart.sync.SyncScheduler
import com.example.beesmart.utils.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserProfileViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    syncQueueDao: SyncQueueDao,
    sessionManager: SessionManager,
    connectivityObserver: ConnectivityObserver,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val _uiState = MutableStateFlow<UserProfileUiState>(UserProfileUiState.Idle)
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    val pendingSyncCount: StateFlow<Int> = syncQueueDao.observePendingCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val lastServerSyncMillis: StateFlow<Long?> = sessionManager.lastServerSyncFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    val isOnline: StateFlow<Boolean> = connectivityObserver.isOnline
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = connectivityObserver.isCurrentlyOnline()
        )

    private var currentProfile: UserProfile? = null
    private var loadProfileJob: Job? = null

    fun loadUserProfile(forceRefresh: Boolean = false) {
        if (loadProfileJob?.isActive == true && !forceRefresh) return
        if (forceRefresh) loadProfileJob?.cancel()

        loadProfileJob = viewModelScope.launch {
            val cached = currentProfile ?: userProfileRepository.getCachedUserProfile()
            if (cached != null) {
                currentProfile = cached
                _uiState.value = UserProfileUiState.ProfileLoaded(cached)
            } else {
                _uiState.value = UserProfileUiState.Loading
            }

            when (val result = userProfileRepository.getUserProfile()) {
                is Result.Success -> {
                    currentProfile = result.data
                    _uiState.value = UserProfileUiState.ProfileLoaded(result.data)
                }
                is Result.Error -> {
                    if (cached == null) {
                        _uiState.value = UserProfileUiState.Error(result.message)
                    }
                }
                else -> {}
            }
        }
    }

    fun updateProfile(firstName: String, lastName: String, phone: String, birthDate: String?) {
        viewModelScope.launch {
            val result = userProfileRepository.enqueueUpdateUserProfile(firstName, lastName, phone, birthDate)
            when (result) {
                is Result.Success -> {
                    currentProfile = result.data
                    _isEditMode.value = false
                    syncScheduler.requestSync()
                    _uiState.value = UserProfileUiState.ProfileLoaded(result.data)
                }
                is Result.Error -> {
                    _uiState.value = UserProfileUiState.Error(result.message)
                }
                else -> {}
            }
        }
    }

    fun resendConfirmationEmail() {
        val email = currentProfile?.email
        if (email.isNullOrBlank()) {
            _uiState.value = UserProfileUiState.Error("Emailul profilului nu este disponibil")
            return
        }

        _uiState.value = UserProfileUiState.Loading
        viewModelScope.launch {
            when (val result = userProfileRepository.resendConfirmationEmail(email)) {
                is Result.Success -> {
                    _uiState.value = UserProfileUiState.Success("Emailul de activare a fost retrimis")
                }
                is Result.Error -> {
                    _uiState.value = UserProfileUiState.Error(result.message)
                }
                else -> {}
            }
        }
    }

    fun setEditMode(enabled: Boolean) {
        _isEditMode.value = enabled
        if (!enabled && currentProfile != null) {
            _uiState.value = UserProfileUiState.ProfileLoaded(currentProfile!!)
        }
    }

    fun logout() {
        viewModelScope.launch {
            when (userProfileRepository.logout()) {
                is Result.Success -> _uiState.value = UserProfileUiState.Success("Deconectare reușită")
                is Result.Error -> _uiState.value = UserProfileUiState.Error("Deconectarea a eșuat")
                else -> {}
            }
        }
    }

    fun resetState() {
        if (currentProfile != null) {
            _uiState.value = UserProfileUiState.ProfileLoaded(currentProfile!!)
        } else {
            _uiState.value = UserProfileUiState.Idle
        }
    }
}

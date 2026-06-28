package com.example.beesmart.ui.profile

/**
 * UI State for UserProfile screen
 */
sealed class UserProfileUiState {
    object Idle : UserProfileUiState()
    object Loading : UserProfileUiState()
    data class Success(val message: String) : UserProfileUiState()
    data class Error(val message: String) : UserProfileUiState()
    data class ProfileLoaded(val profile: com.example.beesmart.network.models.UserProfile) : UserProfileUiState()
}


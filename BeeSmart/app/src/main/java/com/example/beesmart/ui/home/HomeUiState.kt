package com.example.beesmart.ui.home

import com.example.beesmart.data.repository.ApiaryRadar
import com.example.beesmart.data.repository.ApiaryAdviceDigest
import com.example.beesmart.data.repository.HoneyAnalytics

/**
 * Represents all possible states of the Home screen.
 */
sealed class HomeUiState {
    object Idle : HomeUiState()

    object Loading : HomeUiState()

    data class Success(
        val userName: String,
        val hivesCount: String
    ) : HomeUiState()

    data class Error(val message: String) : HomeUiState()

    object LoggingOut : HomeUiState()

    object LoggedOut : HomeUiState()
}

/**
 * Holds the dashboard data.
 */
data class DashboardData(
    val userName: String = "",
    val hivesCount: String = "0",
    val activeHivesCount: Int = 0,
    val pendingTasksCount: Int = 0,
    val overdueTasksCount: Int = 0,
    val inspectionsCount: Int = 0,
    val honeyAnalytics: HoneyAnalytics = HoneyAnalytics(),
    val apiaryRadar: ApiaryRadar = ApiaryRadar(),
    val deepBeeAdvice: ApiaryAdviceDigest = ApiaryAdviceDigest()
)

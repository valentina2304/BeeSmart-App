package com.example.beesmart.ui.hives

import com.example.beesmart.data.repository.BeeFlightAdvisor
import com.example.beesmart.data.repository.WeatherRepository
import com.example.beesmart.network.models.HiveResponse

sealed class HiveListUiState {
    object Loading : HiveListUiState()
    data class Success(val hives: List<HiveResponse>) : HiveListUiState()
    data class Error(val message: String) : HiveListUiState()
    data class DeleteSuccess(val message: String) : HiveListUiState()
}

/** Independent of the hive list — weather can finish before/after the hive query. */
sealed class WeatherUiState {
    object Idle : WeatherUiState()
    object Loading : WeatherUiState()
    data class Success(
        val location: String,
        val bundle: WeatherRepository.WeatherBundle,
        val flight: BeeFlightAdvisor.Verdict
    ) : WeatherUiState()
    data class Unavailable(val reason: String) : WeatherUiState()
}

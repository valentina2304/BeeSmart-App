package com.example.beesmart.ui.hives

import com.example.beesmart.network.models.HiveResponse
import com.example.beesmart.network.models.InspectionAiAnalysisResponse

sealed class HiveDetailUiState {
    object Loading : HiveDetailUiState()
    data class Success(
        val hive: HiveResponse,
        val qrContent: String,
        val aiAnalyses: List<InspectionAiAnalysisResponse>,
        val statsMessage: String? = null,
        val isStatsLoading: Boolean = false
    ) : HiveDetailUiState()
    data class Error(val message: String) : HiveDetailUiState()
}

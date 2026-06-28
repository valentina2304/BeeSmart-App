package com.example.beesmart.ui.inspections

import com.example.beesmart.data.repository.BroodAnalyzer
import com.example.beesmart.network.models.CellDetection
import com.example.beesmart.network.models.InspectionDetailResponse

sealed class InspectionFormUiState {
    object Idle : InspectionFormUiState()
    object Loading : InspectionFormUiState()
    data class Success(val message: String) : InspectionFormUiState()
    data class Error(val message: String) : InspectionFormUiState()
    data class LoadedData(val inspection: InspectionDetailResponse) : InspectionFormUiState()
}

sealed class AnalyzeCellsUiState {
    object Idle : AnalyzeCellsUiState()
    object Loading : AnalyzeCellsUiState()
    data class Success(
        val results: Map<String, Int>,
        val report: BroodAnalyzer.Report,
        val status: String = "success",
        val message: String? = null,
        val cellDetections: List<CellDetection> = emptyList()
    ) : AnalyzeCellsUiState()
    data class Error(val message: String, val isRetryable: Boolean = false) : AnalyzeCellsUiState()
}

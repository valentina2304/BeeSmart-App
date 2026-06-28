package com.example.beesmart.ui.inspections

import com.example.beesmart.network.models.InspectionDetailResponse

sealed class InspectionDetailUiState {
    object Loading : InspectionDetailUiState()
    data class Success(val inspection: InspectionDetailResponse) : InspectionDetailUiState()
    data class Error(val message: String) : InspectionDetailUiState()
    data class OperationSuccess(val message: String) : InspectionDetailUiState() // For photo deletion/update
}
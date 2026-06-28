package com.example.beesmart.ui.inspections

import com.example.beesmart.network.models.InspectionResponse

sealed class InspectionListUiState {
    object Loading : InspectionListUiState()
    data class Success(val inspections: List<InspectionResponse>) : InspectionListUiState()
    data class Error(val message: String) : InspectionListUiState()
    data class DeleteSuccess(val message: String) : InspectionListUiState()
}
package com.example.beesmart.ui.apiaries

import com.example.beesmart.network.models.ApiaryResponse

sealed class ApiaryListUiState {
    object Loading : ApiaryListUiState()
    data class Success(val apiaries: List<ApiaryResponse>) : ApiaryListUiState()
    data class Error(val message: String) : ApiaryListUiState()
    data class DeleteSuccess(val message: String) : ApiaryListUiState()
}
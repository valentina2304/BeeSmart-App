package com.example.beesmart.ui.tasks

import com.example.beesmart.network.models.TaskResponse

sealed class TaskListUiState {
    object Loading : TaskListUiState()
    data class Success(val tasks: List<TaskResponse>) : TaskListUiState()
    data class Error(val message: String) : TaskListUiState()
    data class OperationSuccess(val message: String) : TaskListUiState()
}

enum class TaskFilter {
    ALL, PENDING, OVERDUE, COMPLETED
}
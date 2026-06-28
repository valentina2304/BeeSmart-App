package com.example.beesmart.ui.tasks

import com.example.beesmart.network.models.TaskPriority
import com.example.beesmart.network.models.TaskStatus
import java.time.LocalDate

sealed class TaskFormUiState {
    object Idle : TaskFormUiState()
    object Loading : TaskFormUiState()
    data class Success(val message: String) : TaskFormUiState()
    data class Error(val message: String) : TaskFormUiState()

    data class LoadedData(
        val title: String,
        val description: String?,
        val priority: TaskPriority,
        val dueDate: LocalDate?,
        val apiaryId: String?,
        val hiveId: String?,
        val status: TaskStatus
    ) : TaskFormUiState()
}
package com.example.beesmart.network.models

import com.squareup.moshi.Json

// ==================== ENUMS ====================

enum class HiveType {
    @Json(name = "Langstroth") Langstroth,
    @Json(name = "Dadant") Dadant,
    @Json(name = "TopBar") TopBar,
    @Json(name = "Warre") Warre,
    @Json(name = "Other") Other
}

enum class HiveStatus {
    @Json(name = "Active") Active,
    @Json(name = "Queenless") Queenless,
    @Json(name = "Weak") Weak,
    @Json(name = "Sick") Sick,
    @Json(name = "Preparing") Preparing,
    @Json(name = "Inactive") Inactive
}

enum class TaskPriority {
    @Json(name = "Low") Low,
    @Json(name = "Normal") Normal,
    @Json(name = "High") High,
    @Json(name = "Critical") Critical
}

enum class TaskStatus {
    @Json(name = "Pending") Pending,
    @Json(name = "InProgress") InProgress,
    @Json(name = "Completed") Completed,
    @Json(name = "Cancelled") Cancelled
}

// ==================== APIARY DTOs ====================

data class CreateApiaryRequest(
    val name: String,
    val description: String? = null,
    val location: String? = null
)

data class UpdateApiaryRequest(
    val name: String,
    val description: String? = null,
    val location: String? = null
)

data class ApiaryResponse(
    val id: String,
    val userId: String,
    val name: String,
    val description: String?,
    val location: String?,
    val hiveCount: Int,
    val createdAt: String,
    val updatedAt: String
)

data class ApiaryDetailResponse(
    val id: String,
    val userId: String,
    val name: String,
    val description: String?,
    val location: String?,
    val hives: List<HiveResponse>,
    val createdAt: String,
    val updatedAt: String
)

// ==================== HIVE DTOs ====================

data class CreateHiveRequest(
    val name: String,
    val type: HiveType,
    val status: HiveStatus = HiveStatus.Active,
    val notes: String? = null,
    val reginaPrezenta: Boolean = false,
    val varstaRegina: Int = 0,
    val rameAlbine: Int = 0,
    val ramePuiet: Int = 0,
    val rameMiere: Int = 0
)

data class UpdateHiveRequest(
    val name: String,
    val type: HiveType,
    val status: HiveStatus,
    val notes: String? = null,
    val reginaPrezenta: Boolean = false,
    val varstaRegina: Int = 0,
    val rameAlbine: Int = 0,
    val ramePuiet: Int = 0,
    val rameMiere: Int = 0
)

data class HiveResponse(
    val id: String,
    val apiaryId: String,
    val apiaryName: String,
    val name: String,
    val type: HiveType,
    val status: HiveStatus,
    val notes: String?,
    val createdAt: String,
    val updatedAt: String,
    val reginaPrezenta: Boolean = false,
    val varstaRegina: Int = 0,
    val rameAlbine: Int = 0,
    val ramePuiet: Int = 0,
    val rameMiere: Int = 0,
    val ultimaInspectie: String? = null
)

// ==================== TASK DTOs ====================

data class CreateTaskRequest(
    val title: String,
    val description: String? = null,
    val priority: TaskPriority = TaskPriority.Normal,
    val dueDate: String? = null, // ISO 8601 format
    val apiaryId: String? = null,
    val hiveId: String? = null
)

data class UpdateTaskRequest(
    val title: String,
    val description: String? = null,
    val priority: TaskPriority,
    val status: TaskStatus,
    val dueDate: String? = null,
    val apiaryId: String? = null,
    val hiveId: String? = null
)

data class TaskResponse(
    val id: String,
    val userId: String,
    val apiaryId: String?,
    val apiaryName: String?,
    val hiveId: String?,
    val hiveName: String?,
    val title: String,
    val description: String?,
    val priority: TaskPriority,
    val status: TaskStatus,
    val dueDate: String?,
    val completedAt: String?,
    val createdAt: String,
    val updatedAt: String
)

// ==================== GENERIC RESPONSES ====================

data class ErrorResponse(
    val error: String
)

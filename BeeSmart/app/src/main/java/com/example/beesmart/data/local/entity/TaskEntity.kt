package com.example.beesmart.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.beesmart.network.models.TaskPriority
import com.example.beesmart.network.models.TaskResponse
import com.example.beesmart.network.models.TaskStatus

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val localId: String,
    val serverId: String?,
    val apiaryId: String?,
    val apiaryName: String?,
    val hiveId: String?,
    val hiveName: String?,
    val title: String,
    val description: String?,
    val priority: String,
    val status: String,
    val dueDate: String?,
    val completedAt: String?,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toTaskResponse() = TaskResponse(
        id = serverId ?: localId,
        userId = "",
        apiaryId = apiaryId,
        apiaryName = apiaryName,
        hiveId = hiveId,
        hiveName = hiveName,
        title = title,
        description = description,
        priority = TaskPriority.valueOf(priority),
        status = TaskStatus.valueOf(status),
        dueDate = dueDate,
        completedAt = completedAt,
        createdAt = "",
        updatedAt = updatedAt.toString()
    )
}

fun TaskResponse.toEntity() = TaskEntity(
    localId = id,
    serverId = id,
    apiaryId = apiaryId,
    apiaryName = apiaryName,
    hiveId = hiveId,
    hiveName = hiveName,
    title = title,
    description = description,
    priority = priority.name,
    status = status.name,
    dueDate = dueDate,
    completedAt = completedAt,
    syncStatus = SyncStatus.SYNCED,
    updatedAt = System.currentTimeMillis()
)

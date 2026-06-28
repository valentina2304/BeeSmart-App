package com.example.beesmart.data.repository

import com.example.beesmart.data.local.dao.NotificationHistoryDao
import com.example.beesmart.data.local.entity.NotificationHistoryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHistoryRepository @Inject constructor(
    private val dao: NotificationHistoryDao
) {
    fun observeHistory(): Flow<List<NotificationHistoryItem>> =
        dao.observeAll().map { rows -> rows.map { it.toHistoryItem() } }

    fun observeUnreadCount(): Flow<Int> = dao.observeUnreadCount()

    suspend fun saveTaskReminder(
        title: String,
        message: String,
        taskId: String
    ) {
        dao.insert(
            NotificationHistoryEntity(
                title = title,
                message = message,
                type = TYPE_TASK_REMINDER,
                relatedEntityId = taskId,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun saveTreatmentReminder(
        title: String,
        message: String,
        treatmentId: String
    ) {
        dao.insert(
            NotificationHistoryEntity(
                title = title,
                message = message,
                type = TYPE_TREATMENT_REMINDER,
                relatedEntityId = treatmentId,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun markAllRead() {
        dao.markAllRead()
    }

    companion object {
        const val TYPE_TASK_REMINDER = "TASK_REMINDER"
        const val TYPE_TREATMENT_REMINDER = "TREATMENT_REMINDER"
    }
}

private fun NotificationHistoryEntity.toHistoryItem(): NotificationHistoryItem =
    NotificationHistoryItem(
        id = id,
        title = title,
        message = message,
        type = type,
        relatedEntityId = relatedEntityId,
        createdAt = createdAt,
        isRead = isRead
    )

data class NotificationHistoryItem(
    val id: Long,
    val title: String,
    val message: String,
    val type: String,
    val relatedEntityId: String?,
    val createdAt: Long,
    val isRead: Boolean
)

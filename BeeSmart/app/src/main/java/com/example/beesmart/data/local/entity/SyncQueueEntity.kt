package com.example.beesmart.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operationType: String,  // CREATE, UPDATE, DELETE, COMPLETE, UNCOMPLETE
    val entityType: String,     // APIARY, HIVE, TASK, TREATMENT, EXTRACTION
    val entityLocalId: String,
    val entityServerId: String?,
    val payload: String,
    val retryCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

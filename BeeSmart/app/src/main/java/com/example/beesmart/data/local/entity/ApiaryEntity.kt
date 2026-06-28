package com.example.beesmart.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.beesmart.network.models.ApiaryResponse

@Entity(tableName = "apiaries")
data class ApiaryEntity(
    @PrimaryKey val localId: String,
    val serverId: String?,
    val name: String,
    val description: String?,
    val location: String?,
    val hiveCount: Int = 0,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toApiaryResponse() = ApiaryResponse(
        id = serverId ?: localId,
        userId = "",
        name = name,
        description = description,
        location = location,
        hiveCount = hiveCount,
        createdAt = "",
        updatedAt = updatedAt.toString()
    )
}

fun ApiaryResponse.toEntity() = ApiaryEntity(
    localId = id,
    serverId = id,
    name = name,
    description = description,
    location = location,
    hiveCount = hiveCount,
    syncStatus = SyncStatus.SYNCED,
    updatedAt = System.currentTimeMillis()
)

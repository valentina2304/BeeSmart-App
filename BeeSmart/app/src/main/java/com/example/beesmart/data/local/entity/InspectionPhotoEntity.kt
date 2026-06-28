package com.example.beesmart.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.beesmart.network.models.InspectionPhotoResponse

@Entity(
    tableName = "inspection_photos",
    indices = [
        Index("serverId"),
        Index("inspectionLocalId"),
        Index("inspectionServerId")
    ]
)
data class InspectionPhotoEntity(
    @PrimaryKey val localId: String,
    val serverId: String?,
    val inspectionLocalId: String,
    val inspectionServerId: String?,
    val photoUrl: String,
    val description: String?,
    val createdAt: String,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toResponse(): InspectionPhotoResponse = InspectionPhotoResponse(
        id = serverId ?: localId,
        inspectionId = inspectionServerId ?: inspectionLocalId,
        photoUrl = photoUrl,
        description = description,
        createdAt = createdAt
    )
}

fun InspectionPhotoResponse.toEntity(inspectionLocalId: String, inspectionServerId: String?) =
    InspectionPhotoEntity(
        localId = id,
        serverId = id,
        inspectionLocalId = inspectionLocalId,
        inspectionServerId = inspectionServerId ?: inspectionId,
        photoUrl = photoUrl,
        description = description,
        createdAt = createdAt,
        syncStatus = SyncStatus.SYNCED
    )

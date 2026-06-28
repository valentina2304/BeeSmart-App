package com.example.beesmart.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.beesmart.network.models.ExtractionType
import com.example.beesmart.network.models.HiveExtraction

@Entity(tableName = "extractions")
data class ExtractionEntity(
    @PrimaryKey val localId: String,
    val serverId: String?,
    val hiveLocalId: String,
    val hiveServerId: String?,
    val apiaryId: String,
    val extractionDate: String,
    val type: String,
    val quantity: Double,
    val unit: String,
    val notes: String?,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toHiveExtraction() = HiveExtraction(
        id = serverId ?: localId,
        hiveId = hiveServerId ?: hiveLocalId,
        apiaryId = apiaryId,
        extractionDate = extractionDate,
        type = ExtractionType.valueOf(type),
        quantity = quantity,
        unit = unit,
        notes = notes,
        createdAt = "",
        updatedAt = updatedAt.toString()
    )
}

fun HiveExtraction.toEntity() = ExtractionEntity(
    localId = id,
    serverId = id,
    hiveLocalId = hiveId,
    hiveServerId = hiveId,
    apiaryId = apiaryId,
    extractionDate = extractionDate,
    type = type.name,
    quantity = quantity,
    unit = unit,
    notes = notes,
    syncStatus = SyncStatus.SYNCED,
    updatedAt = System.currentTimeMillis()
)

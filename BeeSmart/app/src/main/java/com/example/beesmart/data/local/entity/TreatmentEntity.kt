package com.example.beesmart.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.beesmart.network.models.HiveTreatment
import com.example.beesmart.network.models.TreatmentType

@Entity(tableName = "treatments")
data class TreatmentEntity(
    @PrimaryKey val localId: String,
    val serverId: String?,
    val hiveLocalId: String,
    val hiveServerId: String?,
    val apiaryId: String,
    val treatmentDate: String,
    val type: String,
    val productName: String,
    val substance: String?,
    val dosage: String?,
    val notes: String?,
    val nextTreatmentDate: String?,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toHiveTreatment() = HiveTreatment(
        id = serverId ?: localId,
        hiveId = hiveServerId ?: hiveLocalId,
        apiaryId = apiaryId,
        treatmentDate = treatmentDate,
        type = TreatmentType.valueOf(type),
        productName = productName,
        substance = substance,
        dosage = dosage,
        notes = notes,
        nextTreatmentDate = nextTreatmentDate,
        createdAt = "",
        updatedAt = updatedAt.toString()
    )
}

fun HiveTreatment.toEntity() = TreatmentEntity(
    localId = id,
    serverId = id,
    hiveLocalId = hiveId,
    hiveServerId = hiveId,
    apiaryId = apiaryId,
    treatmentDate = treatmentDate,
    type = type.name,
    productName = productName,
    substance = substance,
    dosage = dosage,
    notes = notes,
    nextTreatmentDate = nextTreatmentDate,
    syncStatus = SyncStatus.SYNCED,
    updatedAt = System.currentTimeMillis()
)

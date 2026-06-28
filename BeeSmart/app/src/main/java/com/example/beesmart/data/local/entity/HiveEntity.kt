package com.example.beesmart.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.beesmart.network.models.HiveResponse
import com.example.beesmart.network.models.HiveStatus
import com.example.beesmart.network.models.HiveType

@Entity(tableName = "hives")
data class HiveEntity(
    @PrimaryKey val localId: String,
    val serverId: String?,
    val apiaryLocalId: String,
    val apiaryServerId: String?,
    val apiaryName: String,
    val name: String,
    val type: String,
    val status: String,
    val notes: String?,
    val syncStatus: SyncStatus = SyncStatus.SYNCED,
    val updatedAt: Long = System.currentTimeMillis(),
    val reginaPrezenta: Boolean = false,
    val varstaRegina: Int = 0,
    val rameAlbine: Int = 0,
    val ramePuiet: Int = 0,
    val rameMiere: Int = 0,
    val ultimaInspectie: String? = null
) {
    fun toHiveResponse() = HiveResponse(
        id = serverId ?: localId,
        apiaryId = apiaryServerId ?: apiaryLocalId,
        apiaryName = apiaryName,
        name = name,
        type = HiveType.valueOf(type),
        status = HiveStatus.valueOf(status),
        notes = notes,
        reginaPrezenta = reginaPrezenta,
        varstaRegina = varstaRegina,
        rameAlbine = rameAlbine,
        ramePuiet = ramePuiet,
        rameMiere = rameMiere,
        ultimaInspectie = ultimaInspectie,
        createdAt = "",
        updatedAt = updatedAt.toString()
    )
}

fun HiveResponse.toEntity() = HiveEntity(
    localId = id,
    serverId = id,
    apiaryLocalId = apiaryId,
    apiaryServerId = apiaryId,
    apiaryName = apiaryName,
    name = name,
    type = type.name,
    status = status.name,
    notes = notes,
    reginaPrezenta = reginaPrezenta,
    varstaRegina = varstaRegina,
    rameAlbine = rameAlbine,
    ramePuiet = ramePuiet,
    rameMiere = rameMiere,
    ultimaInspectie = ultimaInspectie,
    syncStatus = SyncStatus.SYNCED,
    updatedAt = System.currentTimeMillis()
)

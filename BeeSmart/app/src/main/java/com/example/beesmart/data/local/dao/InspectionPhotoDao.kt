package com.example.beesmart.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.beesmart.data.local.entity.InspectionPhotoEntity

@Dao
interface InspectionPhotoDao {

    @Query(
        """
        SELECT * FROM inspection_photos
        WHERE (inspectionLocalId = :inspectionId OR inspectionServerId = :inspectionId)
          AND syncStatus != 'PENDING_DELETE'
        ORDER BY createdAt
        """
    )
    suspend fun getByInspectionId(inspectionId: String): List<InspectionPhotoEntity>

    @Query("SELECT * FROM inspection_photos WHERE localId = :id OR serverId = :id LIMIT 1")
    suspend fun getById(id: String): InspectionPhotoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: InspectionPhotoEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<InspectionPhotoEntity>)

    @Update
    suspend fun update(entity: InspectionPhotoEntity)

    @Query("UPDATE inspection_photos SET inspectionServerId = :serverId WHERE inspectionLocalId = :localId")
    suspend fun bindInspectionServerId(localId: String, serverId: String)

    @Query("DELETE FROM inspection_photos WHERE localId = :id OR serverId = :id")
    suspend fun deleteById(id: String)

    @Query(
        """
        DELETE FROM inspection_photos
        WHERE (inspectionLocalId = :inspectionId OR inspectionServerId = :inspectionId)
          AND syncStatus = 'SYNCED'
        """
    )
    suspend fun deleteSyncedByInspectionId(inspectionId: String)
}

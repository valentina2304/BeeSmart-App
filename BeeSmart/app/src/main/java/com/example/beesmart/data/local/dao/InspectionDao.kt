package com.example.beesmart.data.local.dao

import androidx.room.*
import com.example.beesmart.data.local.entity.InspectionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InspectionDao {

    @Query("""
        SELECT * FROM inspections
        WHERE syncStatus != 'PENDING_DELETE'
        ORDER BY inspectionDate DESC
    """)
    suspend fun getAll(): List<InspectionEntity>

    @Query("""
        SELECT * FROM inspections
        WHERE syncStatus != 'PENDING_DELETE'
        ORDER BY inspectionDate DESC
    """)
    fun observeAll(): Flow<List<InspectionEntity>>

    @Query("""
        SELECT * FROM inspections
        WHERE apiaryId = :apiaryId AND syncStatus != 'PENDING_DELETE'
        ORDER BY inspectionDate DESC
    """)
    suspend fun getByApiaryId(apiaryId: String): List<InspectionEntity>

    @Query("""
        SELECT * FROM inspections
        WHERE apiaryId = :apiaryId AND syncStatus != 'PENDING_DELETE'
        ORDER BY inspectionDate DESC
    """)
    fun observeByApiaryId(apiaryId: String): Flow<List<InspectionEntity>>

    @Query("""
        SELECT * FROM inspections
        WHERE (hiveLocalId = :hiveId OR hiveServerId = :hiveId)
          AND syncStatus != 'PENDING_DELETE'
        ORDER BY inspectionDate DESC
    """)
    suspend fun getByHiveId(hiveId: String): List<InspectionEntity>

    @Query("""
        SELECT * FROM inspections
        WHERE (hiveLocalId = :hiveId OR hiveServerId = :hiveId)
          AND syncStatus != 'PENDING_DELETE'
        ORDER BY inspectionDate DESC
    """)
    fun observeByHiveId(hiveId: String): Flow<List<InspectionEntity>>

    @Query("SELECT * FROM inspections WHERE localId = :localId OR serverId = :localId LIMIT 1")
    suspend fun getByLocalId(localId: String): InspectionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: InspectionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<InspectionEntity>)

    @Update
    suspend fun update(entity: InspectionEntity)

    @Query("DELETE FROM inspections WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)

    @Query("DELETE FROM inspections WHERE syncStatus = 'SYNCED'")
    suspend fun deleteAllSynced()

    @Query("DELETE FROM inspections WHERE apiaryId = :apiaryId AND syncStatus = 'SYNCED'")
    suspend fun deleteSyncedByApiaryId(apiaryId: String)

    @Query("DELETE FROM inspections WHERE (hiveLocalId = :hiveId OR hiveServerId = :hiveId) AND syncStatus = 'SYNCED'")
    suspend fun deleteSyncedByHiveId(hiveId: String)
}

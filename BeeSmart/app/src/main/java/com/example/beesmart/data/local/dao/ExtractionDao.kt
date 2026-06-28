package com.example.beesmart.data.local.dao

import androidx.room.*
import com.example.beesmart.data.local.entity.ExtractionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExtractionDao {

    @Query("""
        SELECT * FROM extractions
        WHERE (hiveLocalId = :hiveId OR hiveServerId = :hiveId)
          AND syncStatus != 'PENDING_DELETE'
        ORDER BY extractionDate DESC
    """)
    suspend fun getByHiveId(hiveId: String): List<ExtractionEntity>

    @Query("""
        SELECT * FROM extractions
        WHERE (hiveLocalId = :hiveId OR hiveServerId = :hiveId)
          AND syncStatus != 'PENDING_DELETE'
        ORDER BY extractionDate DESC
    """)
    fun observeByHiveId(hiveId: String): Flow<List<ExtractionEntity>>

    @Query("SELECT * FROM extractions WHERE syncStatus != 'PENDING_DELETE' ORDER BY extractionDate DESC")
    suspend fun getAll(): List<ExtractionEntity>

    @Query("SELECT * FROM extractions WHERE syncStatus != 'PENDING_DELETE' ORDER BY extractionDate DESC")
    fun observeAll(): Flow<List<ExtractionEntity>>

    @Query("SELECT * FROM extractions WHERE localId = :localId")
    suspend fun getByLocalId(localId: String): ExtractionEntity?

    @Query("SELECT * FROM extractions WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: String): ExtractionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ExtractionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ExtractionEntity>)

    @Update
    suspend fun update(entity: ExtractionEntity)

    @Query("DELETE FROM extractions WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)

    @Query("DELETE FROM extractions WHERE (hiveLocalId = :hiveId OR hiveServerId = :hiveId) AND syncStatus = 'SYNCED'")
    suspend fun deleteSyncedByHiveId(hiveId: String)

    @Query("DELETE FROM extractions WHERE syncStatus = 'SYNCED'")
    suspend fun deleteAllSynced()
}

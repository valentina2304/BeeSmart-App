package com.example.beesmart.data.local.dao

import androidx.room.*
import com.example.beesmart.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {

    @Query("SELECT * FROM sync_queue ORDER BY createdAt ASC")
    suspend fun getAll(): List<SyncQueueEntity>

    @Query(
        """
        SELECT * FROM sync_queue
        WHERE operationType = :operationType
          AND entityType = :entityType
          AND entityLocalId = :localId
        ORDER BY createdAt DESC
        LIMIT 1
        """
    )
    suspend fun getLatestForEntity(
        operationType: String,
        entityType: String,
        localId: String
    ): SyncQueueEntity?

    @Insert
    suspend fun insert(entity: SyncQueueEntity): Long

    @Update
    suspend fun update(entity: SyncQueueEntity)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM sync_queue WHERE entityLocalId = :localId")
    suspend fun deleteByEntityLocalId(localId: String)

    @Query("SELECT COUNT(*) FROM sync_queue")
    suspend fun getPendingCount(): Int

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE retryCount >= :maxRetries")
    suspend fun getFailedCount(maxRetries: Int): Int
}

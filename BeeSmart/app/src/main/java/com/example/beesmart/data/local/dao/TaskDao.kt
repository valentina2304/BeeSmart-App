package com.example.beesmart.data.local.dao

import androidx.room.*
import com.example.beesmart.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    @Query("SELECT * FROM tasks WHERE syncStatus != 'PENDING_DELETE' ORDER BY CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END, dueDate ASC")
    suspend fun getAll(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE syncStatus != 'PENDING_DELETE' ORDER BY CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END, dueDate ASC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = 'Pending' AND syncStatus != 'PENDING_DELETE' ORDER BY CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END, dueDate ASC")
    suspend fun getPending(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status = 'Pending' AND syncStatus != 'PENDING_DELETE' ORDER BY CASE WHEN dueDate IS NULL THEN 1 ELSE 0 END, dueDate ASC")
    fun observePending(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE status = 'Pending' AND dueDate IS NOT NULL AND dueDate < :now AND syncStatus != 'PENDING_DELETE'")
    suspend fun getOverdue(now: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE status = 'Pending' AND dueDate IS NOT NULL AND dueDate < :now AND syncStatus != 'PENDING_DELETE'")
    fun observeOverdue(now: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE localId = :localId")
    suspend fun getByLocalId(localId: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<TaskEntity>)

    @Update
    suspend fun update(entity: TaskEntity)

    @Query("DELETE FROM tasks WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)

    @Query("DELETE FROM tasks WHERE syncStatus = 'SYNCED'")
    suspend fun deleteAllSynced()
}

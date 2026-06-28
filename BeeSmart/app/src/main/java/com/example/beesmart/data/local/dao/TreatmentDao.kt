package com.example.beesmart.data.local.dao

import androidx.room.*
import com.example.beesmart.data.local.entity.TreatmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TreatmentDao {

    @Query("""
        SELECT * FROM treatments
        WHERE (hiveLocalId = :hiveId OR hiveServerId = :hiveId)
          AND syncStatus != 'PENDING_DELETE'
        ORDER BY treatmentDate DESC
    """)
    suspend fun getByHiveId(hiveId: String): List<TreatmentEntity>

    @Query("""
        SELECT * FROM treatments
        WHERE (hiveLocalId = :hiveId OR hiveServerId = :hiveId)
          AND syncStatus != 'PENDING_DELETE'
        ORDER BY treatmentDate DESC
    """)
    fun observeByHiveId(hiveId: String): Flow<List<TreatmentEntity>>

    @Query("SELECT * FROM treatments WHERE syncStatus != 'PENDING_DELETE' ORDER BY treatmentDate DESC")
    suspend fun getAll(): List<TreatmentEntity>

    @Query("SELECT * FROM treatments WHERE syncStatus != 'PENDING_DELETE' ORDER BY treatmentDate DESC")
    fun observeAll(): Flow<List<TreatmentEntity>>

    @Query("SELECT * FROM treatments WHERE localId = :localId")
    suspend fun getByLocalId(localId: String): TreatmentEntity?

    @Query("SELECT * FROM treatments WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: String): TreatmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TreatmentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<TreatmentEntity>)

    @Update
    suspend fun update(entity: TreatmentEntity)

    @Query("DELETE FROM treatments WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)

    @Query("DELETE FROM treatments WHERE (hiveLocalId = :hiveId OR hiveServerId = :hiveId) AND syncStatus = 'SYNCED'")
    suspend fun deleteSyncedByHiveId(hiveId: String)

    @Query("DELETE FROM treatments WHERE syncStatus = 'SYNCED'")
    suspend fun deleteAllSynced()
}

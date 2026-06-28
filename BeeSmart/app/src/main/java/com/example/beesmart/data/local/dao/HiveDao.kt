package com.example.beesmart.data.local.dao

import androidx.room.*
import com.example.beesmart.data.local.entity.HiveEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HiveDao {

    @Query("SELECT * FROM hives WHERE syncStatus != 'PENDING_DELETE' ORDER BY name")
    suspend fun getAll(): List<HiveEntity>

    @Query("""
        SELECT * FROM hives
        WHERE (apiaryLocalId = :apiaryId OR apiaryServerId = :apiaryId)
          AND syncStatus != 'PENDING_DELETE'
        ORDER BY name
    """)
    suspend fun getByApiaryId(apiaryId: String): List<HiveEntity>

    @Query("""
        SELECT * FROM hives
        WHERE (apiaryLocalId = :apiaryId OR apiaryServerId = :apiaryId)
          AND syncStatus != 'PENDING_DELETE'
        ORDER BY name
    """)
    fun observeByApiaryId(apiaryId: String): Flow<List<HiveEntity>>

    @Query("SELECT * FROM hives WHERE localId = :localId")
    suspend fun getByLocalId(localId: String): HiveEntity?

    @Query("SELECT * FROM hives WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: String): HiveEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: HiveEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<HiveEntity>)

    @Update
    suspend fun update(entity: HiveEntity)

    @Query("DELETE FROM hives WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)

    @Query("DELETE FROM hives WHERE (apiaryLocalId = :apiaryId OR apiaryServerId = :apiaryId) AND syncStatus = 'SYNCED'")
    suspend fun deleteSyncedByApiaryId(apiaryId: String)

    @Query("DELETE FROM hives WHERE syncStatus = 'SYNCED'")
    suspend fun deleteAllSynced()
}

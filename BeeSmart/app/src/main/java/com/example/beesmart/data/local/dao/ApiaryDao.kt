package com.example.beesmart.data.local.dao

import androidx.room.*
import com.example.beesmart.data.local.entity.ApiaryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiaryDao {

    @Query("SELECT * FROM apiaries WHERE syncStatus != 'PENDING_DELETE' ORDER BY name")
    suspend fun getAll(): List<ApiaryEntity>

    @Query("SELECT * FROM apiaries WHERE syncStatus != 'PENDING_DELETE' ORDER BY name")
    fun observeAll(): Flow<List<ApiaryEntity>>

    @Query("SELECT * FROM apiaries WHERE localId = :localId")
    suspend fun getByLocalId(localId: String): ApiaryEntity?

    @Query("SELECT * FROM apiaries WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: String): ApiaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ApiaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ApiaryEntity>)

    @Update
    suspend fun update(entity: ApiaryEntity)

    @Query("DELETE FROM apiaries WHERE localId = :localId")
    suspend fun deleteByLocalId(localId: String)

    @Query("DELETE FROM apiaries WHERE syncStatus = 'SYNCED'")
    suspend fun deleteAllSynced()
}

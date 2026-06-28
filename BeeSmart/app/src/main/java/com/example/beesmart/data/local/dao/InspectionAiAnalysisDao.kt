package com.example.beesmart.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.beesmart.data.local.entity.InspectionAiAnalysisEntity

@Dao
interface InspectionAiAnalysisDao {

    @Query("SELECT * FROM inspection_ai_analysis ORDER BY computedAt DESC")
    suspend fun getAll(): List<InspectionAiAnalysisEntity>

    /** Most recent analysis for the given inspection — match by either localId or serverId. */
    @Query(
        "SELECT * FROM inspection_ai_analysis " +
            "WHERE inspectionLocalId = :id OR inspectionServerId = :id " +
            "ORDER BY computedAt DESC LIMIT 1"
    )
    suspend fun getLatestForInspection(id: String): InspectionAiAnalysisEntity?

    /** Replace the analysis for an inspection (we only keep the most recent). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: InspectionAiAnalysisEntity): Long

    /**
     * After an inspection's CREATE syncs, the `serverId` becomes known. Update the
     * analysis row so future lookups by serverId still resolve.
     */
    @Query("UPDATE inspection_ai_analysis SET inspectionServerId = :serverId WHERE inspectionLocalId = :localId")
    suspend fun bindServerId(localId: String, serverId: String)

    @Query("DELETE FROM inspection_ai_analysis WHERE inspectionLocalId = :localId OR inspectionServerId = :localId")
    suspend fun deleteForInspection(localId: String)
}

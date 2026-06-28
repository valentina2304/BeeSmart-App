package com.example.beesmart.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Locally-cached AI cell-detection analysis tied to an inspection.
 *
 * Lives in its own table on purpose: when the inspections list is refreshed from
 * the server (`deleteAllSynced` + `insertAll`), inspection rows can be replaced
 * with `localId == serverId` versions. The analysis row keeps its own primary
 * key and stores BOTH the original local id and the server id so we can find
 * it again from either side.
 *
 * The analysis itself is server-agnostic — it's a snapshot of the AI counts
 * plus a derived verdict, computed once at save time. The raw map is kept as
 * a JSON blob so future analyzers can recompute metrics without re-running the
 * (paid) AI call.
 */
@Entity(
    tableName = "inspection_ai_analysis",
    indices = [
        Index("inspectionLocalId", unique = true),
        Index("inspectionServerId")
    ]
)
data class InspectionAiAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val inspectionLocalId: String,
    val inspectionServerId: String?,
    /** Raw `Map<String, Int>` returned by the AI, serialised as JSON. */
    val rawCountsJson: String,
    /** Per-cell coordinates, class and confidence, serialised as JSON. */
    val cellDetectionsJson: String = "[]",
    /** Optional message from the AI service (e.g. confidence note). */
    val message: String?,
    /** Epoch millis when the analysis was computed. */
    val computedAt: Long
)

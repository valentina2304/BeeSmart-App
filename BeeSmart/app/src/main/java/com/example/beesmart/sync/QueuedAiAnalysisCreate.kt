package com.example.beesmart.sync

import com.example.beesmart.network.models.SaveInspectionAiAnalysisRequest

data class QueuedAiAnalysisCreate(
    val inspectionLocalId: String,
    val request: SaveInspectionAiAnalysisRequest
)

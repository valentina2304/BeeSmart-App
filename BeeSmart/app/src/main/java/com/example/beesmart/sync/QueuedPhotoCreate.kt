package com.example.beesmart.sync

import com.example.beesmart.network.models.AddInspectionPhotoRequest

data class QueuedPhotoCreate(
    val inspectionLocalId: String,
    val request: AddInspectionPhotoRequest
)

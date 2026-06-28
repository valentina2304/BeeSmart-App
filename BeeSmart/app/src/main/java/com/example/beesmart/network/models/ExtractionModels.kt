package com.example.beesmart.network.models

import java.io.Serializable

enum class ExtractionType {
    Honey,
    Pollen,
    Propolis,
    RoyalJelly,
    Wax,
    Other
}

data class HiveExtraction(
    val id: String,
    val hiveId: String,
    val apiaryId: String,
    val extractionDate: String, // ISO date string
    val type: ExtractionType,
    val quantity: Double,
    val unit: String, // "kg", "g", etc.
    val notes: String?,
    val createdAt: String,
    val updatedAt: String
) : Serializable

data class CreateExtractionRequest(
    val hiveId: String,
    val extractionDate: String,
    val type: ExtractionType,
    val quantity: Double,
    val unit: String,
    val notes: String?
)

data class UpdateExtractionRequest(
    val extractionDate: String,
    val type: ExtractionType,
    val quantity: Double,
    val unit: String,
    val notes: String?
)

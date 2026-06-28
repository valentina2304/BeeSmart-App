package com.example.beesmart.network.models
import java.io.Serializable
enum class TreatmentType {
    Varroa,
    Nosema,
    Fungal,
    Viral,
    Bacterial,
    Preventive,
    Other
}
data class HiveTreatment(
    val id: String,
    val hiveId: String,
    val apiaryId: String,
    val treatmentDate: String, // ISO date string
    val type: TreatmentType,
    val productName: String,
    val substance: String?,
    val dosage: String?,
    val notes: String?,
    val nextTreatmentDate: String?,
    val createdAt: String,
    val updatedAt: String
) : Serializable
data class CreateTreatmentRequest(
    val hiveId: String,
    val treatmentDate: String,
    val type: TreatmentType,
    val productName: String,
    val substance: String?,
    val dosage: String?,
    val notes: String?,
    val nextTreatmentDate: String?
)
data class UpdateTreatmentRequest(
    val treatmentDate: String,
    val type: TreatmentType,
    val productName: String,
    val substance: String?,
    val dosage: String?,
    val notes: String?,
    val nextTreatmentDate: String?
)


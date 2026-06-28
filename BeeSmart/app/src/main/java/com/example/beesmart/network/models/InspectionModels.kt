package com.example.beesmart.network.models


// ==================== REQUEST MODELS ====================

/**
 * Request model for creating a new inspection.
 */
data class CreateInspectionRequest(
    val hiveId: String,
    val inspectionDate: String, // ISO 8601 format: "2025-11-25T10:00:00Z"
    val temperature: Double? = null,
    val framesCount: Int? = null,
    val broodFrames: Int? = null,
    val honeyFrames: Int? = null,
    val pollenFrames: Int? = null,
    val queenSeen: Boolean = false,
    val eggsSeen: Boolean = false,
    val larvaeSeen: Boolean = false,
    val queenCellsSeen: Boolean = false,
    val queenCellsWithEggs: Boolean = false,
    val beardingAtEntrance: Boolean = false,
    val spaceNeeded: Boolean = false,
    val broodPattern: String? = null,
    val honeyCappingPercent: Int? = null,
    val feedingGiven: Boolean = false,
    val waterAvailable: Boolean = false,
    val moistureOrMold: Boolean = false,
    val deadBeesAtEntrance: Boolean = false,
    val unusualBehavior: Boolean = false,
    val temperament: String? = null,
    val oldCombsToReplace: Int? = null,
    val notes: String? = null
)

/**
 * Request model for updating an inspection.
 */
data class UpdateInspectionRequest(
    val inspectionDate: String,
    val temperature: Double? = null,
    val framesCount: Int? = null,
    val broodFrames: Int? = null,
    val honeyFrames: Int? = null,
    val pollenFrames: Int? = null,
    val queenSeen: Boolean = false,
    val eggsSeen: Boolean = false,
    val larvaeSeen: Boolean = false,
    val queenCellsSeen: Boolean = false,
    val queenCellsWithEggs: Boolean = false,
    val beardingAtEntrance: Boolean = false,
    val spaceNeeded: Boolean = false,
    val broodPattern: String? = null,
    val honeyCappingPercent: Int? = null,
    val feedingGiven: Boolean = false,
    val waterAvailable: Boolean = false,
    val moistureOrMold: Boolean = false,
    val deadBeesAtEntrance: Boolean = false,
    val unusualBehavior: Boolean = false,
    val temperament: String? = null,
    val oldCombsToReplace: Int? = null,
    val notes: String? = null
)

/**
 * Request model for adding a photo.
 */
data class AddInspectionPhotoRequest(
    val photoUrl: String,
    val description: String? = null
)

/**
 * Request model for updating a photo's description.
 */
data class UpdateInspectionPhotoRequest(
    val description: String? = null
)

/**
 * Request model for analyzing the cells in a photo.
 */
data class AnalyzeCellsRequest(
    val imageBase64: String
)

data class SaveInspectionAiAnalysisRequest(
    val results: Map<String, Int>,
    val status: String = "success",
    val message: String? = null,
    val cellDetections: List<CellDetection> = emptyList()
)

// ==================== RESPONSE MODELS ====================

/**
 * Response model for the inspection list.
 */
data class InspectionResponse(
    val id: String,
    val hiveId: String,
    val hiveName: String,
    val apiaryId: String,
    val apiaryName: String,
    val inspectionDate: String,
    val temperature: Double? = null,
    val framesCount: Int? = null,
    val broodFrames: Int? = null,
    val honeyFrames: Int? = null,
    val pollenFrames: Int? = null,
    val queenSeen: Boolean = false,
    val eggsSeen: Boolean = false,
    val larvaeSeen: Boolean = false,
    val photosCount: Int = 0,
    val createdAt: String,
    val updatedAt: String,
    val queenCellsSeen: Boolean = false,
    val queenCellsWithEggs: Boolean = false,
    val beardingAtEntrance: Boolean = false,
    val spaceNeeded: Boolean = false,
    val broodPattern: String? = null,
    val honeyCappingPercent: Int? = null,
    val feedingGiven: Boolean = false,
    val waterAvailable: Boolean = false,
    val moistureOrMold: Boolean = false,
    val deadBeesAtEntrance: Boolean = false,
    val unusualBehavior: Boolean = false,
    val temperament: String? = null,
    val oldCombsToReplace: Int? = null,
    val notes: String? = null
)

/**
 * Response model for the full details of an inspection (including photos).
 */
data class InspectionDetailResponse(
    val id: String,
    val hiveId: String,
    val hiveName: String,
    val apiaryId: String,
    val apiaryName: String,
    val inspectionDate: String,
    val temperature: Double? = null,
    val framesCount: Int? = null,
    val broodFrames: Int? = null,
    val honeyFrames: Int? = null,
    val pollenFrames: Int? = null,
    val queenSeen: Boolean = false,
    val eggsSeen: Boolean = false,
    val larvaeSeen: Boolean = false,
    val photos: List<InspectionPhotoResponse> = emptyList(),
    val createdAt: String,
    val updatedAt: String,
    val queenCellsSeen: Boolean = false,
    val queenCellsWithEggs: Boolean = false,
    val beardingAtEntrance: Boolean = false,
    val spaceNeeded: Boolean = false,
    val broodPattern: String? = null,
    val honeyCappingPercent: Int? = null,
    val feedingGiven: Boolean = false,
    val waterAvailable: Boolean = false,
    val moistureOrMold: Boolean = false,
    val deadBeesAtEntrance: Boolean = false,
    val unusualBehavior: Boolean = false,
    val temperament: String? = null,
    val oldCombsToReplace: Int? = null,
    val notes: String? = null
)

/**
 * Response model for an inspection photo.
 */
data class InspectionPhotoResponse(
    val id: String,
    val inspectionId: String,
    val photoUrl: String,
    val description: String? = null,
    val createdAt: String
)

/**
 * Response model for the cell analysis.
 */
data class AnalyzeCellsResponse(
    val status: String,
    val results: Map<String, Int> = emptyMap(),
    val message: String? = null,
    val quality: AnalyzeCellsQuality? = null,
    val cellDetections: List<CellDetection> = emptyList()
)

data class CellDetection(
    val x: Int,
    val y: Int,
    val radius: Int,
    val normalizedX: Double,
    val normalizedY: Double,
    val normalizedRadius: Double,
    val className: String,
    val confidence: Double
)

data class AnalyzeCellsQuality(
    val width: Int? = null,
    val height: Int? = null,
    val blurVariance: Double? = null,
    val brightness: Double? = null,
    val contrast: Double? = null,
    val segmentationEnabled: Boolean? = null,
    val detectedCells: Int? = null,
    val classifiedCells: Int? = null,
    val lowConfidenceCells: Int? = null,
    val lowConfidenceRatio: Double? = null,
    val meanConfidence: Double? = null,
    val combMaskCoverage: Double? = null,
    val combBoundingBox: CombBoundingBox? = null
)

data class CombBoundingBox(
    val x: Int? = null,
    val y: Int? = null,
    val width: Int? = null,
    val height: Int? = null
)

data class InspectionAiAnalysisResponse(
    val id: String,
    val inspectionId: String,
    val hiveId: String,
    val apiaryId: String,
    val inspectionDate: String,
    val status: String,
    val results: Map<String, Int> = emptyMap(),
    val message: String? = null,
    val totalCells: Int,
    val cappedBroodCells: Int,
    val larvaeCells: Int,
    val eggsCells: Int,
    val honeyCells: Int,
    val pollenCells: Int,
    val emptyCells: Int,
    val otherCells: Int,
    val broodCells: Int,
    val storesCells: Int,
    val broodDensity: Double? = null,
    val larvaeToCappedRatio: Double? = null,
    val storesRatio: Double? = null,
    val createdAt: String,
    val cellDetections: List<CellDetection> = emptyList()
)

package com.example.beesmart.network

import com.example.beesmart.network.models.*
import retrofit2.Response
import retrofit2.http.*

/**
 * Retrofit API interface for inspection operations.
 */
interface InspectionApi {

    // ==================== INSPECTION ENDPOINTS ====================

    @GET("inspections")
    suspend fun getAllInspections(): Response<List<InspectionResponse>>

    @GET("inspections/apiary/{apiaryId}")
    suspend fun getInspectionsByApiaryId(@Path("apiaryId") apiaryId: String): Response<List<InspectionResponse>>

    @GET("inspections/hive/{hiveId}")
    suspend fun getInspectionsByHiveId(@Path("hiveId") hiveId: String): Response<List<InspectionResponse>>

    @GET("inspections/{id}")
    suspend fun getInspectionById(@Path("id") id: String): Response<InspectionDetailResponse>

    @POST("inspections")
    suspend fun createInspection(@Body request: CreateInspectionRequest): Response<InspectionResponse>

    @PUT("inspections/{id}")
    suspend fun updateInspection(
        @Path("id") id: String,
        @Body request: UpdateInspectionRequest
    ): Response<InspectionResponse>

    @DELETE("inspections/{id}")
    suspend fun deleteInspection(@Path("id") id: String): Response<Unit>

    // ==================== PHOTO ENDPOINTS ====================

    @POST("inspections/{inspectionId}/photos")
    suspend fun addPhoto(
        @Path("inspectionId") inspectionId: String,
        @Body request: AddInspectionPhotoRequest
    ): Response<InspectionPhotoResponse>

    @PUT("inspections/photos/{photoId}")
    suspend fun updatePhoto(
        @Path("photoId") photoId: String,
        @Body request: UpdateInspectionPhotoRequest
    ): Response<InspectionPhotoResponse>

    @DELETE("inspections/photos/{photoId}")
    suspend fun deletePhoto(@Path("photoId") photoId: String): Response<Unit>

    // ==================== AI ANALYSIS ENDPOINTS ====================

    @POST("inspections/analyze-cells")
    suspend fun analyzeCells(
        @Body request: AnalyzeCellsRequest
    ): Response<AnalyzeCellsResponse>

    @POST("inspections/{inspectionId}/ai-analyses")
    suspend fun saveAiAnalysis(
        @Path("inspectionId") inspectionId: String,
        @Body request: SaveInspectionAiAnalysisRequest
    ): Response<InspectionAiAnalysisResponse>

    @GET("inspections/hive/{hiveId}/ai-analyses")
    suspend fun getAiAnalysesByHiveId(
        @Path("hiveId") hiveId: String
    ): Response<List<InspectionAiAnalysisResponse>>
}

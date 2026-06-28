package com.example.beesmart.network

import com.example.beesmart.network.models.CreateExtractionRequest
import com.example.beesmart.network.models.HiveExtraction
import com.example.beesmart.network.models.UpdateExtractionRequest
import retrofit2.Response
import retrofit2.http.*

interface ExtractionApi {
    @GET("api/extractions")
    suspend fun getAllExtractions(): Response<List<HiveExtraction>>

    @GET("api/extractions/{id}")
    suspend fun getExtractionById(@Path("id") id: String): Response<HiveExtraction>

    @GET("api/extractions/hive/{hiveId}")
    suspend fun getExtractionsByHiveId(@Path("hiveId") hiveId: String): Response<List<HiveExtraction>>

    @GET("api/extractions/apiary/{apiaryId}")
    suspend fun getExtractionsByApiaryId(@Path("apiaryId") apiaryId: String): Response<List<HiveExtraction>>

    @POST("api/extractions")
    suspend fun createExtraction(@Body request: CreateExtractionRequest): Response<HiveExtraction>

    @PUT("api/extractions/{id}")
    suspend fun updateExtraction(@Path("id") id: String, @Body request: UpdateExtractionRequest): Response<HiveExtraction>

    @DELETE("api/extractions/{id}")
    suspend fun deleteExtraction(@Path("id") id: String): Response<Unit>
}

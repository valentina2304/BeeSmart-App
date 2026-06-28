package com.example.beesmart.network
import com.example.beesmart.network.models.CreateTreatmentRequest
import com.example.beesmart.network.models.HiveTreatment
import com.example.beesmart.network.models.UpdateTreatmentRequest
import retrofit2.Response
import retrofit2.http.*
interface TreatmentApi {
    @GET("api/treatments")
    suspend fun getAllTreatments(): Response<List<HiveTreatment>>
    @GET("api/treatments/{id}")
    suspend fun getTreatmentById(@Path("id") id: String): Response<HiveTreatment>
    @GET("api/treatments/hive/{hiveId}")
    suspend fun getTreatmentsByHiveId(@Path("hiveId") hiveId: String): Response<List<HiveTreatment>>
    @GET("api/treatments/apiary/{apiaryId}")
    suspend fun getTreatmentsByApiaryId(@Path("apiaryId") apiaryId: String): Response<List<HiveTreatment>>
    @POST("api/treatments")
    suspend fun createTreatment(@Body request: CreateTreatmentRequest): Response<HiveTreatment>
    @PUT("api/treatments/{id}")
    suspend fun updateTreatment(@Path("id") id: String, @Body request: UpdateTreatmentRequest): Response<HiveTreatment>
    @DELETE("api/treatments/{id}")
    suspend fun deleteTreatment(@Path("id") id: String): Response<Unit>
}

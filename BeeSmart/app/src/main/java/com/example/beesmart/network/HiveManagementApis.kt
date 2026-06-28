package com.example.beesmart.network

import com.example.beesmart.network.models.*
import retrofit2.Response
import retrofit2.http.*

// ==================== APIARY API ====================

interface ApiaryApi {
    @GET("api/apiaries")
    suspend fun getAllApiaries(): Response<List<ApiaryResponse>>

    @GET("api/apiaries/{id}")
    suspend fun getApiaryById(@Path("id") id: String): Response<ApiaryDetailResponse>

    @POST("api/apiaries")
    suspend fun createApiary(@Body request: CreateApiaryRequest): Response<ApiaryResponse>

    @PUT("api/apiaries/{id}")
    suspend fun updateApiary(
        @Path("id") id: String,
        @Body request: UpdateApiaryRequest
    ): Response<ApiaryResponse>

    @DELETE("api/apiaries/{id}")
    suspend fun deleteApiary(@Path("id") id: String): Response<Unit>
}

// ==================== HIVE API ====================

interface HiveApi {
    @GET("api/hives")
    suspend fun getAllHives(): Response<List<HiveResponse>>

    @GET("api/hives/apiary/{apiaryId}")
    suspend fun getHivesByApiaryId(@Path("apiaryId") apiaryId: String): Response<List<HiveResponse>>

    @GET("api/hives/{id}")
    suspend fun getHiveById(@Path("id") id: String): Response<HiveResponse>

    @POST("api/hives/apiary/{apiaryId}")
    suspend fun createHive(
        @Path("apiaryId") apiaryId: String,
        @Body request: CreateHiveRequest
    ): Response<HiveResponse>

    @PUT("api/hives/{id}")
    suspend fun updateHive(
        @Path("id") id: String,
        @Body request: UpdateHiveRequest
    ): Response<HiveResponse>

    @DELETE("api/hives/{id}")
    suspend fun deleteHive(@Path("id") id: String): Response<Unit>
}

// ==================== TASK API ====================

interface TaskApi {
    @GET("api/tasks")
    suspend fun getAllTasks(): Response<List<TaskResponse>>

    @GET("api/tasks/pending")
    suspend fun getPendingTasks(): Response<List<TaskResponse>>

    @GET("api/tasks/overdue")
    suspend fun getOverdueTasks(): Response<List<TaskResponse>>

    @GET("api/tasks/apiary/{apiaryId}")
    suspend fun getTasksByApiaryId(@Path("apiaryId") apiaryId: String): Response<List<TaskResponse>>

    @GET("api/tasks/hive/{hiveId}")
    suspend fun getTasksByHiveId(@Path("hiveId") hiveId: String): Response<List<TaskResponse>>

    @GET("api/tasks/{id}")
    suspend fun getTaskById(@Path("id") id: String): Response<TaskResponse>

    @POST("api/tasks")
    suspend fun createTask(@Body request: CreateTaskRequest): Response<TaskResponse>

    @PUT("api/tasks/{id}")
    suspend fun updateTask(
        @Path("id") id: String,
        @Body request: UpdateTaskRequest
    ): Response<TaskResponse>

    @POST("api/tasks/{id}/complete")
    suspend fun completeTask(@Path("id") id: String): Response<TaskResponse>

    @POST("api/tasks/{id}/uncomplete")
    suspend fun uncompleteTask(@Path("id") id: String): Response<TaskResponse>

    @DELETE("api/tasks/{id}")
    suspend fun deleteTask(@Path("id") id: String): Response<Unit>
}
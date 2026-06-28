package com.example.beesmart.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.beesmart.data.local.AppDatabase
import com.example.beesmart.data.repository.ApiaryRepository
import com.example.beesmart.data.repository.ExtractionRepository
import com.example.beesmart.data.repository.HiveRepository
import com.example.beesmart.data.repository.InspectionRepository
import com.example.beesmart.data.repository.TaskRepository
import com.example.beesmart.data.repository.TreatmentRepository
import com.example.beesmart.network.ApiaryApi
import com.example.beesmart.network.AuthApi
import com.example.beesmart.network.BackendReachability
import com.example.beesmart.network.ExtractionApi
import com.example.beesmart.network.HiveApi
import com.example.beesmart.network.InspectionApi
import com.example.beesmart.network.TaskApi
import com.example.beesmart.network.TreatmentApi
import com.example.beesmart.sync.ConnectivityObserver
import com.example.beesmart.sync.SyncManager
import com.example.beesmart.utils.SessionManager
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import io.mockk.every
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Reusable harness for end-to-end sync integration tests.
 *
 * Wires up:
 *  - Real in-memory Room DB
 *  - Real Retrofit + OkHttp pointing at MockWebServer
 *  - Real Moshi
 *  - Real repositories
 *  - Real SyncManager
 *  - Mocked ConnectivityObserver / BackendReachability (toggleable per test)
 *
 * Usage:
 *   val h = SyncTestHarness.create()
 *   h.setOffline()
 *   ... call repository ...
 *   h.setOnline()
 *   h.enqueueServerResponse(...)
 *   h.syncManager.processQueue()
 *   ... assertions on h.db / h.mockServer.takeRequest() ...
 *   h.tearDown()
 */
class SyncTestHarness private constructor(
    val context: Context,
    val db: AppDatabase,
    val mockServer: MockWebServer,
    val moshi: Moshi,
    val connectivity: ConnectivityObserver,
    val backendReachability: BackendReachability,
    val apiaryApi: ApiaryApi,
    val hiveApi: HiveApi,
    val taskApi: TaskApi,
    val treatmentApi: TreatmentApi,
    val extractionApi: ExtractionApi,
    val inspectionApi: InspectionApi,
    val apiaryRepository: ApiaryRepository,
    val hiveRepository: HiveRepository,
    val taskRepository: TaskRepository,
    val treatmentRepository: TreatmentRepository,
    val extractionRepository: ExtractionRepository,
    val inspectionRepository: InspectionRepository,
    val syncManager: SyncManager
) {

    fun setOffline() {
        every { connectivity.isCurrentlyOnline() } returns false
        every { backendReachability.isLikelyUnreachable() } returns true
    }

    fun setOnline() {
        every { connectivity.isCurrentlyOnline() } returns true
        every { backendReachability.isLikelyUnreachable() } returns false
    }

    fun tearDown() {
        db.close()
        mockServer.shutdown()
    }

    companion object {
        fun create(): SyncTestHarness {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()

            val mockServer = MockWebServer()
            mockServer.start()

            val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

            val client = OkHttpClient.Builder()
                .connectTimeout(2, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.SECONDS)
                .writeTimeout(2, TimeUnit.SECONDS)
                .build()

            val retrofit = Retrofit.Builder()
                .baseUrl(mockServer.url("/"))
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()

            val apiaryApi = retrofit.create(ApiaryApi::class.java)
            val hiveApi = retrofit.create(HiveApi::class.java)
            val taskApi = retrofit.create(TaskApi::class.java)
            val treatmentApi = retrofit.create(TreatmentApi::class.java)
            val extractionApi = retrofit.create(ExtractionApi::class.java)
            val inspectionApi = retrofit.create(InspectionApi::class.java)
            val authApi = retrofit.create(AuthApi::class.java)

            // SessionManager talks to DataStore — mock it so tests don't need a real Context.
            val sessionManager = mockk<SessionManager>(relaxed = true)

            // Mock connectivity — start offline by default; tests flip as needed.
            val connectivity = mockk<ConnectivityObserver>(relaxed = true)
            every { connectivity.isCurrentlyOnline() } returns false

            val backendReachability = mockk<BackendReachability>(relaxed = true)
            every { backendReachability.isLikelyUnreachable() } returns true

            val apiaryRepository = ApiaryRepository(
                apiaryApi, db.apiaryDao(), db.syncQueueDao(), connectivity, backendReachability, moshi
            )
            val hiveRepository = HiveRepository(
                hiveApi, db.hiveDao(), db.syncQueueDao(), connectivity, backendReachability, moshi
            )
            val taskRepository = TaskRepository(
                taskApi, db.taskDao(), db.syncQueueDao(), connectivity, backendReachability, moshi
            )
            val treatmentRepository = TreatmentRepository(
                treatmentApi, db.treatmentDao(), db.syncQueueDao(), connectivity, backendReachability, moshi
            )
            val extractionRepository = ExtractionRepository(
                extractionApi, db.extractionDao(), db.syncQueueDao(), connectivity, backendReachability, moshi
            )
            val inspectionRepository = InspectionRepository(
                inspectionApi, db.inspectionDao(), db.inspectionPhotoDao(),
                db.inspectionAiAnalysisDao(), db.hiveDao(), db.syncQueueDao(),
                connectivity, backendReachability, moshi
            )

            val syncManager = SyncManager(
                db.apiaryDao(), db.hiveDao(), db.taskDao(),
                db.treatmentDao(), db.extractionDao(), db.inspectionDao(),
                db.inspectionPhotoDao(), db.syncQueueDao(), db.inspectionAiAnalysisDao(),
                apiaryApi, hiveApi, taskApi,
                treatmentApi, extractionApi, inspectionApi,
                authApi, sessionManager,
                moshi
            )

            return SyncTestHarness(
                context, db, mockServer, moshi, connectivity, backendReachability,
                apiaryApi, hiveApi, taskApi, treatmentApi, extractionApi, inspectionApi,
                apiaryRepository, hiveRepository, taskRepository,
                treatmentRepository, extractionRepository, inspectionRepository,
                syncManager
            )
        }
    }
}

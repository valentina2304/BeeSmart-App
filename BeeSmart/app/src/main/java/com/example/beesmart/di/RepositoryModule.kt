package com.example.beesmart.di

import android.content.Context
import com.example.beesmart.data.local.AppDatabase
import com.example.beesmart.data.local.dao.*
import com.example.beesmart.data.repository.*
import com.example.beesmart.network.*
import com.example.beesmart.network.BackendReachability
import com.example.beesmart.sync.ConnectivityObserver
import com.example.beesmart.utils.PhotoManager
import com.example.beesmart.utils.SessionManager
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideAuthRepository(
        @UnauthenticatedClient authApi: AuthApi,
        sessionManager: SessionManager,
        appDatabase: AppDatabase,
        userProfileRepository: UserProfileRepository
    ): AuthRepository = AuthRepository(authApi, sessionManager, appDatabase, userProfileRepository)

    @Provides
    @Singleton
    fun provideHomeRepository(
        sessionManager: SessionManager,
        appDatabase: AppDatabase,
        userProfileRepository: UserProfileRepository,
        moshi: Moshi
    ): HomeRepository = HomeRepository(sessionManager, appDatabase, userProfileRepository, moshi)

    @Provides
    @Singleton
    fun provideUserProfileRepository(
        @AuthenticatedClient authApi: AuthApi,
        sessionManager: SessionManager,
        appDatabase: AppDatabase,
        syncQueueDao: SyncQueueDao,
        connectivity: ConnectivityObserver,
        backendReachability: BackendReachability,
        moshi: Moshi
    ): UserProfileRepository = UserProfileRepository(
        authApi, sessionManager, appDatabase, syncQueueDao, connectivity, backendReachability, moshi
    )

    @Provides
    @Singleton
    fun provideApiaryRepository(
        apiaryApi: ApiaryApi,
        apiaryDao: ApiaryDao,
        syncQueueDao: SyncQueueDao,
        connectivity: ConnectivityObserver,
        backendReachability: BackendReachability,
        moshi: Moshi
    ): ApiaryRepository = ApiaryRepository(apiaryApi, apiaryDao, syncQueueDao, connectivity, backendReachability, moshi)

    @Provides
    @Singleton
    fun provideHiveRepository(
        hiveApi: HiveApi,
        hiveDao: HiveDao,
        syncQueueDao: SyncQueueDao,
        connectivity: ConnectivityObserver,
        backendReachability: BackendReachability,
        moshi: Moshi
    ): HiveRepository = HiveRepository(hiveApi, hiveDao, syncQueueDao, connectivity, backendReachability, moshi)

    @Provides
    @Singleton
    fun provideTaskRepository(
        taskApi: TaskApi,
        taskDao: TaskDao,
        syncQueueDao: SyncQueueDao,
        connectivity: ConnectivityObserver,
        backendReachability: BackendReachability,
        moshi: Moshi
    ): TaskRepository = TaskRepository(taskApi, taskDao, syncQueueDao, connectivity, backendReachability, moshi)

    @Provides
    @Singleton
    fun provideInspectionRepository(
        inspectionApi: InspectionApi,
        inspectionDao: InspectionDao,
        inspectionPhotoDao: InspectionPhotoDao,
        inspectionAiAnalysisDao: InspectionAiAnalysisDao,
        hiveDao: HiveDao,
        syncQueueDao: SyncQueueDao,
        connectivity: ConnectivityObserver,
        backendReachability: BackendReachability,
        moshi: Moshi
    ): InspectionRepository = InspectionRepository(
        inspectionApi,
        inspectionDao,
        inspectionPhotoDao,
        inspectionAiAnalysisDao,
        hiveDao,
        syncQueueDao,
        connectivity,
        backendReachability,
        moshi
    )

    @Provides
    @Singleton
    fun provideTreatmentRepository(
        treatmentApi: TreatmentApi,
        treatmentDao: TreatmentDao,
        syncQueueDao: SyncQueueDao,
        connectivity: ConnectivityObserver,
        backendReachability: BackendReachability,
        moshi: Moshi
    ): TreatmentRepository = TreatmentRepository(treatmentApi, treatmentDao, syncQueueDao, connectivity, backendReachability, moshi)

    @Provides
    @Singleton
    fun provideExtractionRepository(
        extractionApi: ExtractionApi,
        extractionDao: ExtractionDao,
        syncQueueDao: SyncQueueDao,
        connectivity: ConnectivityObserver,
        backendReachability: BackendReachability,
        moshi: Moshi
    ): ExtractionRepository = ExtractionRepository(extractionApi, extractionDao, syncQueueDao, connectivity, backendReachability, moshi)

    @Provides
    @Singleton
    fun providePhotoManager(
        @ApplicationContext context: Context
    ): PhotoManager = PhotoManager(context)
}

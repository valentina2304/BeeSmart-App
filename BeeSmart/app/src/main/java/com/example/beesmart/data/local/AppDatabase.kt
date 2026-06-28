package com.example.beesmart.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.beesmart.data.local.dao.*
import com.example.beesmart.data.local.entity.*

@TypeConverters(Converters::class)
@Database(
    entities = [
        ApiaryEntity::class,
        HiveEntity::class,
        TaskEntity::class,
        TreatmentEntity::class,
        ExtractionEntity::class,
        InspectionEntity::class,
        InspectionPhotoEntity::class,
        SyncQueueEntity::class,
        InspectionAiAnalysisEntity::class,
        NotificationHistoryEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun apiaryDao(): ApiaryDao
    abstract fun hiveDao(): HiveDao
    abstract fun taskDao(): TaskDao
    abstract fun treatmentDao(): TreatmentDao
    abstract fun extractionDao(): ExtractionDao
    abstract fun inspectionDao(): InspectionDao
    abstract fun inspectionPhotoDao(): InspectionPhotoDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun inspectionAiAnalysisDao(): InspectionAiAnalysisDao
    abstract fun notificationHistoryDao(): NotificationHistoryDao
}

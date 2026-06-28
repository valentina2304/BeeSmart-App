package com.example.beesmart.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.beesmart.data.local.AppDatabase
import com.example.beesmart.data.local.dao.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // v2 adds the `inspections` table. Existing apiaries/hives/tasks/etc.
    // rows are preserved so cached data survives the app upgrade.
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `inspections` (
                    `localId` TEXT NOT NULL,
                    `serverId` TEXT,
                    `hiveLocalId` TEXT NOT NULL,
                    `hiveServerId` TEXT,
                    `hiveName` TEXT NOT NULL,
                    `apiaryId` TEXT NOT NULL,
                    `apiaryName` TEXT NOT NULL,
                    `inspectionDate` TEXT NOT NULL,
                    `temperature` REAL,
                    `framesCount` INTEGER,
                    `broodFrames` INTEGER,
                    `honeyFrames` INTEGER,
                    `pollenFrames` INTEGER,
                    `queenSeen` INTEGER NOT NULL,
                    `eggsSeen` INTEGER NOT NULL,
                    `larvaeSeen` INTEGER NOT NULL,
                    `photosCount` INTEGER NOT NULL,
                    `createdAt` TEXT NOT NULL,
                    `syncStatus` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`localId`)
                )
                """.trimIndent()
            )
        }
    }

    // v3 adds the `inspection_ai_analysis` table — caches the AI cell-detection
    // counts plus a derived health verdict alongside each inspection.
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `inspection_ai_analysis` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `inspectionLocalId` TEXT NOT NULL,
                    `inspectionServerId` TEXT,
                    `rawCountsJson` TEXT NOT NULL,
                    `message` TEXT,
                    `computedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_inspection_ai_analysis_inspectionLocalId` " +
                    "ON `inspection_ai_analysis` (`inspectionLocalId`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_inspection_ai_analysis_inspectionServerId` " +
                    "ON `inspection_ai_analysis` (`inspectionServerId`)"
            )
        }
    }

    private val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `inspection_photos` (
                    `localId` TEXT NOT NULL,
                    `serverId` TEXT,
                    `inspectionLocalId` TEXT NOT NULL,
                    `inspectionServerId` TEXT,
                    `photoUrl` TEXT NOT NULL,
                    `description` TEXT,
                    `createdAt` TEXT NOT NULL,
                    `syncStatus` TEXT NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`localId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_inspection_photos_serverId` ON `inspection_photos` (`serverId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_inspection_photos_inspectionLocalId` ON `inspection_photos` (`inspectionLocalId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_inspection_photos_inspectionServerId` ON `inspection_photos` (`inspectionServerId`)")
        }
    }

    private val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `notification_history` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `message` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `relatedEntityId` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `isRead` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_notification_history_createdAt` " +
                    "ON `notification_history` (`createdAt`)"
            )
        }
    }

    private val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `hives` ADD COLUMN `reginaPrezenta` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `hives` ADD COLUMN `varstaRegina` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `hives` ADD COLUMN `rameAlbine` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `hives` ADD COLUMN `ramePuiet` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `hives` ADD COLUMN `rameMiere` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `hives` ADD COLUMN `ultimaInspectie` TEXT")
        }
    }

    private val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `inspections` ADD COLUMN `queenCellsSeen` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `inspections` ADD COLUMN `queenCellsWithEggs` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `inspections` ADD COLUMN `beardingAtEntrance` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `inspections` ADD COLUMN `spaceNeeded` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `inspections` ADD COLUMN `broodPattern` TEXT")
            db.execSQL("ALTER TABLE `inspections` ADD COLUMN `honeyCappingPercent` INTEGER")
            db.execSQL("ALTER TABLE `inspections` ADD COLUMN `feedingGiven` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `inspections` ADD COLUMN `waterAvailable` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `inspections` ADD COLUMN `moistureOrMold` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `inspections` ADD COLUMN `deadBeesAtEntrance` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `inspections` ADD COLUMN `unusualBehavior` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `inspections` ADD COLUMN `temperament` TEXT")
            db.execSQL("ALTER TABLE `inspections` ADD COLUMN `oldCombsToReplace` INTEGER")
        }
    }

    private val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "ALTER TABLE `inspection_ai_analysis` " +
                    "ADD COLUMN `cellDetectionsJson` TEXT NOT NULL DEFAULT '[]'"
            )
        }
    }

    // v9 adds the free-text `notes` column to inspections.
    private val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `inspections` ADD COLUMN `notes` TEXT")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "beesmart.db")
            .addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9
            )
            .build()

    @Provides fun provideApiaryDao(db: AppDatabase): ApiaryDao = db.apiaryDao()
    @Provides fun provideHiveDao(db: AppDatabase): HiveDao = db.hiveDao()
    @Provides fun provideTaskDao(db: AppDatabase): TaskDao = db.taskDao()
    @Provides fun provideTreatmentDao(db: AppDatabase): TreatmentDao = db.treatmentDao()
    @Provides fun provideExtractionDao(db: AppDatabase): ExtractionDao = db.extractionDao()
    @Provides fun provideInspectionDao(db: AppDatabase): InspectionDao = db.inspectionDao()
    @Provides fun provideInspectionPhotoDao(db: AppDatabase): InspectionPhotoDao =
        db.inspectionPhotoDao()
    @Provides fun provideSyncQueueDao(db: AppDatabase): SyncQueueDao = db.syncQueueDao()
    @Provides fun provideInspectionAiAnalysisDao(db: AppDatabase): InspectionAiAnalysisDao =
        db.inspectionAiAnalysisDao()
    @Provides fun provideNotificationHistoryDao(db: AppDatabase): NotificationHistoryDao =
        db.notificationHistoryDao()
}

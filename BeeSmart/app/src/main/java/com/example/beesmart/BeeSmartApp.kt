package com.example.beesmart

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.beesmart.notifications.TaskNotificationReceiver
import com.example.beesmart.notifications.TreatmentNotificationReceiver
import com.example.beesmart.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class BeeSmartApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler

    override fun onCreate() {
        super.onCreate()
        syncScheduler.start()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val taskChannel = NotificationChannel(
                TaskNotificationReceiver.CHANNEL_ID,
                TaskNotificationReceiver.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificari pentru termenele limita ale task-urilor"
            }

            val treatmentChannel = NotificationChannel(
                TreatmentNotificationReceiver.CHANNEL_ID,
                TreatmentNotificationReceiver.CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificari pentru verificarile si aplicarile de tratamente"
            }

            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(taskChannel)
            nm.createNotificationChannel(treatmentChannel)
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

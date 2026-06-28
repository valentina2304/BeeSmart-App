package com.example.beesmart.sync

import android.content.Context
import android.util.Log
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkManager
import com.example.beesmart.data.local.dao.SyncQueueDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "SyncScheduler"
private const val PROBE_INTERVAL_MS = 60_000L

@Singleton
class SyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectivity: ConnectivityObserver,
    private val syncQueueDao: SyncQueueDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Kick an immediate sync attempt (constrained to NetworkType.CONNECTED by WorkManager). */
    fun requestSync() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            SyncWorker.WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            SyncWorker.buildRequest()
        )
    }

    /**
     * Called once from Application.onCreate():
     * 1. Registers a periodic 15-min sync as a safety net.
     * 2. Subscribes to connectivity; every time we transition to online, kick a one-time sync.
     */
    fun start() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SyncWorker.PERIODIC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            SyncWorker.buildPeriodicRequest()
        )

        scope.launch {
            connectivity.isOnline
                .drop(1) // ignore the initial replay; the periodic worker covers startup
                .filter { it }
                .onEach { Log.d(TAG, "Connectivity restored — triggering sync") }
                .collect { requestSync() }
        }

        // Probe loop: while pending ops remain and the device reports online, retry every
        // PROBE_INTERVAL_MS. Covers the case where WiFi never flapped but the backend went
        // down and came back — no connectivity transition fires to wake the scheduler.
        scope.launch {
            while (true) {
                delay(PROBE_INTERVAL_MS)
                if (connectivity.isCurrentlyOnline() && syncQueueDao.getPendingCount() > 0) {
                    Log.d(TAG, "Queue non-empty while online — probing sync")
                    requestSync()
                }
            }
        }

        // Also flush anything that was queued before we started listening.
        if (connectivity.isCurrentlyOnline()) requestSync()
    }
}

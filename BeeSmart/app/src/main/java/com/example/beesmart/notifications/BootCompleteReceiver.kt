package com.example.beesmart.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.beesmart.data.local.dao.TaskDao
import com.example.beesmart.data.local.dao.TreatmentDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootCompleteReceiver : BroadcastReceiver() {

    @Inject lateinit var taskDao: TaskDao
    @Inject lateinit var taskNotificationScheduler: TaskNotificationScheduler
    @Inject lateinit var treatmentDao: TreatmentDao
    @Inject lateinit var treatmentNotificationScheduler: TreatmentNotificationScheduler

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                taskDao.getAll()
                    .filter { it.status != "Completed" }
                    .forEach { task ->
                        val dueDate = task.dueDate ?: return@forEach
                        val id = task.serverId ?: task.localId
                        taskNotificationScheduler.scheduleTaskNotifications(id, task.title, dueDate)
                    }

                treatmentDao.getAll()
                    .forEach { treatment ->
                        val nextTreatmentDate = treatment.nextTreatmentDate ?: return@forEach
                        val id = treatment.serverId ?: treatment.localId
                        treatmentNotificationScheduler.scheduleTreatmentReminder(
                            id,
                            treatment.productName,
                            nextTreatmentDate
                        )
                    }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

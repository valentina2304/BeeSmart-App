package com.example.beesmart.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.beesmart.MainActivity
import com.example.beesmart.R
import com.example.beesmart.data.repository.NotificationHistoryRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TaskNotificationReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationHistoryRepository: NotificationHistoryRepository

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TASK_TITLE) ?: "Task"
        val isDeadlineDay = intent.getBooleanExtra(EXTRA_IS_DEADLINE_DAY, false)

        val notifId = notifIdFor(taskId, isDeadlineDay)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context, notifId, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notifTitle = if (isDeadlineDay) "Azi are nevoie de atenția ta" else "Un reminder pentru mâine"
        val notifText = if (isDeadlineDay) {
            "Task-ul \"$title\" ajunge azi la termen. Îl poți bifa când este gata."
        } else {
            "Task-ul \"$title\" are termen mâine. Îți las aici un mic semn din timp."
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tasks)
            .setContentTitle(notifTitle)
            .setContentText(notifText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notifText))
            .setContentIntent(tapPending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (isDeadlineDay) {
            val completeIntent = Intent(context, TaskCompleteActionReceiver::class.java).apply {
                putExtra(TaskCompleteActionReceiver.EXTRA_TASK_ID, taskId)
                putExtra(TaskCompleteActionReceiver.EXTRA_NOTIF_ID, notifId)
            }
            val completePending = PendingIntent.getBroadcast(
                context, notifId + 100_000,
                completeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "Finalizat ✓", completePending)
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, builder.build())

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                notificationHistoryRepository.saveTaskReminder(
                    title = notifTitle,
                    message = notifText,
                    taskId = taskId
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "task_reminders"
        const val CHANNEL_NAME = "Remindere task-uri"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_TASK_TITLE = "extra_task_title"
        const val EXTRA_IS_DEADLINE_DAY = "extra_is_deadline_day"

        fun notifIdFor(taskId: String, isDeadlineDay: Boolean): Int {
            val base = (taskId.hashCode() and 0x7FFFFFFF)
            return if (isDeadlineDay) base * 2 + 1 else base * 2
        }
    }
}

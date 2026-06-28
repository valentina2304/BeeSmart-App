package com.example.beesmart.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskNotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleTaskNotifications(taskId: String, title: String, dueDateString: String) {
        val dueDate = parseDueDate(dueDateString) ?: return
        val today = LocalDate.now()

        val dayBefore = dueDate.minusDays(1)
        if (!dayBefore.isBefore(today)) {
            val triggerMillis = dayBefore.atTime(20, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            scheduleAlarm(taskId, title, triggerMillis, isDeadlineDay = false)
        }

        if (!dueDate.isBefore(today)) {
            val triggerMillis = dueDate.atTime(12, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            scheduleAlarm(taskId, title, triggerMillis, isDeadlineDay = true)
        }
    }

    fun cancelTaskNotifications(taskId: String) {
        listOf(false, true).forEach { isDeadlineDay ->
            val intent = Intent(context, TaskNotificationReceiver::class.java)
            val requestCode = requestCodeFor(taskId, isDeadlineDay)
            PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )?.let { alarmManager.cancel(it) }
        }
    }

    private fun scheduleAlarm(taskId: String, title: String, triggerAtMillis: Long, isDeadlineDay: Boolean) {
        val intent = Intent(context, TaskNotificationReceiver::class.java).apply {
            putExtra(TaskNotificationReceiver.EXTRA_TASK_ID, taskId)
            putExtra(TaskNotificationReceiver.EXTRA_TASK_TITLE, title)
            putExtra(TaskNotificationReceiver.EXTRA_IS_DEADLINE_DAY, isDeadlineDay)
        }
        val requestCode = requestCodeFor(taskId, isDeadlineDay)
        val pendingIntent = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                alarmManager.canScheduleExactAlarms()

        if (canScheduleExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        }
    }

    private fun requestCodeFor(taskId: String, isDeadlineDay: Boolean): Int {
        val base = (taskId.hashCode() and 0x7FFFFFFF)
        return if (isDeadlineDay) base * 2 + 1 else base * 2
    }

    private fun parseDueDate(dueDateString: String): LocalDate? = try {
        ZonedDateTime.parse(dueDateString).toLocalDate()
    } catch (_: Exception) {
        try { LocalDate.parse(dueDateString.substringBefore('T')) } catch (_: Exception) { null }
    }
}

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
class TreatmentNotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleTreatmentReminder(treatmentId: String, productName: String, nextTreatmentDateString: String) {
        val nextTreatmentDate = parseDate(nextTreatmentDateString) ?: return
        val today = LocalDate.now()

        val dayBefore = nextTreatmentDate.minusDays(1)
        if (!dayBefore.isBefore(today)) {
            val triggerMillis = dayBefore.atTime(20, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            scheduleAlarm(treatmentId, productName, triggerMillis, isDeadlineDay = false)
        }

        if (!nextTreatmentDate.isBefore(today)) {
            val triggerMillis = nextTreatmentDate.atTime(8, 0)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            scheduleAlarm(treatmentId, productName, triggerMillis, isDeadlineDay = true)
        }
    }

    fun cancelTreatmentReminder(treatmentId: String) {
        listOf(false, true).forEach { isDeadlineDay ->
            val intent = Intent(context, TreatmentNotificationReceiver::class.java)
            val requestCode = requestCodeFor(treatmentId, isDeadlineDay)
            PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )?.let { alarmManager.cancel(it) }
        }
    }

    private fun scheduleAlarm(treatmentId: String, productName: String, triggerAtMillis: Long, isDeadlineDay: Boolean) {
        val intent = Intent(context, TreatmentNotificationReceiver::class.java).apply {
            putExtra(TreatmentNotificationReceiver.EXTRA_TREATMENT_ID, treatmentId)
            putExtra(TreatmentNotificationReceiver.EXTRA_PRODUCT_NAME, productName)
            putExtra(TreatmentNotificationReceiver.EXTRA_IS_DEADLINE_DAY, isDeadlineDay)
        }
        val requestCode = requestCodeFor(treatmentId, isDeadlineDay)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
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

    private fun requestCodeFor(treatmentId: String, isDeadlineDay: Boolean): Int {
        val base = (treatmentId.hashCode() and 0x7FFFFFFF) xor 0x5A17
        return if (isDeadlineDay) base * 2 + 1 else base * 2
    }

    private fun parseDate(dateString: String): LocalDate? = try {
        ZonedDateTime.parse(dateString).toLocalDate()
    } catch (_: Exception) {
        try {
            LocalDate.parse(dateString.substringBefore('T'))
        } catch (_: Exception) {
            null
        }
    }
}

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
class TreatmentNotificationReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationHistoryRepository: NotificationHistoryRepository

    override fun onReceive(context: Context, intent: Intent) {
        val treatmentId = intent.getStringExtra(EXTRA_TREATMENT_ID) ?: return
        val productName = intent.getStringExtra(EXTRA_PRODUCT_NAME) ?: "tratament"
        val isDeadlineDay = intent.getBooleanExtra(EXTRA_IS_DEADLINE_DAY, false)
        val notifId = notifIdFor(treatmentId, isDeadlineDay)

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context,
            notifId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notifTitle = if (isDeadlineDay) "Azi ai un tratament programat" else "Un reminder pentru mâine"
        val notifText = if (isDeadlineDay) {
            "Tratamentul \"$productName\" ajunge azi la termen. Verifică în jurnal aplicarea sau verificarea."
        } else {
            "Tratamentul \"$productName\" are termen mâine. Îți las aici un mic semn din timp."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield_check)
            .setContentTitle(notifTitle)
            .setContentText(notifText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notifText))
            .setContentIntent(tapPending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notifId, notification)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                notificationHistoryRepository.saveTreatmentReminder(
                    title = notifTitle,
                    message = notifText,
                    treatmentId = treatmentId
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "treatment_reminders"
        const val CHANNEL_NAME = "Remindere tratamente"
        const val EXTRA_TREATMENT_ID = "extra_treatment_id"
        const val EXTRA_PRODUCT_NAME = "extra_product_name"
        const val EXTRA_IS_DEADLINE_DAY = "extra_is_deadline_day"

        fun notifIdFor(treatmentId: String, isDeadlineDay: Boolean): Int {
            val base = (((treatmentId.hashCode() and 0x7FFFFFFF) xor 0x5A17) % 1_000_000)
            return (if (isDeadlineDay) base * 2 + 1 else base * 2) + 50_000
        }
    }
}

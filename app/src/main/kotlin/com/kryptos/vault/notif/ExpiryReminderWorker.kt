package com.kryptos.vault.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fmz.kryptos.R
import com.kryptos.vault.MainActivity

class ExpiryReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE).orEmpty().ifBlank { "Vault entry" }
        val body = inputData.getString(KEY_BODY).orEmpty()
        val entryId = inputData.getLong(KEY_ENTRY_ID, 0L)

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel(nm)

        // Tap → open the app. We don't deep-link to the detail screen yet, but the user lands on
        // the unlocked vault and the entry is right there in the list.
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            applicationContext,
            entryId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // Distinct id per (entry, threshold) so multiple reminders for one entry don't collide.
        val notifId = (entryId * 10 + (inputData.getInt(KEY_THRESHOLD_INDEX, 0))).toInt()
        nm.notify(notifId, notif)
        return Result.success()
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Expiry reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Heads-up before passports, licenses, and cards expire."
        }
        nm.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "expiry_reminders"
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_ENTRY_ID = "entry_id"
        const val KEY_THRESHOLD_INDEX = "threshold_index"
    }
}

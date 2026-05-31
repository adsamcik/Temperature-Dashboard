package com.adsamcik.temperaturedashboard.shared.alerts

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.adsamcik.temperaturedashboard.core.model.Sensor
import io.github.aakira.napier.Napier

class AndroidNotifier(private val context: Context) : Notifier {
    private val manager = NotificationManagerCompat.from(context)

    override fun notify(sensor: Sensor, title: String, body: String) {
        ensureChannel()
        val launcher = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pending = launcher?.let {
            PendingIntent.getActivity(
                context,
                sensor.id.raw.toInt(),
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val notification: Notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        try {
            manager.notify(sensor.id.raw.toInt(), notification)
        } catch (se: SecurityException) {
            Napier.w("POST_NOTIFICATIONS denied; alert dropped: ${se.message}", tag = LOG_TAG)
        }
    }

    private fun ensureChannel() {
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Sensor alerts",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Threshold-crossing alerts for your temperature/humidity sensors."
            },
        )
    }

    private companion object {
        const val LOG_TAG = "AndroidNotifier"
        const val CHANNEL_ID = "tdash.alerts"
    }
}

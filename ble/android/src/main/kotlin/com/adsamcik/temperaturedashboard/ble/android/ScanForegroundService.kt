package com.adsamcik.temperaturedashboard.ble.android

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.github.aakira.napier.Napier

/**
 * Foreground service that keeps a BLE scan alive while the app is backgrounded.
 *
 * The actual scanning is owned by an injected [AndroidBleScanner] singleton —
 * this service just exists to satisfy Android's foreground-service contract so
 * the OS doesn't kill us when the user switches apps.
 *
 * Start with [start] from your application bootstrapper; stop with [stop].
 * Idempotent.
 */
class ScanForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Napier.i("ScanForegroundService starting", tag = LOG_TAG)
        ensureChannel()
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notif)
        }
        return START_STICKY
    }

    private fun ensureChannel() {
        val mgr = getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Sensor scan",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps the temperature scanner alive while the app is backgrounded."
                setShowBadge(false)
            },
        )
    }

    private fun buildNotification(): Notification {
        val launcherIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = launcherIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Temperature Dashboard")
            .setContentText("Scanning for sensors")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
    }

    companion object {
        private const val LOG_TAG = "ScanForegroundService"
        private const val CHANNEL_ID = "tdash.scan"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, ScanForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScanForegroundService::class.java))
        }

        /** Runtime permissions the app must hold before [start] will succeed. */
        val requiredPermissions: Array<String> = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_SCAN)
                add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
    }
}

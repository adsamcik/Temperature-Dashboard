package com.adsamcik.temperaturedashboard.shared.alerts

import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.TrayState
import com.adsamcik.temperaturedashboard.core.model.Sensor

/**
 * Desktop [Notifier] backed by Compose Multiplatform's [TrayState].
 * `sendNotification` pops a native balloon notification through the system
 * tray (Windows toast, macOS notification centre, Linux libnotify).
 */
class DesktopTrayNotifier(private val trayState: TrayState) : Notifier {
    override fun notify(sensor: Sensor, title: String, body: String) {
        trayState.sendNotification(
            Notification(
                title = title,
                message = body,
                type = Notification.Type.Warning,
            ),
        )
    }
}

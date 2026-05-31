package com.adsamcik.temperaturedashboard.desktop

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.TrayState
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.adsamcik.temperaturedashboard.shared.TdashApp
import com.adsamcik.temperaturedashboard.shared.di.desktopPlatformModule
import com.adsamcik.temperaturedashboard.shared.di.sharedModule
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

/**
 * Desktop entry point.
 *
 * Lifecycle:
 *  - Koin + Napier are started once before `application { }`. The scanning
 *    coordinator that Koin builds is process-scoped — it keeps scanning even
 *    while the window is hidden to tray.
 *  - The window's close button (✕) hides the window instead of quitting, so
 *    background data collection continues. The tray icon exposes a Show menu
 *    item to bring the window back, and a Quit item that actually exits.
 *  - Double-clicking the tray icon also shows the window.
 *  - The same TrayState is registered as a Koin singleton so the
 *    `DesktopTrayNotifier` can pop alert balloons through it.
 */
fun main() {
    if (GlobalContext.getOrNull() == null) {
        Napier.base(DebugAntilog())
        startKoin {
            modules(sharedModule, desktopPlatformModule)
        }
    }

    val trayState: TrayState = GlobalContext.get().get()

    application {
        var windowVisible by remember { mutableStateOf(true) }
        val trayIcon = rememberVectorPainter(Icons.Outlined.Thermostat)

        Tray(
            icon = trayIcon,
            state = trayState,
            tooltip = "Temperature Dashboard",
            onAction = { windowVisible = true },
            menu = {
                Item("Show dashboard", onClick = { windowVisible = true })
                Separator()
                Item("Quit", onClick = ::exitApplication)
            },
        )

        Window(
            onCloseRequest = { windowVisible = false }, // hide, don't exit
            visible = windowVisible,
            title = "Temperature Dashboard",
            state = rememberWindowState(size = DpSize(1100.dp, 720.dp)),
        ) {
            MaterialTheme { TdashApp(useCompactLayout = false) }
        }
    }
}

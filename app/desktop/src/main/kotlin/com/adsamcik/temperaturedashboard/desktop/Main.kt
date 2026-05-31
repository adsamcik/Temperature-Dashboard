package com.adsamcik.temperaturedashboard.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.adsamcik.temperaturedashboard.shared.TdashApp
import com.adsamcik.temperaturedashboard.shared.di.desktopPlatformModule
import com.adsamcik.temperaturedashboard.shared.di.sharedModule
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.core.context.startKoin

fun main() {
    Napier.base(DebugAntilog())
    startKoin {
        modules(sharedModule, desktopPlatformModule)
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Temperature Dashboard",
            state = rememberWindowState(size = DpSize(1100.dp, 720.dp)),
        ) {
            // Desktop window is always wide enough to use the rail layout.
            TdashApp(useCompactLayout = false)
        }
    }
}

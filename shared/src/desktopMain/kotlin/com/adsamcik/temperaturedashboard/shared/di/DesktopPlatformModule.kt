package com.adsamcik.temperaturedashboard.shared.di

import androidx.compose.ui.window.TrayState
import com.adsamcik.temperaturedashboard.ble.api.BleScanner
import com.adsamcik.temperaturedashboard.ble.api.BluetoothAdapterMonitor
import com.adsamcik.temperaturedashboard.ble.desktop.DesktopBleScanner
import com.adsamcik.temperaturedashboard.ble.desktop.DesktopBluetoothAdapterMonitor
import com.adsamcik.temperaturedashboard.core.database.DatabaseFactory
import com.adsamcik.temperaturedashboard.shared.alerts.DesktopTrayNotifier
import com.adsamcik.temperaturedashboard.shared.alerts.Notifier
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.PreferencesSettings
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.prefs.Preferences

val desktopPlatformModule: Module = module {
    single<DatabaseFactory> { DatabaseFactory() }

    single<ObservableSettings> {
        val prefs = Preferences.userRoot().node("com/adsamcik/temperaturedashboard")
        PreferencesSettings(prefs)
    }

    single<BleScanner> { DesktopBleScanner() }
    single<BluetoothAdapterMonitor> {
        val nativeAvailable = runCatching { System.loadLibrary("btleplug_jni") }.isSuccess
        DesktopBluetoothAdapterMonitor(nativeAvailable = nativeAvailable)
    }

    // TrayState is created in Main.kt before application{} and injected here so
    // the Notifier can pop notifications through the system tray.
    single<TrayState> { TrayState() }
    single<Notifier> { DesktopTrayNotifier(get()) }
}

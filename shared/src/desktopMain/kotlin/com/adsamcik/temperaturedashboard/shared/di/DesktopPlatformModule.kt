package com.adsamcik.temperaturedashboard.shared.di

import com.adsamcik.temperaturedashboard.ble.api.BleScanner
import com.adsamcik.temperaturedashboard.ble.api.BluetoothAdapterMonitor
import com.adsamcik.temperaturedashboard.ble.desktop.DesktopBleScanner
import com.adsamcik.temperaturedashboard.ble.desktop.DesktopBluetoothAdapterMonitor
import com.adsamcik.temperaturedashboard.core.database.DatabaseFactory
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
}

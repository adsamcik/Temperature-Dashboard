package com.adsamcik.temperaturedashboard.shared.di

import android.content.Context
import com.adsamcik.temperaturedashboard.ble.android.AndroidBleScanner
import com.adsamcik.temperaturedashboard.ble.android.AndroidBluetoothAdapterMonitor
import com.adsamcik.temperaturedashboard.ble.api.BleScanner
import com.adsamcik.temperaturedashboard.ble.api.BluetoothAdapterMonitor
import com.adsamcik.temperaturedashboard.core.database.DatabaseFactory
import com.adsamcik.temperaturedashboard.shared.alerts.AndroidNotifier
import com.adsamcik.temperaturedashboard.shared.alerts.Notifier
import com.russhwolf.settings.ObservableSettings
import com.russhwolf.settings.SharedPreferencesSettings
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

val androidPlatformModule: Module = module {
    single<DatabaseFactory> { DatabaseFactory(androidContext()) }

    single<ObservableSettings> {
        val prefs = androidContext().getSharedPreferences(
            "tdash.settings",
            Context.MODE_PRIVATE,
        )
        SharedPreferencesSettings(prefs)
    }

    single<BleScanner> { AndroidBleScanner(androidContext()) }
    single<BluetoothAdapterMonitor> { AndroidBluetoothAdapterMonitor(androidContext()) }
    single<Notifier> { AndroidNotifier(androidContext()) }
    single<com.adsamcik.temperaturedashboard.shared.export.HistorySharer> {
        com.adsamcik.temperaturedashboard.shared.export.AndroidHistorySharer(androidContext())
    }
    single<com.adsamcik.temperaturedashboard.shared.system.AutostartManager> {
        com.adsamcik.temperaturedashboard.shared.system.AndroidAutostartManager()
    }
    single<com.adsamcik.temperaturedashboard.ble.api.BleConnector> {
        com.adsamcik.temperaturedashboard.ble.android.AndroidBleConnector(androidContext())
    }
}

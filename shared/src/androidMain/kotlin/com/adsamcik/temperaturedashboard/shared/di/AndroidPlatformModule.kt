package com.adsamcik.temperaturedashboard.shared.di

import android.content.Context
import com.adsamcik.temperaturedashboard.ble.android.AndroidBleScanner
import com.adsamcik.temperaturedashboard.ble.android.AndroidBluetoothAdapterMonitor
import com.adsamcik.temperaturedashboard.ble.api.BleScanner
import com.adsamcik.temperaturedashboard.ble.api.BluetoothAdapterMonitor
import com.adsamcik.temperaturedashboard.core.database.DatabaseFactory
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
}

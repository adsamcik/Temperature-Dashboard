package com.adsamcik.temperaturedashboard.shared.di

import com.adsamcik.temperaturedashboard.core.database.DatabaseFactory
import com.adsamcik.temperaturedashboard.core.database.IntervalCoalescer
import com.adsamcik.temperaturedashboard.core.database.TemperatureDatabase
import com.adsamcik.temperaturedashboard.core.datastore.SettingsRepository
import com.adsamcik.temperaturedashboard.decoder.api.DecoderRegistry
import com.adsamcik.temperaturedashboard.decoder.api.DeviceProfileRegistry
import com.adsamcik.temperaturedashboard.decoder.builtins.BuiltinDecoders
import com.adsamcik.temperaturedashboard.shared.alerts.AlertEvaluator
import com.adsamcik.temperaturedashboard.shared.alerts.SensorAlertRepository
import com.adsamcik.temperaturedashboard.shared.repository.ReadingRepository
import com.adsamcik.temperaturedashboard.shared.repository.SensorRepository
import com.adsamcik.temperaturedashboard.shared.scanning.AdvertisementInterpreter
import com.adsamcik.temperaturedashboard.shared.scanning.ScanningCoordinator
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Platform-agnostic Koin module. Platform modules ([androidPlatformModule],
 * [desktopPlatformModule]) add the BLE scanner + adapter monitor + database
 * factory + Notifier (each with different constructors).
 */
val sharedModule: Module = module {
    // Decoder pipeline
    single<DecoderRegistry> { BuiltinDecoders.newRegistry() }
    single<DeviceProfileRegistry> { BuiltinDecoders.newProfileRegistry() }

    // Database
    single<TemperatureDatabase> { get<DatabaseFactory>().create() }
    single { get<TemperatureDatabase>().sensorDao() }
    single { get<TemperatureDatabase>().readingIntervalDao() }
    single { get<TemperatureDatabase>().sensorAlertDao() }

    // Settings
    single<SettingsRepository> { SettingsRepository(get()) }

    // Domain repositories
    single { SensorRepository(get()) }
    single { IntervalCoalescer(get()) }
    single { ReadingRepository(get(), get()) }
    single { SensorAlertRepository(get()) }

    // Alerts: Notifier is provided per platform; AlertEvaluator is common.
    single { AlertEvaluator(get(), get()) }

    // Interpreter + coordinator
    single { AdvertisementInterpreter(get()) }
    single { ScanningCoordinator(get(), get(), get(), get(), get()) }
}

package com.adsamcik.temperaturedashboard

import android.app.Application
import com.adsamcik.temperaturedashboard.shared.di.androidPlatformModule
import com.adsamcik.temperaturedashboard.shared.di.sharedModule
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class TemperatureDashboardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Napier.base(DebugAntilog())
        startKoin {
            androidLogger()
            androidContext(this@TemperatureDashboardApp)
            modules(sharedModule, androidPlatformModule)
        }
    }
}

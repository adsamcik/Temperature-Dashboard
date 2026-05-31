package com.adsamcik.temperaturedashboard

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import com.adsamcik.temperaturedashboard.ble.android.ScanForegroundService
import com.adsamcik.temperaturedashboard.shared.TdashApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        ScanForegroundService.start(this)
        setContent {
            val config = LocalConfiguration.current
            val compact = remember(config) { config.screenWidthDp < COMPACT_BREAKPOINT_DP }
            TdashApp(useCompactLayout = compact)
        }
    }

    private companion object {
        const val COMPACT_BREAKPOINT_DP = 600
    }
}

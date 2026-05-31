package com.adsamcik.temperaturedashboard.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Temperature Dashboard",
        state = rememberWindowState(size = DpSize(1100.dp, 720.dp)),
    ) {
        MaterialTheme {
            HelloScreen()
        }
    }
}

@Composable
private fun HelloScreen() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = "Temperature Dashboard — Desktop, Phase 0 \uD83C\uDF21\uFE0F",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

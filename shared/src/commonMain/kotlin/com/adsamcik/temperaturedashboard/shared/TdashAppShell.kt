package com.adsamcik.temperaturedashboard.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.adsamcik.temperaturedashboard.core.designsystem.TdashTheme
import com.adsamcik.temperaturedashboard.core.model.SensorId
import com.adsamcik.temperaturedashboard.shared.navigation.NavStack
import com.adsamcik.temperaturedashboard.shared.navigation.NavTarget
import com.adsamcik.temperaturedashboard.shared.navigation.ShellDestination

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TdashAppShell(
    shellScreens: Map<ShellDestination, @Composable (onOpenSensorDetail: (SensorId) -> Unit) -> Unit>,
    sensorDetailScreen: @Composable (SensorId) -> Unit,
    useCompactLayout: Boolean = false,
    snackbarState: androidx.compose.material3.SnackbarHostState =
        remember { androidx.compose.material3.SnackbarHostState() },
) {
    val navStack = remember { NavStack() }
    var current by remember { mutableStateOf<NavTarget>(navStack.current) }

    fun navigate(target: NavTarget) {
        navStack.push(target); current = navStack.current
    }
    fun reset(destination: ShellDestination) {
        navStack.resetTo(NavTarget.Shell(destination)); current = navStack.current
    }
    fun back() {
        if (navStack.pop()) current = navStack.current
    }

    val openDetail: (SensorId) -> Unit = { id -> navigate(NavTarget.SensorDetail(id)) }

    when (val target = current) {
        is NavTarget.Shell -> ShellScaffold(
            current = target.destination,
            onSelect = ::reset,
            useCompactLayout = useCompactLayout,
            snackbarState = snackbarState,
            content = {
                val screen = shellScreens[target.destination]
                if (screen != null) screen(openDetail) else Text(
                    text = "${target.destination.label} screen not provided",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
        )
        is NavTarget.SensorDetail -> DetailScaffold(
            onBack = ::back,
            snackbarState = snackbarState,
            content = { sensorDetailScreen(target.sensorId) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShellScaffold(
    current: ShellDestination,
    onSelect: (ShellDestination) -> Unit,
    useCompactLayout: Boolean,
    snackbarState: androidx.compose.material3.SnackbarHostState,
    content: @Composable () -> Unit,
) {
    if (useCompactLayout) {
        Scaffold(
            topBar = { TopAppBar(title = { Text(current.label) }) },
            snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarState) },
            bottomBar = {
                NavigationBar {
                    ShellDestination.entries.forEach { dest ->
                        NavigationBarItem(
                            selected = dest == current,
                            onClick = { onSelect(dest) },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) { content() }
        }
    } else {
        Surface {
            androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail {
                    ShellDestination.entries.forEach { dest ->
                        NavigationRailItem(
                            selected = dest == current,
                            onClick = { onSelect(dest) },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) },
                        )
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(
                        topBar = { TopAppBar(title = { Text(current.label) }) },
                        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarState) },
                    ) { padding ->
                        Box(modifier = Modifier.fillMaxSize().padding(padding)) { content() }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailScaffold(
    onBack: () -> Unit,
    snackbarState: androidx.compose.material3.SnackbarHostState,
    content: @Composable () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sensor") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) { content() }
    }
}


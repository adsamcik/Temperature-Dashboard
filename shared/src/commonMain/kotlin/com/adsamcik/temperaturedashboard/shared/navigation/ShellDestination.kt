package com.adsamcik.temperaturedashboard.shared.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import com.adsamcik.temperaturedashboard.core.model.SensorId

/**
 * Top-level destinations in the bottom-nav / nav-rail shell.
 *
 * Detail screens (sensor detail, edit, etc.) push on top of whichever shell
 * destination the user is currently in — they aren't first-class shell items.
 */
enum class ShellDestination(
    val label: String,
    val icon: ImageVector,
) {
    Dashboard("Dashboard", Icons.Outlined.Dashboard),
    Scan("Add sensor", Icons.Outlined.Sensors),
    Settings("Settings", Icons.Outlined.Settings),
}

/**
 * Sealed hierarchy of in-app navigation requests. Kept platform-agnostic so
 * the shell can drive either Compose Navigation (Android) or simple state
 * pushes (Desktop) — both paths land here.
 */
sealed interface NavRequest {
    data class GoToShell(val destination: ShellDestination) : NavRequest
    data class OpenSensorDetail(val sensorId: SensorId) : NavRequest
    data object Back : NavRequest
}

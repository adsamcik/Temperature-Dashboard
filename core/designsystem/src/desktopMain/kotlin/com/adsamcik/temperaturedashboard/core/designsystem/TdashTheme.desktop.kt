package com.adsamcik.temperaturedashboard.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable

/** Desktop has no Material You — always falls back to the static scheme. */
@Composable
actual fun dynamicColorSchemeOrNull(darkTheme: Boolean): ColorScheme? = null

/**
 * Desktop has no `MaterialExpressiveTheme` yet (Compose Multiplatform 1.8.x
 * tracks androidx.compose.material3:1.3.x, while Expressive lives in 1.4
 * alphas). We re-apply [MaterialTheme] to keep the API symmetric — the
 * `content` lambda runs inside a fully-themed Material 3 surface.
 */
@Composable
actual fun ExpressiveSurface(
    colorScheme: ColorScheme,
    typography: Typography,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = colorScheme, typography = typography, content = content)
}

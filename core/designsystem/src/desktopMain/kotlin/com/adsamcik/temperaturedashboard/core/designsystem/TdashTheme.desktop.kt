package com.adsamcik.temperaturedashboard.core.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable

/** Desktop has no Material You — always falls back to the static scheme. */
@Composable
actual fun dynamicColorSchemeOrNull(darkTheme: Boolean): ColorScheme? = null

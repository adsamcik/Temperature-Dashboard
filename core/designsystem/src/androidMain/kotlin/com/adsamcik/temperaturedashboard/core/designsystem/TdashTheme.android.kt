package com.adsamcik.temperaturedashboard.core.designsystem

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun dynamicColorSchemeOrNull(darkTheme: Boolean): ColorScheme? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
    val ctx = LocalContext.current
    return if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
actual fun ExpressiveSurface(
    colorScheme: ColorScheme,
    typography: Typography,
    content: @Composable () -> Unit,
) {
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = typography,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

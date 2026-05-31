package com.adsamcik.temperaturedashboard.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.adsamcik.temperaturedashboard.core.designsystem.token.TdashColorTokens

private val LightColors: ColorScheme = lightColorScheme(
    primary = TdashColorTokens.LightPrimary,
    onPrimary = TdashColorTokens.LightOnPrimary,
    primaryContainer = TdashColorTokens.LightPrimaryContainer,
    onPrimaryContainer = TdashColorTokens.LightOnPrimaryContainer,
    secondary = TdashColorTokens.LightSecondary,
    onSecondary = TdashColorTokens.LightOnSecondary,
    secondaryContainer = TdashColorTokens.LightSecondaryContainer,
    onSecondaryContainer = TdashColorTokens.LightOnSecondaryContainer,
    tertiary = TdashColorTokens.LightTertiary,
    onTertiary = TdashColorTokens.LightOnTertiary,
    tertiaryContainer = TdashColorTokens.LightTertiaryContainer,
    onTertiaryContainer = TdashColorTokens.LightOnTertiaryContainer,
    error = TdashColorTokens.LightError,
    onError = TdashColorTokens.LightOnError,
    errorContainer = TdashColorTokens.LightErrorContainer,
    onErrorContainer = TdashColorTokens.LightOnErrorContainer,
    background = TdashColorTokens.LightBackground,
    onBackground = TdashColorTokens.LightOnBackground,
    surface = TdashColorTokens.LightSurface,
    onSurface = TdashColorTokens.LightOnSurface,
    surfaceVariant = TdashColorTokens.LightSurfaceVariant,
    onSurfaceVariant = TdashColorTokens.LightOnSurfaceVariant,
    surfaceContainer = TdashColorTokens.LightSurfaceContainer,
    surfaceContainerHigh = TdashColorTokens.LightSurfaceContainerHigh,
    outline = TdashColorTokens.LightOutline,
    outlineVariant = TdashColorTokens.LightOutlineVariant,
)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = TdashColorTokens.DarkPrimary,
    onPrimary = TdashColorTokens.DarkOnPrimary,
    primaryContainer = TdashColorTokens.DarkPrimaryContainer,
    onPrimaryContainer = TdashColorTokens.DarkOnPrimaryContainer,
    secondary = TdashColorTokens.DarkSecondary,
    onSecondary = TdashColorTokens.DarkOnSecondary,
    secondaryContainer = TdashColorTokens.DarkSecondaryContainer,
    onSecondaryContainer = TdashColorTokens.DarkOnSecondaryContainer,
    tertiary = TdashColorTokens.DarkTertiary,
    onTertiary = TdashColorTokens.DarkOnTertiary,
    tertiaryContainer = TdashColorTokens.DarkTertiaryContainer,
    onTertiaryContainer = TdashColorTokens.DarkOnTertiaryContainer,
    error = TdashColorTokens.DarkError,
    onError = TdashColorTokens.DarkOnError,
    errorContainer = TdashColorTokens.DarkErrorContainer,
    onErrorContainer = TdashColorTokens.DarkOnErrorContainer,
    background = TdashColorTokens.DarkBackground,
    onBackground = TdashColorTokens.DarkOnBackground,
    surface = TdashColorTokens.DarkSurface,
    onSurface = TdashColorTokens.DarkOnSurface,
    surfaceVariant = TdashColorTokens.DarkSurfaceVariant,
    onSurfaceVariant = TdashColorTokens.DarkOnSurfaceVariant,
    surfaceContainer = TdashColorTokens.DarkSurfaceContainer,
    surfaceContainerHigh = TdashColorTokens.DarkSurfaceContainerHigh,
    outline = TdashColorTokens.DarkOutline,
    outlineVariant = TdashColorTokens.DarkOutlineVariant,
)

/**
 * Material 3 theme for the Temperature Dashboard.
 *
 * `dynamicColor = true` (Android 12+) sources the colour scheme from the
 * user's wallpaper; everywhere else we fall back to the warm-coral static
 * scheme above. The platform-specific [dynamicColorSchemeOrNull] expect/actual
 * does the platform check.
 */
@Composable
fun TdashTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val dynamic = if (dynamicColor) dynamicColorSchemeOrNull(darkTheme) else null
    val scheme = dynamic ?: if (darkTheme) DarkColors else LightColors

    MaterialTheme(
        colorScheme = scheme,
        typography = TdashTypography,
        content = content,
    )
}

/** Resolves Material You / dynamic colour where available, or null. */
@Composable
expect fun dynamicColorSchemeOrNull(darkTheme: Boolean): ColorScheme?

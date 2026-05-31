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
 * Both [darkTheme] and [dynamicColor] are passed in by the app shell from
 * the user's preferences (settings repository) so they can be overridden at
 * runtime. On Android, dynamic colour pulls from Material You wallpaper;
 * on Desktop we always fall back to the static warm-coral scheme regardless.
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
    ) {
        ExpressiveSurface(colorScheme = scheme, typography = TdashTypography, content = content)
    }
}

/** Resolves Material You / dynamic colour where available, or null. */
@Composable
expect fun dynamicColorSchemeOrNull(darkTheme: Boolean): ColorScheme?

/**
 * Wraps [content] in `MaterialExpressiveTheme` on Android (M3 Expressive
 * spring physics, motion scheme, ripple) and in stock [MaterialTheme] on
 * Desktop. Both branches preserve the [colorScheme] and [typography] passed
 * in, so theming is unified across the inner Material* call.
 */
@Composable
expect fun ExpressiveSurface(
    colorScheme: ColorScheme,
    typography: androidx.compose.material3.Typography,
    content: @Composable () -> Unit,
)

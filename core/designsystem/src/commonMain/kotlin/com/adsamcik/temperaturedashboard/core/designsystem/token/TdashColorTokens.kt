package com.adsamcik.temperaturedashboard.core.designsystem.token

import androidx.compose.ui.graphics.Color

/**
 * Material 3 colour scheme for the Temperature Dashboard.
 *
 * Tonal palette derives from a single seed (warm coral that nods at the
 * "temperature" theme without being too literal). The scheme is built once
 * here and consumed by [com.adsamcik.temperaturedashboard.core.designsystem.TdashTheme].
 *
 * On Android 12+ we override with dynamic colour from the user's wallpaper —
 * see TdashTheme.android.kt. On Desktop and pre-S Android the static scheme
 * below wins.
 */
internal object TdashColorTokens {
    // Static seed-derived light scheme
    val LightPrimary = Color(0xFFB52A1F)
    val LightOnPrimary = Color(0xFFFFFFFF)
    val LightPrimaryContainer = Color(0xFFFFDAD4)
    val LightOnPrimaryContainer = Color(0xFF410000)

    val LightSecondary = Color(0xFF775651)
    val LightOnSecondary = Color(0xFFFFFFFF)
    val LightSecondaryContainer = Color(0xFFFFDAD4)
    val LightOnSecondaryContainer = Color(0xFF2C1512)

    val LightTertiary = Color(0xFF715B2E)
    val LightOnTertiary = Color(0xFFFFFFFF)
    val LightTertiaryContainer = Color(0xFFFEDFA6)
    val LightOnTertiaryContainer = Color(0xFF251A00)

    val LightError = Color(0xFFBA1A1A)
    val LightOnError = Color(0xFFFFFFFF)
    val LightErrorContainer = Color(0xFFFFDAD6)
    val LightOnErrorContainer = Color(0xFF410002)

    val LightBackground = Color(0xFFFFFBFF)
    val LightOnBackground = Color(0xFF201A19)
    val LightSurface = Color(0xFFFFFBFF)
    val LightOnSurface = Color(0xFF201A19)
    val LightSurfaceVariant = Color(0xFFF5DDD9)
    val LightOnSurfaceVariant = Color(0xFF534340)
    val LightSurfaceContainer = Color(0xFFFAEDEA)
    val LightSurfaceContainerHigh = Color(0xFFF4E7E4)
    val LightOutline = Color(0xFF85736F)
    val LightOutlineVariant = Color(0xFFD8C2BD)

    // Static seed-derived dark scheme
    val DarkPrimary = Color(0xFFFFB4A8)
    val DarkOnPrimary = Color(0xFF690000)
    val DarkPrimaryContainer = Color(0xFF93000A)
    val DarkOnPrimaryContainer = Color(0xFFFFDAD4)

    val DarkSecondary = Color(0xFFE7BDB6)
    val DarkOnSecondary = Color(0xFF442925)
    val DarkSecondaryContainer = Color(0xFF5D3F3B)
    val DarkOnSecondaryContainer = Color(0xFFFFDAD4)

    val DarkTertiary = Color(0xFFE1C38C)
    val DarkOnTertiary = Color(0xFF3E2E04)
    val DarkTertiaryContainer = Color(0xFF584419)
    val DarkOnTertiaryContainer = Color(0xFFFEDFA6)

    val DarkError = Color(0xFFFFB4AB)
    val DarkOnError = Color(0xFF690005)
    val DarkErrorContainer = Color(0xFF93000A)
    val DarkOnErrorContainer = Color(0xFFFFDAD6)

    val DarkBackground = Color(0xFF1A110F)
    val DarkOnBackground = Color(0xFFF1DEDA)
    val DarkSurface = Color(0xFF1A110F)
    val DarkOnSurface = Color(0xFFF1DEDA)
    val DarkSurfaceVariant = Color(0xFF534340)
    val DarkOnSurfaceVariant = Color(0xFFD8C2BD)
    val DarkSurfaceContainer = Color(0xFF261E1B)
    val DarkSurfaceContainerHigh = Color(0xFF302724)
    val DarkOutline = Color(0xFFA08C88)
    val DarkOutlineVariant = Color(0xFF534340)
}

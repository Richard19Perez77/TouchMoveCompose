package com.rick.touchmovecompose.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Crypton Future Media's Hatsune Miku signature teal (#39C5BB). */
val MikuTeal = Color(0xFF39C5BB)
val MikuTealLight = Color(0xFF7AEEE6)
val MikuTealDark = Color(0xFF137A74)
val MikuMint = Color(0xFFD4FFFB)
val MikuInk = Color(0xFF0B1C1B)
val MikuNight = Color(0xFF102826)
val MikuGrey = Color(0xFFB8C4C3)
val MikuWhite = Color(0xFFF4FFFD)

val Player1Border = Color(0xFF137A7F)
val Player1Selected = Color(0xFF39C5BB)
val Player2Border = Color(0xFFE12885)
val Player2Selected = Color(0xFFFF5AAD)
val UnownedBorder = Color(0xFF2A2A2A)
val EmptySlotBorder = Color(0x66FFFFFF)
val HudScrim = Color(0xCC0B1C1B)
val ScoreText = MikuWhite

val TurnHighlight = Color(0xFFFF5AAD)

/** One Miku staging: canvas, cubes, HUD. Rotated on each tap. */
data class MikuLook(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val secondary: Color,
    val accent: Color,
    val onBackground: Color,
    val onPrimary: Color,
    val dark: Boolean,
) {
    fun colorScheme(): ColorScheme {
        return if (dark) {
            darkColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = MikuTealDark,
                onPrimaryContainer = MikuMint,
                secondary = secondary,
                onSecondary = onPrimary,
                secondaryContainer = Color(0xFF1E3A38),
                onSecondaryContainer = MikuMint,
                tertiary = accent,
                onTertiary = onPrimary,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = ScoreText,
                surfaceVariant = Color(0xFF1A3331),
                onSurfaceVariant = MikuGrey,
                outline = MikuTealDark,
            )
        } else {
            lightColorScheme(
                primary = primary,
                onPrimary = onPrimary,
                primaryContainer = MikuTealLight,
                onPrimaryContainer = MikuInk,
                secondary = secondary,
                onSecondary = MikuWhite,
                secondaryContainer = Color(0xFFD5E6E4),
                onSecondaryContainer = MikuInk,
                tertiary = accent,
                onTertiary = onPrimary,
                background = background,
                onBackground = onBackground,
                surface = surface,
                onSurface = MikuInk,
                surfaceVariant = Color(0xFFE3F4F2),
                onSurfaceVariant = Color(0xFF3D4F4D),
                outline = MikuTealDark,
            )
        }
    }
}

/**
 * Tap-cycled looks. Index 0 is the imported dark scheme, index 1 the imported
 * light scheme; the rest remix Player1/Player2 and stage colors from the same file.
 */
val MikuLooks = listOf(
    MikuLook(
        background = MikuInk,
        surface = MikuNight,
        primary = MikuTeal,
        secondary = MikuGrey,
        accent = MikuTealLight,
        onBackground = MikuMint,
        onPrimary = MikuInk,
        dark = true,
    ),
    MikuLook(
        background = MikuMint,
        surface = MikuWhite,
        primary = MikuTealDark,
        secondary = Color(0xFF4A5C5B),
        accent = MikuTeal,
        onBackground = MikuInk,
        onPrimary = MikuWhite,
        dark = false,
    ),
    MikuLook(
        background = MikuNight,
        surface = MikuInk,
        primary = Player1Selected,
        secondary = MikuNight,
        accent = TurnHighlight,
        onBackground = MikuMint,
        onPrimary = MikuInk,
        dark = true,
    ),
    MikuLook(
        background = MikuInk,
        surface = MikuNight,
        primary = Player2Selected,
        secondary = Player2Border,
        accent = MikuTealLight,
        onBackground = MikuMint,
        onPrimary = MikuInk,
        dark = true,
    ),
    MikuLook(
        background = UnownedBorder,
        surface = MikuInk,
        primary = Player1Border,
        secondary = Color(0xFF1A3331),
        accent = TurnHighlight,
        onBackground = MikuMint,
        onPrimary = MikuWhite,
        dark = true,
    ),
    MikuLook(
        background = MikuTealDark,
        surface = MikuInk,
        primary = MikuTealLight,
        secondary = Color(0xFF1E3A38),
        accent = Player2Selected,
        onBackground = MikuMint,
        onPrimary = MikuInk,
        dark = true,
    ),
    MikuLook(
        background = MikuNight,
        surface = MikuInk,
        primary = Player2Border,
        secondary = MikuNight,
        accent = ScoreText,
        onBackground = MikuWhite,
        onPrimary = MikuInk,
        dark = true,
    ),
    MikuLook(
        background = MikuWhite,
        surface = MikuMint,
        primary = Player1Selected,
        secondary = Color(0xFFD5E6E4),
        accent = Player2Selected,
        onBackground = MikuInk,
        onPrimary = MikuInk,
        dark = false,
    ),
)

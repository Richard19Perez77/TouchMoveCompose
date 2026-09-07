package com.rick.touchmovecompose.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val MikuDarkColorScheme = darkColorScheme(
    primary = MikuTeal,
    onPrimary = MikuInk,
    primaryContainer = MikuTealDark,
    onPrimaryContainer = MikuMint,
    secondary = MikuNight,
    onSecondary = MikuMint,
    secondaryContainer = Color(0xFF1E3A38),
    onSecondaryContainer = MikuMint,
    tertiary = MikuAccent,
    onTertiary = MikuInk,
    background = MikuNight,
    onBackground = MikuMint,
    surface = MikuNight,
    onSurface = MikuWhite,
    surfaceVariant = Color(0xFF1A3331),
    onSurfaceVariant = MikuGrey,
    outline = MikuTealDark,
)

private val MikuLightColorScheme = lightColorScheme(
    primary = MikuTeal,
    onPrimary = MikuInk,
    primaryContainer = MikuTealLight,
    onPrimaryContainer = MikuInk,
    secondary = MikuMint,
    onSecondary = MikuInk,
    secondaryContainer = Color(0xFFD5E6E4),
    onSecondaryContainer = MikuInk,
    tertiary = MikuAccent,
    onTertiary = MikuInk,
    background = MikuMint,
    onBackground = MikuInk,
    surface = MikuWhite,
    onSurface = MikuInk,
    surfaceVariant = Color(0xFFE3F4F2),
    onSurfaceVariant = Color(0xFF3D4F4D),
    outline = MikuTealDark,
)

/** Material 3 Miku theme. Canvas uses primary / secondary / tertiary (accent). */
@Composable
fun TouchMoveComposeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> MikuDarkColorScheme
        else -> MikuLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
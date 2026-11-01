package com.gitdroidstudio.shahid.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

val PrimaryBlue = Color(0xFF2F80ED)
val PrimaryBlueVariant = Color(0xFF1B5E20)
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkSurfaceVariant = Color(0xFF282828)
val DarkBorder = Color(0xFF383838)
val AccentGreen = Color(0xFF27AE60)
val AccentOrange = Color(0xFFE67E22)
val AccentRed = Color(0xFFEB5757)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    secondary = AccentGreen,
    tertiary = AccentOrange,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFEEEEEE),
    error = AccentRed
)

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    secondary = AccentGreen,
    tertiary = AccentOrange,
    background = Color(0xFFF8F9FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFF1F3F5),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFF212529),
    onSurface = Color(0xFF212529),
    error = AccentRed
)

@Composable
fun GitDroidStudioTheme(
    darkTheme: Boolean = true, // Default dark as requested
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

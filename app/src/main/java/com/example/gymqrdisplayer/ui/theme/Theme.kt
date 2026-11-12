package com.example.gymqrdisplayer.ui.theme

import android.app.Activity
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

private val DarkColorScheme = darkColorScheme(
    primary = TealLight,
    onPrimary = Color(0xFF00332E),
    primaryContainer = Color(0xFF0F4C5C),
    onPrimaryContainer = Color(0xFFB8F5EC),
    secondary = Color(0xFF9CC9E8),
    secondaryContainer = Color(0xFF173A57),
    onSecondaryContainer = Color(0xFFD3E8F7),
    background = NavyDeep,
    surface = NavyDeep,
    surfaceVariant = Color(0xFF1A2C3D),
    onSurfaceVariant = Color(0xFFB4C3D1),
    surfaceContainer = Color(0xFF10233A),
    surfaceContainerHigh = Color(0xFF162B44),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF006B62),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F5EC),
    onPrimaryContainer = Color(0xFF00201C),
    secondary = Navy,
    secondaryContainer = Color(0xFFD3E8F7),
    onSecondaryContainer = Navy,
    background = Color(0xFFF5F8FA),
    surface = Color(0xFFF5F8FA),
    surfaceVariant = Color(0xFFE2EAF0),
    onSurfaceVariant = Color(0xFF475867),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEDF2F6),
)

@Composable
fun GYMQRDisplayerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
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
        typography = Typography,
        content = content
    )
}
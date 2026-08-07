package com.vpn.member.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val KuayunBlue = Color(0xFF1B4DFF)
private val KuayunCyan = Color(0xFF00D4FF)
private val KuayunDarkBg = Color(0xFF0A0E17)
private val KuayunDarkSurface = Color(0xFF141B2D)
private val KuayunDarkCard = Color(0xFF1A2338)

private val DarkColors =
    darkColorScheme(
        primary = KuayunCyan,
        onPrimary = KuayunDarkBg,
        primaryContainer = Color(0xFF0D3D66),
        onPrimaryContainer = KuayunCyan,
        secondary = KuayunBlue,
        onSecondary = Color.White,
        background = KuayunDarkBg,
        onBackground = Color(0xFFE8EDF5),
        surface = KuayunDarkSurface,
        onSurface = Color(0xFFE8EDF5),
        surfaceVariant = KuayunDarkCard,
        onSurfaceVariant = Color(0xFF9AA8BC),
        error = Color(0xFFFF6B6B),
        onError = Color.White,
        outline = Color(0xFF2A3548),
    )

private val LightColors =
    lightColorScheme(
        primary = KuayunBlue,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFD6E4FF),
        onPrimaryContainer = Color(0xFF0A2463),
        secondary = KuayunCyan,
        onSecondary = KuayunDarkBg,
        background = Color(0xFFF4F7FC),
        onBackground = Color(0xFF0F1729),
        surface = Color.White,
        onSurface = Color(0xFF0F1729),
        surfaceVariant = Color(0xFFE8EEF8),
        onSurfaceVariant = Color(0xFF5A6B82),
        error = Color(0xFFD32F2F),
        outline = Color(0xFFC5D0E0),
    )

@Composable
fun KuayunTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        content = content,
    )
}

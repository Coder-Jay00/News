package com.intelligence.brief.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Professional News Color Palette (Light)
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF1A365D),           // Deep Navy
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3B82F6),  // Blue
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF06B6D4),         // Cyan/Teal
    onSecondary = Color.White,
    background = Color(0xFFF8FAFC),        // Light Gray-White
    onBackground = Color(0xFF1E293B),      // Slate
    surface = Color.White,
    onSurface = Color(0xFF1E293B),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),  // Muted text
    outline = Color(0xFFCBD5E1)
)

// Professional News Color Palette (Dark)
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),           // Light Blue
    onPrimary = Color(0xFF1E293B),
    primaryContainer = Color(0xFF1A365D),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF22D3EE),         // Bright Cyan
    onSecondary = Color(0xFF0F172A),
    background = Color(0xFF0F172A),        // Deep Dark Blue
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF1E293B),           // Slate surface
    onSurface = Color(0xFFF1F5F9),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569)
)

@Composable
fun BriefTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disable for consistent branding
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
        typography = Typography(), // Default M3 Typography
        content = content
    )
}

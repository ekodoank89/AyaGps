package com.aya.module.presentation.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Palet fallback (Android < 12)
private val Blue10 = Color(0xFF001D36)
private val Blue20 = Color(0xFF003258)
private val Blue40 = Color(0xFF0061A4)
private val Blue80 = Color(0xFFA0CAFD)
private val Blue90 = Color(0xFFD1E4FF)
private val Gray10 = Color(0xFF101C2B)
private val Gray40 = Color(0xFF535F70)
private val Gray80 = Color(0xFFBBC7DB)
private val Gray90 = Color(0xFFD7E3F7)

private val LightColorScheme = lightColorScheme(
    primary = Blue40,
    onPrimary = Color.White,
    primaryContainer = Blue90,
    onPrimaryContainer = Blue10,
    secondary = Gray40,
    onSecondary = Color.White,
    secondaryContainer = Gray90,
    onSecondaryContainer = Gray10
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue80,
    onPrimary = Blue20,
    primaryContainer = Blue20,
    onPrimaryContainer = Blue90,
    secondary = Gray80,
    onSecondary = Gray10,
    secondaryContainer = Gray40,
    onSecondaryContainer = Gray90
)

@Composable
fun AyaGpsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // Material You (Android 12+)
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
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

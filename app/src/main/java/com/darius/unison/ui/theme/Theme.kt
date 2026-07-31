package com.darius.unison.ui.theme

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

private val LightColors =
    lightColorScheme(
        primary = Color(0xFF345CA8),
        onPrimary = Color.White,
        secondary = Color(0xFF53607A),
        tertiary = Color(0xFF6D5689),
    )

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFA9C7FF),
        secondary = Color(0xFFBBC7E4),
        tertiary = Color(0xFFD8B9F5),
    )

@Composable
fun UnisonTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark ->
                dynamicDarkColorScheme(context)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
            dark -> DarkColors
            else -> LightColors
        }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}

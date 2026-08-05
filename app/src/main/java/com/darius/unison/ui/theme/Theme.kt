package com.darius.unison.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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

private val BaseTypography = Typography()
private val UnisonTypography =
    Typography(
        displayLarge = BaseTypography.displayLarge,
        displayMedium = BaseTypography.displayMedium,
        displaySmall = BaseTypography.displaySmall,
        headlineLarge = BaseTypography.headlineLarge,
        headlineMedium = BaseTypography.headlineMedium,
        headlineSmall = BaseTypography.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = BaseTypography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = BaseTypography.titleSmall,
        bodyLarge = BaseTypography.bodyLarge,
        bodyMedium = BaseTypography.bodyMedium,
        bodySmall = BaseTypography.bodySmall,
        labelLarge = BaseTypography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
        labelMedium = BaseTypography.labelMedium,
        labelSmall = BaseTypography.labelSmall,
    )

private val UnisonShapes =
    Shapes(
        extraSmall = RoundedCornerShape(10.dp),
        small = RoundedCornerShape(14.dp),
        medium = RoundedCornerShape(18.dp),
        large = RoundedCornerShape(26.dp),
        extraLarge = RoundedCornerShape(32.dp),
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
    MaterialTheme(
        colorScheme = colors,
        typography = UnisonTypography,
        shapes = UnisonShapes,
        content = content,
    )
}

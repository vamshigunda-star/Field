package com.vamshi.field.ui.theme

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
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFF93C5FD),
    secondary = DynamicOrange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF431407),
    onSecondaryContainer = Color(0xFFFFD7BA),
    tertiary = AquaCyan,
    onTertiary = Color(0xFF003640),
    background = Color(0xFF111827),
    onBackground = Color(0xFFF9FAFB),
    surface = Color(0xFF1F2937),
    onSurface = Color(0xFFF9FAFB),
    surfaceVariant = Color(0xFF374151),
    onSurfaceVariant = Color(0xFF9CA3AF),
    surfaceContainer = Color(0xFF1F2937),
    surfaceContainerLow = Color(0xFF111827),
    surfaceContainerHigh = Color(0xFF374151),
    surfaceContainerHighest = Color(0xFF4B5563),
    outline = Color(0xFF4B5563),
    outlineVariant = Color(0xFF374151),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF4C0519),
    onErrorContainer = Color(0xFFFDA4AF)
)

private val LightColorScheme = lightColorScheme(
    primary = ElectricBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E7FF),
    onPrimaryContainer = ElectricBlue,
    secondary = DynamicOrange,
    onSecondary = Color.White,
    secondaryContainer = SportOrangeContainer,
    onSecondaryContainer = SportOrange,
    tertiary = AquaCyan,
    onTertiary = Color.White,
    background = BackgroundLight,
    onBackground = TextPrimary,
    surface = SurfaceWhite,
    onSurface = TextPrimary,
    surfaceVariant = Color(0xFFF3F4F6),
    onSurfaceVariant = TextSecondary,
    surfaceContainer = SurfaceWhite,
    surfaceContainerLow = BackgroundLight,
    surfaceContainerHigh = Color(0xFFF3F4F6),
    surfaceContainerHighest = Color(0xFFE5E7EB),
    outline = OutlineGrey,
    outlineVariant = Color(0xFFE5E7EB),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = PerformanceRed,
    onErrorContainer = PerformanceRedText
)

@Composable
fun FieldTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    // We default to false to ensure brand consistency
    dynamicColor: Boolean = false,
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
        shapes = Shapes,
        content = content
    )
}

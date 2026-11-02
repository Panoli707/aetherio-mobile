package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val Ink = Color(0xFF080F24)
internal val Panel = Color(0xFF14233F)
internal val Cream = Color(0xFFF4F7FF)
internal val Muted = Color(0xFFB5C4DF)
internal val Aqua = Color(0xFF55E6D1)
internal val Violet = Color(0xFFB39AFF)
internal val Coral = Color(0xFFFF927D)
internal val Rating = Aqua

@Composable
internal fun AetherioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Aqua, onPrimary = Ink, primaryContainer = Color(0xFF17443F), onPrimaryContainer = Cream,
            secondary = Violet, onSecondary = Ink, secondaryContainer = Color(0xFF30294E), onSecondaryContainer = Cream,
            tertiary = Coral, onTertiary = Ink, tertiaryContainer = Color(0xFF4B2937), onTertiaryContainer = Cream,
            background = Ink, onBackground = Cream, surface = Panel, onSurface = Cream,
            surfaceVariant = Color(0xFF203554), onSurfaceVariant = Muted,
            surfaceContainerLowest = Ink, surfaceContainerLow = Color(0xFF101B34), surfaceContainer = Panel,
            surfaceContainerHigh = Color(0xFF1B2D4B), surfaceContainerHighest = Color(0xFF203554),
            surfaceDim = Ink, surfaceBright = Color(0xFF26395A), surfaceTint = Violet,
            outline = Color(0xFF59729B), outlineVariant = Color(0xFF304767), error = Color(0xFFFFB4AB)
        ),
        typography = Typography(
            headlineLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp),
            headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 25.sp),
            titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 20.sp),
            titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
            titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp),
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
            bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp),
            labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp)
        ),
        content = content
    )
}

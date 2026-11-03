package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

internal val Ink = Color(0xFF0B0B0F)
internal val Panel = Color(0xFF17171E)
internal val RaisedPanel = Color(0xFF202028)
internal val HighestPanel = Color(0xFF292932)
internal val Cream = Color(0xFFF5F5F7)
internal val Muted = Color(0xFFAAAAB8)
internal val Accent = Color(0xFFA78BFA)
internal val AccentContainer = Color(0xFF2B2140)
internal val OnAccentContainer = Color(0xFFE9DDFF)
internal val Outline = Color(0xFF777786)
internal val Divider = Color(0xFF34343F)
internal val Success = Color(0xFF86D9AC)
internal val Warning = Color(0xFFEAC476)
internal val Error = Color(0xFFFFB4AB)
internal val ErrorContainer = Color(0xFF3B191B)
internal val Rating = Cream
// Keep glass readable over even the brightest artwork/video frames.
internal const val GlassPanelAlpha = .94f

internal val AetherioColors = darkColorScheme(
    primary = Accent, onPrimary = Ink, primaryContainer = AccentContainer, onPrimaryContainer = OnAccentContainer,
    secondary = Muted, onSecondary = Ink, secondaryContainer = RaisedPanel, onSecondaryContainer = Cream,
    tertiary = Cream, onTertiary = Ink, tertiaryContainer = HighestPanel, onTertiaryContainer = Cream,
    background = Ink, onBackground = Cream, surface = Panel, onSurface = Cream,
    surfaceVariant = RaisedPanel, onSurfaceVariant = Muted,
    surfaceContainerLowest = Ink, surfaceContainerLow = Panel, surfaceContainer = Panel,
    surfaceContainerHigh = RaisedPanel, surfaceContainerHighest = HighestPanel,
    surfaceDim = Ink, surfaceBright = HighestPanel, surfaceTint = Color.Transparent,
    inverseSurface = Cream, inverseOnSurface = Ink, inversePrimary = AccentContainer,
    outline = Outline, outlineVariant = Divider, scrim = Color.Black,
    error = Error, onError = Ink, errorContainer = ErrorContainer, onErrorContainer = Error
)

@Composable
internal fun AetherioTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AetherioColors,
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
        content = { CompositionLocalProvider(LocalContentColor provides Cream, content = content) }
    )
}

package com.panoli707.aetherio.nativeapp.ui

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
internal enum class ThemePalette(val label: String, val accent: Color, val container: Color, val onContainer: Color) {
    VIOLET("Violeta", Color(0xFFA78BFA), Color(0xFF2B2140), Color(0xFFE9DDFF)),
    BLUE("Azul", Color(0xFF69B7FF), Color(0xFF15304A), Color(0xFFD8ECFF)),
    GREEN("Verde", Color(0xFF62D6A5), Color(0xFF123A2C), Color(0xFFD0F7E5)),
    PINK("Rosa", Color(0xFFFF8FCB), Color(0xFF45203A), Color(0xFFFFD9EC)),
    AMBER("Ámbar", Color(0xFFFFC66D), Color(0xFF49351C), Color(0xFFFFE8C2))
}

private var activePalette by mutableStateOf(ThemePalette.VIOLET)
private var transparentControls by mutableStateOf(false)
internal val Accent get() = activePalette.accent
internal val AccentContainer get() = activePalette.container
internal val OnAccentContainer get() = activePalette.onContainer
internal val TransparentControlsEnabled get() = transparentControls
internal val AppButtonAlpha get() = if (transparentControls) .28f else 1f
internal val ButtonSurfaceAlpha get() = if (transparentControls) .22f else .88f

internal fun loadThemePalette(context: Context) {
    val preferences = context.getSharedPreferences("aetherio_theme_v1", Context.MODE_PRIVATE)
    val name = preferences.getString("palette", null)
    activePalette = ThemePalette.entries.firstOrNull { it.name == name } ?: ThemePalette.VIOLET
    transparentControls = preferences.getBoolean("transparent_controls", false)
}

internal fun saveThemePalette(context: Context, palette: ThemePalette): Boolean {
    val saved = runCatching { context.applicationContext.getSharedPreferences("aetherio_theme_v1", Context.MODE_PRIVATE)
        .edit().putString("palette", palette.name).commit() }.getOrDefault(false)
    if (saved) activePalette = palette
    return saved
}

internal fun saveTransparentControls(context: Context, enabled: Boolean): Boolean {
    val saved = runCatching { context.applicationContext.getSharedPreferences("aetherio_theme_v1", Context.MODE_PRIVATE)
        .edit().putBoolean("transparent_controls", enabled).commit() }.getOrDefault(false)
    if (saved) transparentControls = enabled
    return saved
}
internal val Outline = Color(0xFF777786)
internal val Divider = Color(0xFF34343F)
internal val Success = Color(0xFF86D9AC)
internal val Warning = Color(0xFFEAC476)
internal val Error = Color(0xFFFFB4AB)
internal val ErrorContainer = Color(0xFF3B191B)
internal val Rating = Cream
// Keep glass readable over even the brightest artwork/video frames.
internal const val GlassPanelAlpha = .94f

internal val AetherioColors get() = darkColorScheme(
    primary = Accent.copy(alpha = AppButtonAlpha), onPrimary = if (TransparentControlsEnabled) Cream else Ink,
    primaryContainer = AccentContainer.copy(alpha = if (TransparentControlsEnabled) .38f else 1f), onPrimaryContainer = OnAccentContainer,
    secondary = Muted, onSecondary = Ink,
    secondaryContainer = RaisedPanel.copy(alpha = if (TransparentControlsEnabled) .38f else 1f), onSecondaryContainer = Cream,
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

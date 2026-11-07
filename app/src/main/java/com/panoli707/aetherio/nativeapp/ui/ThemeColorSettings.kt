package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun ThemeColorSettings(onBack: () -> Unit) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf(ThemePalette.entries.firstOrNull { it.accent == Accent } ?: ThemePalette.VIOLET) }

    Column(Modifier.fillMaxSize().background(Ink).testTag("themeColorSettings")) {
        ScreenHeader("Colores de Aetherio", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Color de acento", style = MaterialTheme.typography.titleLarge)
            Text("Elige el color que se usa en botones, iconos y elementos destacados de Aetherio.", color = Muted)

            Surface(shape = RoundedCornerShape(20.dp), color = Panel.copy(alpha = if (TransparentControlsEnabled) .22f else 1f),
                border = BorderStroke(1.dp, Outline)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Transparencia", style = MaterialTheme.typography.titleMedium)
                        Text("Aplica un fondo translúcido al cuadro del perfil y a los botones de Aetherio.",
                            color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = TransparentControlsEnabled, onCheckedChange = { enabled ->
                        saveTransparentControls(context, enabled)
                    }, modifier = Modifier.testTag("themeTransparency"))
                }
            }

            ThemePalette.entries.forEach { palette ->
                val isSelected = selected == palette
                Surface(
                    modifier = Modifier.fillMaxWidth().testTag("themePalette:${palette.name}")
                        .clickable {
                            if (saveThemePalette(context, palette)) {
                                selected = palette
                            }
                        },
                    shape = RoundedCornerShape(20.dp), color = Panel.copy(alpha = if (TransparentControlsEnabled) .22f else 1f),
                    border = BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) palette.accent else Outline)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(palette.accent), contentAlignment = Alignment.Center) {
                            if (isSelected) Icon(Icons.Default.Check, "Seleccionado", tint = Ink)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(palette.label, style = MaterialTheme.typography.titleMedium)
                            Text("Vista previa del tema", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.Palette, null, tint = palette.accent)
                    }
                }
            }
        }
    }
}

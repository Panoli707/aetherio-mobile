package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.panoli707.aetherio.nativeapp.data.*

@Composable
internal fun HomeDesignSettings(state: AppState, onChange: (HomePreferences) -> Unit) {
    val preferences = state.homePreferences
    val enabled = !state.homePreferencesSaving
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    Text("Pantalla de inicio", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
    Text("Contenido principal", style = MaterialTheme.typography.titleMedium)
    val choices = listOf(
        HomeContentPreference.MOVIES_SERIES to "Películas y series",
        HomeContentPreference.ANIME to "Anime",
        HomeContentPreference.BOTH to "Ambos"
    )
    // A row of three buttons is too narrow on small screens and at enlarged font sizes.
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { (value, label) ->
            FilterChip(selected = preferences.content == value, enabled = enabled, onClick = { onChange(preferences.copy(content = value)) },
                label = { Text(label) }, modifier = Modifier.fillMaxWidth().testTag("designContent:${value.name}"))
        }
    }
    Text("Las portadas iniciales muestran solo el tipo elegido; en Ambos se combinan. Los demás catálogos siguen disponibles.", color = Muted, style = MaterialTheme.typography.bodySmall)
    Text(if (state.homePreferencesSaving) "Guardando…" else "Los cambios se guardan automáticamente en este dispositivo.",
        color = Muted, style = MaterialTheme.typography.bodySmall)
    state.homePreferencesError?.let { message ->
        TimedErrorText(message, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            style = MaterialTheme.typography.bodyMedium)
    }
    if (preferences.content == HomeContentPreference.BOTH) {
        Text("¿Con qué preferencia?", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !preferences.animeFirst, enabled = enabled, onClick = { onChange(preferences.copy(animeFirst = false)) },
                label = { Text("Películas y series") }, modifier = Modifier.testTag("designFirst:movies"))
            FilterChip(selected = preferences.animeFirst, enabled = enabled, onClick = { onChange(preferences.copy(animeFirst = true)) },
                label = { Text("Anime") }, modifier = Modifier.testTag("designFirst:anime"))
        }
    }
    DesignToggle("Pósteres horizontales", "Solo afecta a los catálogos de Inicio; Continuar viendo conserva su formato.",
        preferences.horizontalPosters, { onChange(preferences.copy(horizontalPosters = it)) }, "designPosters", enabled)
    Text("Reordenar los catálogos", style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.semantics { heading() })
    Text("Oculta filas o cambia su posición. Los catálogos nuevos se añadirán al final de tus preferencias.",
        color = Muted, style = MaterialTheme.typography.bodySmall)
    val sections = DesktopHomeCatalog.rails().distinctBy { it.id }.map { rail ->
        state.sections.firstOrNull { it.id == rail.id } ?: rail.section()
    }
    val ordered = preferences.arranged(sections)
    ordered.forEachIndexed { index, section ->
        Surface(color = Panel, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Divider),
            modifier = Modifier.fillMaxWidth().testTag("designRail:${section.id}")) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(section.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val count = state.sections.firstOrNull { it.id == section.id }?.items?.size
                        if (count != null) Text("$count títulos", style = MaterialTheme.typography.bodySmall, color = Muted)
                    }
                    Switch(checked = section.id !in preferences.hiddenCatalogIds, enabled = enabled, onCheckedChange = { checked ->
                        onChange(preferences.copy(hiddenCatalogIds = if (checked) preferences.hiddenCatalogIds - section.id
                            else preferences.hiddenCatalogIds + section.id))
                    }, modifier = Modifier.testTag("designVisible:${section.id}").semantics { contentDescription = "Mostrar ${section.title} en Inicio" })
                }
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { onChange(preferences.move(section.id, -1, sections)) }, enabled = enabled && index > 0,
                        modifier = Modifier.testTag("designUp:${section.id}")) {
                        Icon(Icons.Default.ArrowUpward, "Subir ${section.title}")
                    }
                    IconButton(onClick = { onChange(preferences.move(section.id, 1, sections)) },
                        enabled = enabled && index < ordered.lastIndex,
                        modifier = Modifier.testTag("designDown:${section.id}")) {
                        Icon(Icons.Default.ArrowDownward, "Bajar ${section.title}")
                    }
                }
            }
        }
    }
    OutlinedButton(onClick = { confirmReset = true }, enabled = enabled, modifier = Modifier.fillMaxWidth().testTag("designReset")) {
        Text("Restablecer diseño de Inicio")
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false },
        title = { Text("¿Restablecer el diseño de Inicio?") },
        text = { Text("Se restaurarán el orden, la visibilidad y el formato de los catálogos. Tu biblioteca e historial no cambiarán.") },
        confirmButton = { TextButton(enabled = enabled, onClick = { confirmReset = false; onChange(HomePreferences()) }) { Text("Restablecer") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancelar") } })
}

@Composable
private fun DesignToggle(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit, tag: String, enabled: Boolean) {
    Surface(color = Panel, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Divider)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked, onChange, enabled = enabled, modifier = Modifier.testTag(tag).semantics { contentDescription = title })
        }
    }
}

package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.panoli707.aetherio.nativeapp.data.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SourcePreferenceButton(preferences: SourcePreferences, sources: List<StreamSource>,
    selectedProvider: String?, onChange: (SourcePreferences) -> Unit) {
    var contextMenu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf(false) }
    var favoritesOnly by remember { mutableStateOf(false) }
    Box {
        Box(Modifier.size(48.dp).background(Cream.copy(alpha = .07f), CircleShape)
            .border(1.dp, if (preferences.active) Accent else Outline, CircleShape)
            .combinedClickable(onClickLabel = "Abrir preferencias", onLongClickLabel = "Abrir menú de filtros",
                onClick = { favoritesOnly = false; dialog = true }, onLongClick = { contextMenu = true })
            .semantics { contentDescription = "Preferencias de fuentes"; role = Role.Button
                stateDescription = if (preferences.active) "Preferencias activas" else "Sin preferencias activas" },
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.FilterList, null, tint = if (preferences.active) Accent else Cream)
        }
        DropdownMenu(expanded = contextMenu, onDismissRequest = { contextMenu = false }) {
            DropdownMenuItem(text = { Text("Idioma y calidad") }, onClick = { contextMenu = false; favoritesOnly = false; dialog = true })
            DropdownMenuItem(text = { Text("Gestionar favoritos") }, onClick = { contextMenu = false; favoritesOnly = true; dialog = true })
            sources.firstOrNull { it.providerKey == selectedProvider }?.let { source ->
                val favorite = SourcePreferenceRules.providerKey(source) in preferences.favoriteProviders
                DropdownMenuItem(text = { Text(if (favorite) "Quitar proveedor de favoritos" else "Marcar proveedor favorito") },
                    onClick = { contextMenu = false; onChange(SourcePreferenceRules.toggleProvider(source, preferences)) })
            }
            DropdownMenuItem(text = { Text("Restablecer preferencias") }, onClick = {
                contextMenu = false
                // Reset ranking only; removing favorites is a separate, explicit action.
                onChange(preferences.copy(language = SourceLanguage.ANY, quality = SourceQuality.ANY, favoritesFirst = true))
            })
        }
    }
    if (dialog) SourcePreferencesDialog(preferences, sources, favoritesOnly, onChange, { dialog = false })
}

@Composable
private fun SourcePreferencesDialog(preferences: SourcePreferences, sources: List<StreamSource>, favoritesOnly: Boolean,
    onChange: (SourcePreferences) -> Unit, onDismiss: () -> Unit) {
    var confirmClear by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Ink, titleContentColor = Cream,
        textContentColor = Cream, title = { Text(if (favoritesOnly) "Fuentes favoritas" else "Preferencias de fuentes") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!favoritesOnly) {
                    Text("Se muestran primero favoritos, después idioma y después calidad. No se ocultan resultados.", color = Muted)
                    SourcePreferenceChoice("Idioma preferido", preferences.language, SourceLanguage.entries,
                        { it.label }, { onChange(preferences.copy(language = it)) })
                    SourcePreferenceChoice("Calidad preferida", preferences.quality, SourceQuality.entries,
                        { it.label }, { onChange(preferences.copy(quality = it)) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Favoritos primero", Modifier.weight(1f))
                    Switch(checked = preferences.favoritesFirst, onCheckedChange = { onChange(preferences.copy(favoritesFirst = it)) },
                        modifier = Modifier.semantics { contentDescription = "Priorizar favoritos" })
                }
                Text("Proveedores favoritos", style = MaterialTheme.typography.titleSmall)
                Text("Se aplica también a otros títulos del mismo proveedor.", color = Muted)
                sources.distinctBy(SourcePreferenceRules::providerKey).forEach { source ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = SourcePreferenceRules.providerKey(source) in preferences.favoriteProviders,
                            onCheckedChange = { onChange(SourcePreferenceRules.toggleProvider(source, preferences)) },
                            modifier = Modifier.semantics { contentDescription = "Proveedor favorito ${source.providerName}" })
                        Text(source.providerName, Modifier.weight(1f))
                    }
                }
                if (sources.isEmpty()) Text("Los proveedores aparecerán al recibir fuentes.", color = Muted)
                val knownProviders = sources.map(SourcePreferenceRules::providerKey).toSet()
                val missingProviders = preferences.favoriteProviders - knownProviders
                if (missingProviders.isNotEmpty()) Text("${missingProviders.size} proveedores favoritos no aparecen en esta búsqueda.", color = Muted)
                Text("Fuentes favoritas (${preferences.favoriteSources.size})", style = MaterialTheme.typography.titleSmall)
                Text("Usa la estrella de una tarjeta para guardar esa fuente. En HTTP, un enlace renovado puede requerir marcarla de nuevo; para todos los títulos, marca el proveedor.", color = Muted)
                val knownSources = sources.associateBy(SourcePreferenceRules::sourceKey)
                preferences.favoriteSources.forEach { key ->
                    val source = knownSources[key]
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(source?.let { "${it.providerName} · ${it.display.title ?: it.name}" }
                            ?: "Fuente no presente · ${key.take(8)}", Modifier.weight(1f), maxLines = 3)
                        TextButton(onClick = { onChange(preferences.copy(favoriteSources = preferences.favoriteSources - key)) }) { Text("Quitar") }
                    }
                }
                // Missing providers can be removed individually without discarding any other favorite.
                missingProviders.forEach { key ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Proveedor no presente · ${key.take(8)}", Modifier.weight(1f))
                        TextButton(onClick = { onChange(preferences.copy(favoriteProviders = preferences.favoriteProviders - key)) }) { Text("Quitar") }
                    }
                }
                if (preferences.favoriteSources.isNotEmpty() || preferences.favoriteProviders.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) { Text("Borrar todos los favoritos") }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Listo") } })
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("¿Borrar todos los favoritos?") },
        text = { Text("No se eliminarán proveedores ni se cambiarán el idioma o la calidad preferidos.") },
        confirmButton = { TextButton(onClick = { onChange(preferences.copy(favoriteSources = emptySet(), favoriteProviders = emptySet())); confirmClear = false }) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } })
}

@Composable
private fun <T> SourcePreferenceChoice(title: String, selected: T, values: List<T>, label: (T) -> String, onSelect: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$title: ${label(selected)}") }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 320.dp)) {
            values.forEach { value -> DropdownMenuItem(text = { Text(label(value)) }, onClick = { expanded = false; onSelect(value) },
                modifier = Modifier.semantics { this.selected = value == selected }) }
        }
    }
}

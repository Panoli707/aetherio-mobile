package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.panoli707.aetherio.nativeapp.data.*
import com.panoli707.aetherio.nativeapp.player.VideoFormat
import java.net.URI
import kotlinx.coroutines.launch

@Composable
internal fun AddonsSettings(state: AppState, onAdd: (String) -> Unit, onRemove: (String) -> Unit,
    onDesktopSourceToggle: (String, Boolean) -> Unit) {
    var url by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(false) }
    var startedOperation by remember { mutableLongStateOf(0) }
    var success by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val busy = state.addonSaving || pending || state.addonsLoading
    LaunchedEffect(pending, state.addonOperation) {
        if (pending && state.addonOperation != startedOperation) {
            pending = false
            success = state.addonError == null
            if (success) url = ""
        }
    }
    val submit = {
        if (!busy) {
            val candidate = url.trim()
            val uri = runCatching { URI(candidate) }.getOrNull()
            invalid = uri == null || !uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank() ||
                uri.rawUserInfo != null || uri.rawFragment != null || !uri.path.orEmpty().endsWith("/manifest.json")
            if (!invalid) {
                focus.clearFocus()
                startedOperation = state.addonOperation
                pending = true
                success = false
                onAdd(candidate)
            }
        }
    }
    Text("Aetherio Provider Hub", style = MaterialTheme.typography.titleLarge)
    Text("Resolvedor predeterminado integrado. No necesitas añadir addons: el Hub agrega las fuentes en el servidor. Catálogos y fichas siguen integrados en Mobile.", color = Muted)
    Text("Fuentes adicionales (opcionales)", style = MaterialTheme.typography.titleLarge)
    Text("Desactivadas por defecto para no duplicar las consultas del Hub. Las selecciones antiguas se conservan sin borrarlas; activa aquí solo las fuentes adicionales que quieras consultar directamente. AnimeAV1 sigue siendo nativo e independiente de AnimES.",
        color = Muted)
    DesktopSources.providers.forEach { provider ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(provider.name, style = MaterialTheme.typography.titleMedium)
                if (provider.id in state.desktopSourceErrors) {
                    Text("No respondió en la última consulta", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            Switch(checked = provider.id in state.enabledDesktopSources,
                onCheckedChange = { onDesktopSourceToggle(provider.id, it) },
                modifier = Modifier.semantics { contentDescription = provider.name })
        }
    }
    Text("Proveedores propios", style = MaterialTheme.typography.titleLarge)
    Text("Añade la URL HTTPS de un manifest.json compatible para buscar fuentes desde las fichas. " +
        "Usa solo proveedores y contenidos que tengas permiso para reproducir.", color = Muted)
    OutlinedTextField(value = url, onValueChange = { url = it.take(8192); invalid = false; success = false },
        enabled = !busy, singleLine = true, isError = invalid,
        label = { Text("URL HTTPS del manifest.json") },
        supportingText = { Text("Puede incluir configuración privada. No se mostrará en la lista.") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }), modifier = Modifier.fillMaxWidth())
    Button(onClick = { submit() }, enabled = !busy && url.isNotBlank()) { Text("Añadir proveedor") }
    if (state.addonSaving || pending) LoadingRow("Guardando proveedor…")
    if (invalid || state.addonError != null) {
        val message = if (invalid) "Usa una URL HTTPS válida que termine en /manifest.json, sin usuario ni fragmento."
            else "No se pudo completar la operación. Revisa la URL, la conexión y que el manifiesto sea compatible."
        Text(message, color = MaterialTheme.colorScheme.error,
            modifier = Modifier.semantics { error(message); liveRegion = LiveRegionMode.Polite })
    }
    if (success && state.addonError == null) Text("Proveedor guardado. Ya puedes buscar fuentes desde las fichas.", color = Rating,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    if (state.addonsLoading) LoadingRow("Cargando proveedores…")
    else if (state.addons.none { !ProviderHub.isHub(it) }) Text("No has añadido proveedores propios.", color = Muted)
    state.addons.filterNot(ProviderHub::isHub).forEach { addon ->
        val host = remember(addon.manifestUrl) { runCatching { URI(addon.manifestUrl).host }.getOrNull() ?: "Host no disponible" }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(addon.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(host, color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { focus.clearFocus(); success = false; onRemove(addon.id) }, enabled = !busy) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar proveedor ${addon.name}")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SourceSheet(state: AppState, query: SourceQuery, onDismiss: () -> Unit,
    onPlay: (StreamSource, VideoFormat) -> Unit, onSettings: () -> Unit, onRetry: () -> Unit = {}) {
    val hasProviders = state.addons.isNotEmpty() || state.enabledDesktopSources.isNotEmpty()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var format by rememberSaveable(query.key) { mutableStateOf(VideoFormat.AUTO) }
    // Finish the sheet animation before changing the underlying destination.
    val closeThen: (() -> Unit) -> Unit = { action -> scope.launch { sheet.hide(); if (!sheet.isVisible) action() } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Panel, contentColor = Cream) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "title") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    RoundAction("Volver", Icons.Default.ArrowBack, { closeThen(onDismiss) })
                    Column(Modifier.weight(1f)) {
                        Text(query.item.title, style = MaterialTheme.typography.titleLarge)
                        Text(if (query.season != null && query.episode != null) "Temporada ${query.season} · Episodio ${query.episode}"
                            else "Fuentes de reproducción", color = Muted)
                    }
                }
            }
            item(key = "format") {
                Text("Formato de reproducción", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoFormat.entries.forEach { option ->
                        FilterChip(selected = format == option, onClick = { format = option },
                            label = { Text(if (option == VideoFormat.AUTO) "Auto" else option.label) })
                    }
                }
                Text("Auto respeta el formato indicado por cada fuente. Los metadatos proceden del proveedor; no verifican la calidad.",
                    color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            if (state.addonsLoading) item(key = "addonsLoading") { LoadingRow("Cargando proveedores…") }
            else if (!hasProviders) item(key = "configure") {
                StatusMessage("Configura tus proveedores", "Añade un manifiesto compatible en Ajustes para buscar fuentes de este título.",
                    "Ir a Ajustes", { closeThen(onSettings) })
            }
            if (state.sourcesLoading) item(key = "loading") { LoadingRow("Buscando fuentes…") }
            if (state.sourceError != null) item(key = "error") {
                val message = state.sourceError
                Text(message, color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.semantics { error(message); liveRegion = LiveRegionMode.Polite })
                if (!state.sourcesLoading) TextButton(onClick = onRetry) { Text("Reintentar búsqueda") }
            }
            if (!state.sourcesLoading && !state.addonsLoading && hasProviders && state.sourceError == null && state.sources.isEmpty()) item(key = "empty") {
                StatusMessage("Sin resultados", "Provider Hub y los proveedores opcionales no devolvieron fuentes para este título o episodio. Puedes volver a intentarlo.",
                    "Reintentar búsqueda", onRetry)
            }
            items(state.sources, key = { "${it.addonId}:${it.id}" }) { source ->
                Surface(color = Ink, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(source.addonName, color = Muted, style = MaterialTheme.typography.labelLarge)
                        Text(source.name.ifBlank { "Fuente de vídeo" }, style = MaterialTheme.typography.titleMedium)
                        if (source.description.isNotBlank()) Text(source.description, color = Muted, style = MaterialTheme.typography.bodyMedium)
                        if (source.url.startsWith("http://", true)) Text("Esta fuente usa vídeo HTTP sin cifrar en un dominio permitido.",
                            color = Muted, style = MaterialTheme.typography.bodySmall)
                        if (source.isTorrent) Text("Torrent conservado · Mobile no tiene motor torrent. Este resultado no es reproducible en esta versión.", color = Muted)
                        Button(onClick = { onPlay(source, if (format == VideoFormat.AUTO) source.format else format) }, enabled = source.playable,
                            modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Reproducir ${source.name}, proveedor ${source.addonName}" }) {
                            Text(if (source.isTorrent) "Requiere motor torrent" else "Reproducir")
                        }
                    }
                }
            }
        }
    }
}

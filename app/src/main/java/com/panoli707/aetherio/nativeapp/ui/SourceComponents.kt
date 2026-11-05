package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
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
    if (success && state.addonError == null) Text("Proveedor guardado. Ya puedes buscar fuentes desde las fichas.", color = Success,
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
    var selectedProvider by rememberSaveable(query.key) { mutableStateOf<String?>(null) }
    val providers = remember(state.sources) { state.sources.distinctBy { it.addonId }.map { it.addonId to it.addonName } }
    LaunchedEffect(state.sources, selectedProvider, state.sourcesLoading) {
        if (!state.sourcesLoading && selectedProvider != null && state.sources.none { it.addonId == selectedProvider }) selectedProvider = null
    }
    val visibleSources = remember(state.sources, selectedProvider) {
        if (selectedProvider == null) state.sources else state.sources.filter { it.addonId == selectedProvider }
    }
    // Finish the sheet animation before changing the underlying destination.
    val closeThen: (() -> Unit) -> Unit = { action -> scope.launch { sheet.hide(); if (!sheet.isVisible) action() } }
    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    // The app behind this dialog is blurred on API 31+; older Android keeps a darker translucent tint.
    // Source glass is deliberately lighter than opaque settings panels. Cards provide text contrast.
    val panelAlpha = if (android.os.Build.VERSION.SDK_INT >= 31) .38f else .72f
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = Color.Transparent,
        contentColor = Cream, tonalElevation = 0.dp, scrimColor = Color.Black.copy(alpha = .16f),
        dragHandle = null, shape = shape, sheetMaxWidth = 720.dp) {
        SourceSystemBars()
        Column(Modifier.fillMaxWidth().fillMaxHeight(.94f).clip(shape)
            .background(Brush.verticalGradient(listOf(Panel.copy(alpha = panelAlpha), Ink.copy(alpha = panelAlpha + .12f))))
            .border(1.dp, Cream.copy(alpha = .16f), shape).testTag("sourceGlassPanel")) {
            Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 6.dp)
                .size(width = 32.dp, height = 4.dp).background(Cream.copy(alpha = .35f), CircleShape))
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp).testTag("sourceToolbar"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SourceToolbarAction("Volver", Icons.AutoMirrored.Filled.ArrowBack, { closeThen(onDismiss) })
                Column(Modifier.weight(1f)) {
                    Text("Seleccionar fuente", style = MaterialTheme.typography.titleLarge)
                    Text(query.item.title, color = Cream, style = MaterialTheme.typography.titleMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (query.season != null && query.episode != null) "Temporada ${query.season} · Episodio ${query.episode}"
                        else "Fuentes de reproducción", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                SourceToolbarAction("Actualizar fuentes", Icons.Default.Refresh, onRetry,
                    enabled = !state.sourcesLoading && !state.addonsLoading, loading = state.sourcesLoading)
            }
            HorizontalDivider(color = Cream.copy(alpha = .10f))
            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)) {
                item(key = "provider:all") {
                    FilterChip(selected = selectedProvider == null, onClick = { selectedProvider = null },
                        colors = sourceFilterColors(), border = BorderStroke(1.dp, if (selectedProvider == null) Accent else Outline),
                        label = { Text("Todas ${state.sources.size}") }, modifier = Modifier.semantics {
                            contentDescription = "Todas las fuentes, ${state.sources.size}"
                        })
                }
                providers.forEach { (providerId, providerName) ->
                    val count = state.sources.count { it.addonId == providerId }
                    item(key = "provider:$providerId") {
                        FilterChip(selected = selectedProvider == providerId, onClick = { selectedProvider = providerId },
                            colors = sourceFilterColors(), border = BorderStroke(1.dp, if (selectedProvider == providerId) Accent else Outline),
                            label = { Text("$providerName $count", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            modifier = Modifier.semantics {
                                contentDescription = "$providerName, $count ${if (count == 1) "fuente" else "fuentes"}"
                            })
                    }
                }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("sourceResults"),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.addonsLoading) item(key = "addonsLoading") { LoadingRow("Cargando proveedores…") }
                else if (!hasProviders) item(key = "configure") {
                    StatusMessage("Configura tus proveedores", "Añade un manifiesto compatible en Ajustes para buscar fuentes de este título.",
                        "Ir a Ajustes", { closeThen(onSettings) })
                }
                if (state.sourcesLoading) item(key = "loading") { LoadingRow("Buscando fuentes…") }
                if (!state.sourcesLoading && state.sourceError != null && state.sources.none { it.playable }) item(key = "error") {
                    val message = state.sourceError
                    Text(message, color = Cream.copy(alpha = .85f),
                        modifier = Modifier.padding(vertical = 12.dp).testTag("sourceFailure")
                            .semantics { liveRegion = LiveRegionMode.Polite })
                }
                if (!state.sourcesLoading && !state.addonsLoading && hasProviders && state.sourceError == null && state.sources.isEmpty()) item(key = "empty") {
                    StatusMessage("Sin resultados", "No hay fuentes para este título o episodio. Usa el botón de actualizar para volver a intentarlo.")
                }
                items(visibleSources, key = { "${it.addonId}:${it.id}" }) { source ->
                    Surface(onClick = { onPlay(source, if (source.isTorrent) VideoFormat.AUTO else source.format) },
                        enabled = source.playable, color = Ink.copy(alpha = .60f), shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, Cream.copy(alpha = .12f)),
                        modifier = Modifier.fillMaxWidth().testTag("source:${source.addonId}:${source.id}").semantics {
                            contentDescription = "Seleccionar fuente ${source.name}, proveedor ${source.addonName}"
                        }) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text(source.name.ifBlank { "Fuente de vídeo" }, style = MaterialTheme.typography.titleMedium,
                                maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text("Presentado por ${source.addonName}", color = Muted, style = MaterialTheme.typography.labelLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (source.description.isNotBlank()) Text(source.description, color = Muted, style = MaterialTheme.typography.bodyMedium,
                                maxLines = 5, overflow = TextOverflow.Ellipsis)
                            val badges = buildList {
                                source.format.takeIf { it != VideoFormat.AUTO }?.label?.let(::add)
                                source.subtitles.map { it.lang.trim() }.filter(String::isNotEmpty).distinct().forEach { add("Sub: $it") }
                            }
                            if (badges.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(badges, key = { it }) { badge ->
                                        Surface(color = Cream.copy(alpha = .06f), shape = RoundedCornerShape(12.dp),
                                            border = BorderStroke(1.dp, Cream.copy(alpha = .12f))) {
                                            Text(badge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                                style = MaterialTheme.typography.labelLarge, color = Cream)
                                        }
                                    }
                                }
                            }
                            if (source.url.startsWith("http://", true)) Text("Esta fuente usa vídeo HTTP sin cifrar en un dominio permitido.",
                                color = Muted, style = MaterialTheme.typography.bodySmall)
                            if (source.isTorrent) Text("Torrent P2P · Requiere participantes disponibles y puede consumir muchos datos y almacenamiento.",
                                color = Muted, style = MaterialTheme.typography.bodySmall)
                            if (!source.playable) Text("Fuente no compatible", color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun sourceFilterColors() = FilterChipDefaults.filterChipColors(containerColor = Cream.copy(alpha = .06f),
    labelColor = Cream, selectedContainerColor = Accent.copy(alpha = .20f), selectedLabelColor = Cream)

@Composable
private fun SourceSystemBars() {
    val view = LocalView.current
    SideEffect {
        // Material3 1.3 uses system theme flags for its dialog. Keep white icons over our dark glass.
        (view.parent as? DialogWindowProvider)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
}

@Composable
private fun SourceToolbarAction(description: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit, enabled: Boolean = true, loading: Boolean = false) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(48.dp)
        .background(Cream.copy(alpha = .07f), CircleShape).border(1.dp, Outline, CircleShape)
        .semantics { contentDescription = description }) {
        if (loading) CircularProgressIndicator(Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp)
        else Icon(icon, null, tint = Cream.copy(alpha = if (enabled) 1f else .45f))
    }
}

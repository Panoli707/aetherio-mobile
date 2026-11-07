package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.panoli707.aetherio.nativeapp.data.*
import com.panoli707.aetherio.nativeapp.downloads.*
import java.util.Locale

@Composable
internal fun BatchDownloadDialog(item: MediaItem, season: Int, episodes: List<EpisodeItem>, downloads: List<MediaDownload>,
    onDismiss: () -> Unit, onConfirm: (List<EpisodeItem>) -> Unit) {
    val available = remember(item.key, season, episodes, downloads) { episodes.filter { episode ->
        episode.released && episode.season == season && downloads.none { it.target.item.key == item.key && it.target.season == season && it.target.episode == episode.number }
    } }
    var selected by rememberSaveable(item.key, season) { mutableStateOf(emptyList<Int>()) }
    val valid = available.filter { it.number in selected }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(28.dp), color = Panel, border = BorderStroke(1.dp, Outline),
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(.94f).fillMaxHeight(.9f).testTag("batchDownloads")) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.Download, null, tint = Accent)
                    Column(Modifier.weight(1f)) {
                        Text("Descargar episodios", style = MaterialTheme.typography.titleLarge)
                        Text("${item.title} · Temporada $season", color = Muted, maxLines = 2)
                    }
                }
                Text("Selecciona episodios y después una fuente real para cada uno. No se cambia de proveedor automáticamente.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text("Se descargan de uno en uno en redes no medidas (Wi-Fi). Audio y subtítulos dependen del archivo; los subtítulos externos de los addons no se guardan.", color = Muted, style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selected = if (valid.size == available.size) emptyList() else available.map { it.number } }, enabled = available.isNotEmpty()) {
                        Text(if (available.isNotEmpty() && valid.size == available.size) "Deseleccionar todo" else "Seleccionar todo")
                    }
                    Spacer(Modifier.weight(1f)); Text("${valid.size} seleccionados", color = Muted)
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (available.isEmpty()) item { Text("No hay episodios nuevos disponibles. Los ya añadidos se administran desde Perfil > Descargas.", color = Muted) }
                    items(episodes.filter { it.season == season }, key = { it.number }) { episode ->
                        val enabled = available.any { it.number == episode.number }
                        val checked = enabled && episode.number in selected
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ink.copy(alpha = .4f))
                            .clickable(enabled = enabled) { selected = if (checked) selected - episode.number else selected + episode.number }.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Checkbox(checked, onCheckedChange = { selected = if (it) selected + episode.number else selected - episode.number }, enabled = enabled)
                            AsyncImage(episode.imageUrl ?: item.posterUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(60.dp, 44.dp).clip(RoundedCornerShape(8.dp)))
                            Column(Modifier.weight(1f)) {
                                Text("E${episode.number} · ${episode.title}", maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(if (!episode.released) "Todavía no emitido" else if (!enabled) "Ya está en Descargas" else episode.runtimeMinutes?.let { "$it min" }.orEmpty(),
                                    color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancelar") }
                    Button(onClick = { onConfirm(valid) }, enabled = valid.isNotEmpty(), modifier = Modifier.weight(1f)) { Text("Continuar (${valid.size})") }
                }
            }
        }
    }
}

@Composable
internal fun DownloadsScreen(state: AppState, actions: PersonalActions, onBack: () -> Unit) {
    var deleting by remember { mutableStateOf<MediaDownload?>(null) }
    var choosing by remember { mutableStateOf<MediaDownload?>(null) }
    LazyColumn(Modifier.fillMaxSize().background(Ink).testTag("downloadsScreen"), contentPadding = PaddingValues(bottom = 132.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { ScreenHeader("Descargas", onBack) }
        item { Column(Modifier.padding(horizontal = 22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${state.downloads.count { it.stage == DownloadStage.COMPLETE }} disponibles · ${sizeText(state.downloads.sumOf { it.bytes })} guardados", style = MaterialTheme.typography.titleMedium)
            Text("Cola de una descarga a la vez, solo en redes no medidas. Los vídeos quedan en el almacenamiento privado de Aetherio, no en la caché temporal. Desinstalar o borrar datos elimina las descargas.", color = Muted, style = MaterialTheme.typography.bodySmall)
            state.downloadMessage?.let { Text(it, color = Muted) }
        } }
        if (state.downloads.isEmpty()) item { StatusMessage("Sin descargas", "Abre una película o usa el botón junto a Temporada en una serie para elegir qué guardar.") }
        items(state.downloads, key = { it.id }) { record ->
            Surface(Modifier.fillMaxWidth().padding(horizontal = 18.dp), shape = RoundedCornerShape(22.dp), color = Panel, border = BorderStroke(1.dp, Outline)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(record.target.imageUrl ?: record.target.item.posterUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp, 72.dp).clip(RoundedCornerShape(10.dp)))
                        Column(Modifier.weight(1f)) {
                            Text(record.label, style = MaterialTheme.typography.titleMedium)
                            Text(record.provider, color = Muted, style = MaterialTheme.typography.bodySmall)
                            Text(record.stage.label, color = if (record.stage == DownloadStage.COMPLETE) Success else Accent)
                            Text(sizeText(record.bytes) + if (record.total > 0) " / ${sizeText(record.total)}" else "", color = Muted, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (record.stage == DownloadStage.DOWNLOADING) {
                        val fraction = record.fraction
                        if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    record.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        when (record.stage) {
                            DownloadStage.COMPLETE -> TextButton(onClick = { actions.playDownload(record.id) }) { Icon(Icons.Default.PlayArrow, null); Text("Reproducir") }
                            DownloadStage.QUEUED, DownloadStage.PREPARING, DownloadStage.DOWNLOADING -> TextButton(onClick = { actions.downloadAction(record.id, "pause", null) }) { Text("Pausar") }
                            DownloadStage.NEEDS_FILE -> TextButton(onClick = { choosing = record }) { Text("Elegir archivo") }
                            else -> TextButton(onClick = { actions.downloadAction(record.id, "resume", null) }) { Text(if (record.stage == DownloadStage.FAILED) "Reintentar" else "Reanudar") }
                        }
                        if (record.stage !in listOf(DownloadStage.QUEUED, DownloadStage.PREPARING, DownloadStage.DOWNLOADING)) IconButton(onClick = { deleting = record }) {
                            Icon(Icons.Default.DeleteOutline, "Eliminar ${record.label}")
                        }
                    }
                }
            }
        }
    }
    deleting?.let { record -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Eliminar descarga") },
        text = { Text("Se borrarán el vídeo y los bloques parciales de ${record.label}. Tu biblioteca y progreso no cambian.") },
        confirmButton = { TextButton(onClick = { actions.downloadAction(record.id, "delete", null); deleting = null }) { Text("Eliminar") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }) }
    choosing?.let { record -> AlertDialog(onDismissRequest = { choosing = null }, title = { Text("Archivo del torrent") },
        text = { LazyColumn(Modifier.heightIn(max = 400.dp)) { items(record.files, key = { it.index }) { file ->
            TextButton(onClick = { actions.downloadAction(record.id, "resume", file.index); choosing = null }) { Text("${file.name} · ${sizeText(file.size)}") }
        } } }, confirmButton = { TextButton(onClick = { choosing = null }) { Text("Cerrar") } }) }
}

private fun sizeText(bytes: Long): String = if (bytes >= 1024L * 1024 * 1024) String.format(Locale.ROOT, "%.2f GiB", bytes / (1024.0 * 1024 * 1024))
    else String.format(Locale.ROOT, "%.1f MiB", bytes.coerceAtLeast(0) / (1024.0 * 1024))

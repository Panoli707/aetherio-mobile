package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.panoli707.aetherio.nativeapp.AetherioApplication
import com.panoli707.aetherio.nativeapp.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Mobile counterpart of desktop CardArtworkPicker: current, provider alternatives and reset. */
@Composable
internal fun MediaArtworkPicker(item: MediaItem, logos: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val provider = LocalArtworkProvider.current ?: (context.applicationContext as AetherioApplication).artworkProvider
    var kind by remember(item.key, logos) { mutableStateOf(if (logos) ArtworkSelectionKind.LOGO else ArtworkSelectionKind.PORTRAIT) }
    var options by remember(item.key, kind) { mutableStateOf<List<ArtworkOption>>(emptyList()) }
    var current by remember(item.key, kind) { mutableStateOf<String?>(null) }
    var manual by remember(item.key, kind) { mutableStateOf(false) }
    var loading by remember(item.key, kind) { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember(item.key, kind) { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(item.key, kind, retry) {
        loading = true
        error = null
        try {
            val selected = provider.selection(item, kind)
            manual = selected != null
            current = selected?.url ?: if (logos) provider.logo(item) else provider.resolve(item, kind == ArtworkSelectionKind.PORTRAIT).cover
            val initial = current?.takeIf(String::isNotEmpty)?.let { ArtworkOption(it, "Actual") }
            options = listOfNotNull(initial)
            options = (listOfNotNull(initial) + provider.options(item, kind)).distinctBy { it.url }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = "No se pudieron cargar las alternativas. Puedes reintentar sin perder tu selección." }
        finally { loading = false }
    }
    fun choose(url: String?) {
        scope.launch {
            busy = true
            error = null
            try {
                provider.select(item, kind, url)
                onDismiss()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "No se pudo guardar la selección" }
            finally { busy = false }
        }
    }
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(24.dp), color = Panel,
            modifier = Modifier.fillMaxWidth(.94f).widthIn(max = 680.dp).fillMaxHeight(.88f).testTag("mediaArtworkPicker")) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(if (logos) "Logo del medio" else "Portada del medio", style = MaterialTheme.typography.titleLarge)
                Text(item.title, style = MaterialTheme.typography.bodySmall, color = Muted)
                if (!logos) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == ArtworkSelectionKind.PORTRAIT, enabled = !busy,
                        onClick = { kind = ArtworkSelectionKind.PORTRAIT }, label = { Text("Vertical") })
                    FilterChip(selected = kind == ArtworkSelectionKind.BACKDROP, enabled = !busy,
                        onClick = { kind = ArtworkSelectionKind.BACKDROP }, label = { Text("Horizontal") })
                }
                if (loading || busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                error?.let { TimedErrorText(it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { choose(null) }, enabled = !busy && !loading,
                        modifier = Modifier.testTag("artworkReset")) { Text(if (manual) "Restablecer predeterminado" else "Predeterminado ✓") }
                    if (logos) TextButton(onClick = { choose("") }, enabled = !busy && !loading,
                        modifier = Modifier.testTag("artworkUseText")) { Text(if (current == "") "Usar texto ✓" else "Usar texto") }
                }
                LazyVerticalGrid(columns = GridCells.Adaptive(if (kind == ArtworkSelectionKind.PORTRAIT) 120.dp else 150.dp),
                    modifier = Modifier.weight(1f).fillMaxWidth().testTag("artworkOptions"),
                    verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(options, key = { it.url }) { option ->
                        val selected = current == option.url
                        Surface(onClick = { if (selected) onDismiss() else choose(option.url) }, enabled = !busy && !loading,
                            shape = RoundedCornerShape(12.dp), border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) Accent else Muted.copy(alpha = .25f)),
                            color = Ink, modifier = Modifier.testTag("artworkOption:${option.url}")) {
                            Column {
                                Artwork(option.url, Modifier.fillMaxWidth().aspectRatio(if (kind == ArtworkSelectionKind.PORTRAIT) 2f / 3f else 16f / 9f),
                                    description = option.label, contentScale = if (logos) ContentScale.Fit else ContentScale.Crop)
                                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (selected) "Actual" else option.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
                                    if (selected) Icon(Icons.Default.Check, "Seleccionado", tint = Accent, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
                if (!loading && options.isEmpty()) Text("No hay más imágenes disponibles para este título.", color = Muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (error != null) TextButton(onClick = { retry++ }, enabled = !busy) { Text("Reintentar") }
                    TextButton(onClick = onDismiss, enabled = !busy) { Text("Cerrar") }
                }
            }
        }
    }
}

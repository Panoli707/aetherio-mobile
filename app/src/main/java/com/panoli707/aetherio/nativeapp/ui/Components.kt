package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.panoli707.aetherio.nativeapp.data.MediaItem
import com.panoli707.aetherio.nativeapp.data.ArtworkImages
import com.panoli707.aetherio.nativeapp.player.MediaUrlPolicy
import com.panoli707.aetherio.nativeapp.player.VideoFormat
import java.util.Locale

@Composable
internal fun Artwork(url: String?, modifier: Modifier = Modifier, description: String? = null, placeholderColor: Color = Panel,
    highResolution: Boolean = false) {
    val placeholder = modifier.background(placeholderColor)
    val preferred = remember(url, highResolution) { if (highResolution) ArtworkImages.highResolution(url) else url }
    var failed by remember(preferred) { mutableStateOf(false) }
    if (LocalInspectionMode.current || url.isNullOrBlank()) Box(placeholder)
    else AsyncImage(model = if (failed) url else preferred, contentDescription = description, contentScale = ContentScale.Crop,
        filterQuality = FilterQuality.High, onError = { failed = true }, modifier = placeholder)
}

@Composable
internal fun RoundAction(description: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(48.dp).clip(CircleShape)
            .background(Panel.copy(alpha = .88f)).border(1.dp, Cream.copy(alpha = .14f), CircleShape)
    ) { Icon(icon, contentDescription = description, tint = Cream) }
}

@Composable
internal fun MediaTitle(item: MediaItem, modifier: Modifier = Modifier, centered: Boolean = false) {
    var failed by remember(item.logoUrl) { mutableStateOf(false) }
    val inspection = LocalInspectionMode.current
    Box(modifier, contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart) {
        if (!item.logoUrl.isNullOrBlank() && !failed && !inspection) {
            AsyncImage(
                model = item.logoUrl, contentDescription = item.title,
                contentScale = ContentScale.Fit, onError = { failed = true },
                modifier = Modifier.fillMaxWidth().height(88.dp)
            )
        } else {
            Text(item.title, style = MaterialTheme.typography.headlineLarge,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun Metadata(item: MediaItem, centered: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically) {
        if (item.score > 0) {
            Text(String.format(Locale.forLanguageTag("es"), "%.1f", item.score), color = Rating,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { contentDescription = "Valoración ${item.score} sobre 10" })
            Spacer(Modifier.width(10.dp))
        }
        Text(listOf(item.year, item.genres.take(2).joinToString(" · ")).filter { it.isNotBlank() }.joinToString(" · "),
            color = Muted, style = MaterialTheme.typography.bodySmall, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun Poster(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier, rank: Int? = null) {
    Column(modifier.clickable(onClickLabel = "Ver ${item.title}", onClick = onClick)
        .semantics(mergeDescendants = true) { }) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp))) {
            Artwork(item.posterUrl, Modifier.fillMaxSize())
            if (rank != null) {
                Box(Modifier.fillMaxWidth().height(64.dp).background(Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = .65f), Color.Transparent))))
                Text("$rank", color = Cream, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 9.dp, top = 3.dp)
                        .semantics { contentDescription = "Puesto $rank" })
            }
        }
        Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 7.dp))
        if (item.year.isNotBlank()) Text(item.year, color = Muted, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
internal fun Featured(item: MediaItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.aspectRatio(1.78f).clip(RoundedCornerShape(16.dp))
        .clickable(onClickLabel = "Ver ${item.title}", onClick = onClick)
        .semantics(mergeDescendants = true) { }) {
        Artwork(item.backdropUrl ?: item.posterUrl, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
            listOf(Color.Transparent, Ink.copy(alpha = .15f), Ink.copy(alpha = .92f)))))
        Column(Modifier.align(Alignment.BottomCenter).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(item.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (item.year.isNotBlank()) Text(item.year, style = MaterialTheme.typography.bodySmall, color = Cream)
        }
    }
}

@Composable
internal fun ScreenHeader(title: String, onBack: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (onBack != null) RoundAction("Volver", Icons.Default.ArrowBack, onBack)
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun StatusMessage(title: String, message: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Text(message, color = Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
        if (action != null) OutlinedButton(onClick = onAction) { Text(action) }
    }
}

@Composable
internal fun LoadingRow(label: String) {
    Row(Modifier.fillMaxWidth().padding(24.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(label, modifier = Modifier.padding(start = 12.dp), color = Muted)
    }
}

@Composable
internal fun FloatingNavigation(tab: String, collapsed: Boolean, onTab: (String) -> Unit, onSearch: () -> Unit,
    modifier: Modifier = Modifier) {
    val glass = Brush.linearGradient(listOf(Panel.copy(alpha = .97f), Color(0xFF292344).copy(alpha = .97f)))
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val itemWidth = ((maxWidth - 118.dp) / 4).coerceIn(48.dp, 70.dp)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.shadow(18.dp, CircleShape).clip(CircleShape).background(glass)
                .border(1.dp, Cream.copy(alpha = .20f), CircleShape).animateContentSize().padding(5.dp),
                verticalAlignment = Alignment.CenterVertically) {
                NavigationItem("Inicio", Icons.Default.Home, tab == "home", collapsed, itemWidth) { onTab("home") }
                if (!collapsed) {
                    NavigationItem("Anime", Icons.Default.LiveTv, tab == "anime", false, itemWidth) { onTab("anime") }
                    NavigationItem("Biblioteca", Icons.Default.Favorite, tab == "library", false, itemWidth) { onTab("library") }
                    NavigationItem("Ajustes", Icons.Default.Settings, tab == "settings", false, itemWidth) { onTab("settings") }
                }
            }
            IconButton(onClick = onSearch, modifier = Modifier.padding(start = 10.dp).size(58.dp)
                .shadow(18.dp, CircleShape).clip(CircleShape).background(glass)
                .border(1.dp, Cream.copy(alpha = .20f), CircleShape)) {
                Icon(Icons.Default.Search, "Buscar", modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun NavigationItem(label: String, icon: ImageVector, selected: Boolean, compact: Boolean, width: Dp, onClick: () -> Unit) {
    Column(Modifier.width(if (compact) 48.dp else width).height(if (compact) 48.dp else 54.dp)
        .clip(CircleShape).background(if (selected) Aqua.copy(alpha = .16f) else Color.Transparent)
        .clickable(role = Role.Tab, onClick = onClick).semantics(mergeDescendants = true) { this.selected = selected },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, if (compact) label else null, Modifier.size(23.dp), tint = if (selected) Aqua else Muted)
        if (!compact) Text(label, color = if (selected) Aqua else Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
    }
}

@Composable
internal fun VideoLinkDialog(onDismiss: () -> Unit, onOpenVideo: (String, VideoFormat) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    var format by rememberSaveable { mutableStateOf(VideoFormat.AUTO) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val submit = {
        val validated = try { MediaUrlPolicy.validate(url) } catch (_: Exception) { null }
        if (validated == null) invalid = true else {
            focus.clearFocus()
            onDismiss()
            onOpenVideo(validated, format)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(20.dp).imePadding(),
        title = { Text("Reproducir por enlace") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                 Text("Introduce una URL HTTPS directa a un vídeo propio o que tengas permiso para reproducir. " +
                    "También puedes buscar fuentes desde una ficha con tus proveedores configurados.")
                OutlinedTextField(value = url, onValueChange = { url = it; invalid = false },
                    label = { Text("URL HTTPS del vídeo") }, singleLine = true, isError = invalid,
                    supportingText = { if (invalid) Text("Usa un enlace HTTPS directo y válido a un vídeo permitido.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                     keyboardActions = KeyboardActions(onGo = { submit() }), modifier = Modifier.fillMaxWidth())
                Text("Formato del enlace", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoFormat.entries.forEach { option ->
                        FilterChip(selected = format == option, onClick = { format = option }, label = { Text(option.label) })
                    }
                }
                Text("Elige HLS o DASH si el enlace no termina en .m3u8 o .mpd.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        },
        confirmButton = { TextButton(onClick = { submit() }, enabled = url.isNotBlank()) { Text("Reproducir") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

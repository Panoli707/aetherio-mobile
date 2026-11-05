package com.panoli707.aetherio.nativeapp.player

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.delay
import coil.compose.AsyncImage
import com.panoli707.aetherio.nativeapp.ui.Accent
import com.panoli707.aetherio.nativeapp.ui.Outline
import com.panoli707.aetherio.nativeapp.ui.Panel
import com.panoli707.aetherio.nativeapp.ui.GlassPanelAlpha

@OptIn(UnstableApi::class)
@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    state: VideoUiState,
    canChangeSource: Boolean,
    onAction: (VideoAction) -> Unit,
    onSeek: (Long) -> Unit,
    onSpeed: (Float) -> Unit,
    onTrack: (String) -> Unit,
    onTorrentFile: (Int) -> Unit = {},
    onVolume: (Float) -> Unit = {},
    video: @Composable (Int) -> Unit
) {
    var resizeMode by rememberSaveable { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var locked by rememberSaveable { mutableStateOf(false) }
    var visible by rememberSaveable { mutableStateOf(true) }
    var menu by remember { mutableStateOf<String?>(null) }
    var interaction by remember { mutableLongStateOf(0L) }
    var scrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }
    val currentState by rememberUpdatedState(state)
    val currentAction by rememberUpdatedState(onAction)
    val act: (VideoAction) -> Unit = { interaction++; onAction(it) }
    val duration = state.durationMs.coerceAtLeast(0L)
    val position = state.positionMs.coerceAtLeast(0L).let { if (duration > 0L) it.coerceAtMost(duration) else it }
    val canSeek = state.seekable && duration > 0L && state.error == null
    val showControls = !locked && (visible || !state.playing || state.buffering ||
        scrubbing || menu != null || state.error != null)
    val aspectLabel = when (resizeMode) {
        AspectRatioFrameLayout.RESIZE_MODE_FILL -> "Fill"
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> "Zoom"
        else -> "Fit"
    }
    BackHandler {
        if (menu != null) { menu = null; interaction++ }
        else currentAction(VideoAction.CLOSE)
    }
    LaunchedEffect(canSeek, state.error) {
        if (!canSeek) scrubbing = false
        if (state.error != null) menu = null
    }
    LaunchedEffect(state.playing, state.buffering, state.error, locked, scrubbing, menu, interaction, visible) {
        if (!state.playing || state.buffering || state.error != null) visible = true
        if (state.playing && !state.buffering && !scrubbing && menu == null &&
            state.error == null && !locked && visible) {
            delay(3_500)
            visible = false
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
        // Observe without consuming, so controls and the slider retain their gestures.
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.any { it.pressed }) interaction++
            }
        }
    }) {
        Box(Modifier.fillMaxSize()) { video(resizeMode) }
        if (state.torrentFiles.isNotEmpty()) {
            AlertDialog(onDismissRequest = { act(VideoAction.CLOSE) },
                title = { Text("Selecciona el archivo de vídeo") },
                text = {
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.heightIn(max = 360.dp)) {
                        items(state.torrentFiles.size) { index ->
                            val file = state.torrentFiles[index]
                            TextButton(onClick = { onTorrentFile(file.index) }) {
                                Text("${file.label}${if (file.pathTruncated) "…" else ""} · ${file.size / (1024 * 1024)} MiB")
                            }
                        }
                    }
                }, confirmButton = {}, dismissButton = {
                    TextButton(onClick = { act(if (canChangeSource) VideoAction.FIND_SOURCES else VideoAction.CLOSE) }) { Text("Cancelar") }
                })
        }
        Box(Modifier.fillMaxSize().testTag("playerSurface").semantics {
            if (!locked) {
                contentDescription = "Mostrar u ocultar controles"
                onClick { visible = !visible; interaction++; true }
            }
        }.pointerInput(locked) {
            detectTapGestures(
                onTap = { if (!locked) visible = !visible },
                onDoubleTap = { point ->
                    if (!locked && currentState.seekable && currentState.error == null) {
                        currentAction(if (point.x < size.width / 2f) VideoAction.REWIND else VideoAction.FORWARD)
                    }
                }
            )
        })
        val chrome = showControls && menu != "info"
        if (chrome) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(200.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .5f)))))
        }
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val wide = maxWidth >= 600.dp
            val compact = maxHeight < 360.dp
            val errorMaxHeight = (maxHeight - 80.dp).coerceAtLeast(48.dp)
            val gutter = if (wide) 24.dp else 12.dp
            val centerSize = if (compact) 72.dp else 80.dp
            val bottomSpace = if (wide) 124.dp else 216.dp
            Box(Modifier.fillMaxSize().padding(horizontal = gutter, vertical = 8.dp).testTag("playerSafeArea")) {
                if (chrome) {
                    Row(Modifier.align(Alignment.TopStart), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PlayerButton(Icons.Rounded.Close, "Volver", glass = true) { act(VideoAction.CLOSE) }
                        if (canChangeSource) PlayerButton(Icons.Rounded.SwapHoriz, "Cambiar fuente", glass = true) {
                            act(VideoAction.FIND_SOURCES)
                        }
                    }
                    Row(Modifier.align(Alignment.TopEnd).width(if (wide) 220.dp else 172.dp)
                        .glass(videoOverlay = true).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (state.volume == 0f) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                        PlayerSlider(state.volume.coerceIn(0f, 1f), { interaction++; onVolume(it) }, {}, true,
                            "Volumen", Modifier.weight(1f).padding(start = 8.dp))
                    }
                }
                if (locked) PlayerButton(Icons.Rounded.LockOpen, "Desbloquear controles",
                    modifier = Modifier.align(Alignment.TopEnd), glass = true) {
                    locked = false; visible = true; interaction++
                }
                if (chrome && state.error != null) {
                    Column(Modifier.align(Alignment.Center).widthIn(max = 520.dp).heightIn(max = errorMaxHeight)
                        .glass(RoundedCornerShape(20.dp)).verticalScroll(rememberScrollState()).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error, color = Color.White)
                        TextButton(onClick = { act(VideoAction.RETRY) }) { Text("Reintentar", color = Color.White) }
                        if (canChangeSource) TextButton(onClick = { act(VideoAction.FIND_SOURCES) }) {
                            Text("Cambiar fuente", color = Color.White)
                        }
                    }
                } else if (chrome) {
                    // Independent of the footer: transport stays at the actual video centre.
                    Row(Modifier.align(Alignment.Center).testTag("playerTransport"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (wide) 28.dp else 20.dp)) {
                        PlayerButton(Icons.Rounded.Replay10, "Retroceder 10 segundos", enabled = canSeek,
                            size = 56.dp, iconSize = 30.dp, glass = true) { act(VideoAction.REWIND) }
                        if (state.buffering) Box(Modifier.size(centerSize).glass(CircleShape, videoOverlay = true), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(32.dp).semantics { contentDescription = "Cargando video" },
                                color = Accent, strokeWidth = 3.dp)
                        } else PlayerButton(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (state.playing) "Pausar" else "Reproducir", size = centerSize, iconSize = 40.dp,
                            glass = true) { act(VideoAction.TOGGLE_PLAY) }
                        PlayerButton(Icons.Rounded.Forward10, "Avanzar 10 segundos", enabled = canSeek,
                            size = 56.dp, iconSize = 30.dp, glass = true) { act(VideoAction.FORWARD) }
                    }
                    // In landscape, put Info beside the timeline instead of reserving a whole extra row.
                    // This lowers title/tools/progress by 36dp without moving transport or clipping controls.
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .padding(bottom = if (wide && !compact) 12.dp else 0.dp).testTag("playerFooter")) {
                        val tools: @Composable () -> Unit = {
                            Row(Modifier.glass(videoOverlay = true).padding(horizontal = 4.dp).testTag("playerTools"),
                                verticalAlignment = Alignment.CenterVertically) {
                                listOf("speed", "text", "audio").forEach { kind ->
                                    Box {
                                        val icon = when (kind) {
                                            "text" -> Icons.Rounded.Subtitles
                                            "audio" -> Icons.Rounded.GraphicEq
                                            else -> Icons.Rounded.Speed
                                        }
                                        val label = when (kind) { "text" -> "Subtítulos"; "audio" -> "Audio"; else -> "Velocidad" }
                                        PlayerButton(icon, label) { menu = kind; interaction++ }
                                        DropdownMenu(expanded = menu == kind, onDismissRequest = { menu = null; interaction++ },
                                            containerColor = Panel) {
                                            if (kind == "speed") {
                                                listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                                                    DropdownMenuItem(text = {
                                                        Text("${speed}x", color = if (state.speed == speed) Accent else Color.White,
                                                            fontWeight = if (state.speed == speed) FontWeight.Bold else FontWeight.Normal)
                                                    },
                                                        modifier = Modifier.semantics { selected = state.speed == speed },
                                                        onClick = { onSpeed(speed); menu = null; interaction++ })
                                                }
                                            } else {
                                                val tracks = if (kind == "text") state.subtitleTracks else state.audioTracks
                                                if (kind == "text" && tracks.none { it.id == "text:none" }) {
                                                    DropdownMenuItem(text = {
                                                        Text("Desactivar subtítulos", color = if (tracks.none { it.selected }) Accent else Color.White)
                                                    },
                                                        modifier = Modifier.semantics { selected = tracks.none { it.selected } },
                                                        onClick = { onTrack("text:none"); menu = null; interaction++ })
                                                }
                                                tracks.forEach { track ->
                                                    DropdownMenuItem(text = {
                                                        Text(track.label, color = if (track.selected) Accent else Color.White,
                                                            fontWeight = if (track.selected) FontWeight.Bold else FontWeight.Normal)
                                                    },
                                                        modifier = Modifier.semantics { selected = track.selected },
                                                        onClick = { onTrack(track.id); menu = null; interaction++ })
                                                }
                                                if (tracks.isEmpty() && kind == "audio") DropdownMenuItem(
                                                    text = { Text("Sin pistas de audio", color = Color.LightGray) }, onClick = {}, enabled = false)
                                            }
                                        }
                                    }
                                }
                                PlayerButton(Icons.Rounded.AspectRatio, "Ajuste de imagen: $aspectLabel") {
                                    interaction++
                                    resizeMode = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                                        AspectRatioFrameLayout.RESIZE_MODE_FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    }
                                }
                                PlayerButton(Icons.Rounded.Lock, "Bloquear controles") {
                                    locked = true; visible = true; menu = null; interaction++
                                }
                                PlayerButton(Icons.Rounded.ScreenRotation, "Girar pantalla") { act(VideoAction.ROTATE) }
                            }
                        }
                        if (wide) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            PlayerMetadata(state, Modifier.weight(1f), compact)
                            tools()
                        } else {
                            PlayerMetadata(state, Modifier.fillMaxWidth(), compact)
                            Spacer(Modifier.height(8.dp))
                            Box(Modifier.align(Alignment.End)) { tools() }
                        }
                        val displayedPosition = if (scrubbing) (scrubFraction.toDouble() * duration).toLong() else position
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(playerTime(displayedPosition), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            PlayerSlider(if (scrubbing) scrubFraction else if (duration > 0) position.toFloat() / duration else 0f,
                                { scrubbing = true; scrubFraction = it; interaction++ }, {
                                    if (canSeek) onSeek((scrubFraction.toDouble() * duration).toLong().coerceIn(0L, duration))
                                    scrubbing = false; interaction++
                                }, canSeek, "Posición de reproducción", Modifier.weight(1f))
                            Text(if (state.live) "EN DIRECTO" else playerTime(duration), color = Color.White,
                                fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            if (wide) PlayerButton(Icons.Rounded.Info, "Información") { menu = "info"; interaction++ }
                        }
                        if (!wide) TextButton(onClick = { menu = "info"; interaction++ },
                            modifier = Modifier.height(48.dp).testTag("playerInfo"), contentPadding = PaddingValues(horizontal = 0.dp)) {
                            Text("Info", color = Color.White, fontSize = 14.sp,
                                modifier = Modifier.semantics { contentDescription = "Información" })
                        }
                    }
                }
                val skip = openingSkip(state)
                val ending = endingSkip(state)
                if (!locked && state.error == null && menu != "info") {
                    Row(Modifier.align(Alignment.BottomEnd).padding(bottom = if (chrome) bottomSpace else 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (skip != null && (skip.precise || chrome)) GlassSkipButton(
                            if (skip.precise) "Saltar opening" else "Opening · +90 s") { act(VideoAction.SKIP_OPENING) }
                        if (ending != null && (ending.precise || chrome)) GlassSkipButton(
                            if (ending.precise) "Saltar ending" else "Ending · +90 s") { act(VideoAction.SKIP_ENDING) }
                    }
                }
            }
            if (menu == "info" && !locked) {
                Box(Modifier.fillMaxSize().clickable { menu = null; interaction++ })
                PlayerInfo(state, Modifier.align(Alignment.BottomCenter).padding(horizontal = gutter, vertical = 16.dp)
                    .fillMaxWidth().heightIn(max = maxHeight * .65f)) { menu = null; interaction++ }
            }
        }
    }
}

@Composable
private fun PlayerButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    iconSize: Dp = 26.dp,
    glass: Boolean = false,
    onClick: () -> Unit
) {
    val button = modifier.size(size).testTag("playerButton:$label")
    IconButton(onClick = onClick, enabled = enabled, modifier = if (glass) button.glass(CircleShape, videoOverlay = true) else button) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(iconSize),
            tint = Color.White.copy(alpha = if (enabled) 1f else .35f))
    }
}

/** Translucency only: blurring the SurfaceView would blur controls, not the video behind them. */
private fun Modifier.glass(shape: Shape = RoundedCornerShape(50), videoOverlay: Boolean = false) = clip(shape)
    .background(if (videoOverlay) Brush.linearGradient(listOf(Color.White.copy(alpha = .18f), Panel.copy(alpha = .24f)))
        else Brush.linearGradient(listOf(Panel.copy(alpha = GlassPanelAlpha), Panel.copy(alpha = GlassPanelAlpha))))
    .border(1.dp, Color.White.copy(alpha = .28f), shape)

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSlider(value: Float, onValue: (Float) -> Unit, onFinished: () -> Unit,
    enabled: Boolean, label: String, modifier: Modifier = Modifier) {
    val colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent,
        inactiveTrackColor = Outline, disabledThumbColor = Color.White.copy(alpha = .35f),
        disabledActiveTrackColor = Color.White.copy(alpha = .35f), disabledInactiveTrackColor = Color.White.copy(alpha = .12f))
    Slider(value = value, onValueChange = onValue, onValueChangeFinished = onFinished,
        enabled = enabled, modifier = modifier.height(48.dp).semantics { contentDescription = label },
        colors = colors, thumb = { Box(Modifier.size(12.dp).background(if (enabled) Accent else Color.White.copy(alpha = .35f), CircleShape)) },
        track = { slider -> SliderDefaults.Track(sliderState = slider, modifier = Modifier.height(4.dp),
            enabled = enabled, colors = colors, thumbTrackGapSize = 0.dp, drawStopIndicator = null) })
}

@Composable
private fun PlayerMetadata(state: VideoUiState, modifier: Modifier, compact: Boolean) {
    Column(modifier) {
        if (state.episodeLabel.isNotBlank()) Text(state.episodeLabel, color = Color.White.copy(alpha = .7f),
            fontSize = 11.sp, maxLines = 1)
        Text(state.title, color = Color.White, fontSize = (if (compact) 17 else 20).sp,
            fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.subtitle.isNotBlank()) Text(state.subtitle, Modifier.weight(1f, fill = false),
                color = Color.White.copy(alpha = .7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (state.videoHeight > 0) Text("${state.videoHeight}p", color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
        }
    }
}

@Composable
private fun GlassSkipButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.glass(videoOverlay = true).heightIn(min = 48.dp)) {
        Text(label, color = Color.White)
    }
}

@Composable
private fun PlayerInfo(state: VideoUiState, modifier: Modifier, onClose: () -> Unit) {
    Row(modifier.glass(RoundedCornerShape(20.dp), videoOverlay = true).clickable(onClick = {})
        .padding(16.dp).testTag("playerInfoCard"), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (!state.posterUrl.isNullOrBlank()) AsyncImage(model = state.posterUrl, contentDescription = "Portada",
            modifier = Modifier.width(76.dp).height(114.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text(state.title, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 18.sp)
            if (state.subtitle.isNotBlank()) Text(state.subtitle, color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
            if (state.overview.isNotBlank()) Text(state.overview, color = Color.White, fontSize = 14.sp,
                modifier = Modifier.padding(top = 8.dp))
            if (state.videoHeight > 0) Text("Resolución: ${state.videoHeight}p", color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
            state.torrentLabel?.let { Text(it, color = Color.White.copy(alpha = .8f), fontSize = 12.sp) }
            state.torrentMetrics?.let { metrics ->
                Text("P2P · ${metrics.peers} peers · ${metrics.downloadBytesPerSecond / 1024} KiB/s",
                    color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
            }
        }
        PlayerButton(Icons.Rounded.Close, "Cerrar", onClick = onClose)
    }
}

private fun playerTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1_000
    val minutes = seconds / 60
    val tail = (seconds % 60).toString().padStart(2, '0')
    return if (minutes >= 60) "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}:$tail" else "$minutes:$tail"
}

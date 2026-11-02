package com.panoli707.aetherio.nativeapp.player

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import kotlinx.coroutines.delay

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
    BackHandler { currentAction(VideoAction.CLOSE) }
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
        if (showControls || locked) {
            Box(Modifier.fillMaxWidth().height(160.dp).background(Brush.verticalGradient(
                listOf(Color.Black.copy(alpha = .7f), Color.Transparent)
            )))
        }
        if (showControls && state.error == null) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(260.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .8f)))))
        }
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            val compact = maxHeight < 420.dp
            val wide = maxWidth >= 600.dp
            val gutter = if (wide) 32.dp else 12.dp
            val controlsWidth = maxWidth - gutter * 2
            Column(Modifier.fillMaxSize().padding(horizontal = gutter)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically) {
                    if (showControls && canChangeSource) PlayerButton(Icons.Rounded.SwapHoriz, "Cambiar fuente") {
                        act(VideoAction.FIND_SOURCES)
                    }
                    if (showControls || locked) PlayerButton(
                        if (locked) Icons.Rounded.LockOpen else Icons.Rounded.Lock,
                        if (locked) "Desbloquear controles" else "Bloquear controles"
                    ) { locked = !locked; visible = true; menu = null; interaction++ }
                    if (showControls) PlayerButton(Icons.AutoMirrored.Rounded.ArrowBack, "Volver") {
                        act(VideoAction.CLOSE)
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (showControls && state.error != null) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(state.error, color = Color.White)
                            TextButton(onClick = { act(VideoAction.RETRY) }) { Text("Reintentar", color = Color.White) }
                            if (canChangeSource) TextButton(onClick = { act(VideoAction.FIND_SOURCES) }) {
                                Text("Cambiar fuente", color = Color.White)
                            }
                        }
                    } else if (showControls) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (wide) 48.dp else 16.dp)) {
                            PlayerButton(Icons.Rounded.Replay10, "Retroceder 10 segundos", enabled = canSeek) {
                                act(VideoAction.REWIND)
                            }
                            if (state.buffering) {
                                Box(Modifier.size(if (compact) 64.dp else 80.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(40.dp).semantics {
                                        contentDescription = "Cargando video"
                                    }, color = Color.White)
                                }
                            } else PlayerButton(
                                if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                if (state.playing) "Pausar" else "Reproducir",
                                size = if (compact) 64 else 80
                            ) { act(VideoAction.TOGGLE_PLAY) }
                            PlayerButton(Icons.Rounded.Forward10, "Avanzar 10 segundos", enabled = canSeek) {
                                act(VideoAction.FORWARD)
                            }
                        }
                    }
                    val skip = openingSkip(state)
                    if (!locked && skip != null && (skip.precise || showControls)) {
                        FilledTonalButton(onClick = { act(VideoAction.SKIP_OPENING) },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(bottom = 8.dp)) {
                            Text(if (skip.precise) "Saltar opening" else "Opening · +90 s")
                        }
                    }
                }
                if (showControls && state.error == null) {
                    Column(Modifier.padding(bottom = if (compact) 0.dp else 12.dp)) {
                        Text(state.title, color = Color.White, fontSize = (if (wide) 26 else 20).sp,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (state.subtitle.isNotBlank()) Text(state.subtitle, Modifier.weight(1f),
                                color = Color.White.copy(alpha = .7f), fontSize = 13.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (state.videoHeight > 0) Text("${state.videoHeight}p", Modifier.padding(horizontal = 8.dp)
                                .background(Color.White.copy(alpha = .15f)).padding(horizontal = 6.dp, vertical = 2.dp),
                                color = Color.White, fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Slider(
                                value = if (scrubbing) scrubFraction else if (duration > 0) position.toFloat() / duration else 0f,
                                onValueChange = { scrubbing = true; scrubFraction = it; interaction++ },
                                onValueChangeFinished = {
                                    if (canSeek) onSeek((scrubFraction.toDouble() * duration).toLong().coerceIn(0L, duration))
                                    scrubbing = false; interaction++
                                },
                                enabled = canSeek,
                                modifier = Modifier.weight(1f).semantics { contentDescription = "Posici\u00f3n de reproducci\u00f3n" },
                                thumb = { Box(Modifier.size(10.dp).background(Color.White, CircleShape)) },
                                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = .3f),
                                    disabledThumbColor = Color.White.copy(alpha = .35f),
                                    disabledActiveTrackColor = Color.White.copy(alpha = .3f),
                                    disabledInactiveTrackColor = Color.White.copy(alpha = .15f))
                            )
                            val displayedPosition = if (scrubbing) (scrubFraction.toDouble() * duration).toLong() else position
                            Text("${playerTime(displayedPosition)} / ${if (state.live) "EN DIRECTO" else playerTime(duration)}",
                                Modifier.padding(start = 8.dp), color = Color.White, fontSize = 12.sp)
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()).widthIn(min = controlsWidth),
                            horizontalArrangement = Arrangement.SpaceBetween) {
                            listOf("text", "audio", "speed").forEach { kind ->
                                Box {
                                    val icon = when (kind) {
                                        "text" -> Icons.Rounded.Subtitles
                                        "audio" -> Icons.Rounded.VolumeUp
                                        else -> Icons.Rounded.Speed
                                    }
                                    val label = when (kind) { "text" -> "Subt\u00edtulos"; "audio" -> "Audio"; else -> "Velocidad" }
                                    PlayerButton(icon, label) { menu = kind }
                                    DropdownMenu(expanded = menu == kind, onDismissRequest = { menu = null; interaction++ }) {
                                        if (kind == "speed") {
                                            listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                                                DropdownMenuItem(text = { Text("${speed}x",
                                                    fontWeight = if (state.speed == speed) FontWeight.Bold else FontWeight.Normal) },
                                                    modifier = Modifier.semantics { selected = state.speed == speed },
                                                    onClick = { onSpeed(speed); menu = null; interaction++ })
                                            }
                                        } else {
                                            val tracks = if (kind == "text") state.subtitleTracks else state.audioTracks
                                            if (kind == "text" && tracks.none { it.id == "text:none" }) {
                                                DropdownMenuItem(text = { Text("Desactivar subt\u00edtulos") },
                                                    modifier = Modifier.semantics { selected = tracks.none { it.selected } },
                                                    onClick = { onTrack("text:none"); menu = null; interaction++ })
                                            }
                                            tracks.forEach { track ->
                                                DropdownMenuItem(text = { Text(track.label,
                                                    fontWeight = if (track.selected) FontWeight.Bold else FontWeight.Normal) },
                                                    modifier = Modifier.semantics { selected = track.selected },
                                                    onClick = { onTrack(track.id); menu = null; interaction++ })
                                            }
                                            if (tracks.isEmpty() && kind == "audio") DropdownMenuItem(
                                                text = { Text("Sin pistas de audio") }, onClick = {}, enabled = false)
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
                            PlayerButton(Icons.Rounded.Info, "Informaci\u00f3n") { menu = "info" }
                            PlayerButton(Icons.Rounded.ScreenRotation, "Girar pantalla") { act(VideoAction.ROTATE) }
                        }
                    }
                }
            }
        }
        if (menu == "info" && !locked) AlertDialog(
            onDismissRequest = { menu = null; interaction++ },
            title = { Text(state.title) },
            text = {
                Column {
                    if (state.subtitle.isNotBlank()) Text(state.subtitle)
                    if (state.videoHeight > 0) Text("Resoluci\u00f3n: ${state.videoHeight}p")
                }
            },
            confirmButton = { TextButton(onClick = { menu = null; interaction++ }) { Text("Cerrar") } }
        )
    }
}

@Composable
private fun PlayerButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    size: Int = 48,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(size.dp)) {
        Icon(icon, contentDescription = label, modifier = Modifier.size((if (size > 48) 48 else 26).dp),
            tint = Color.White.copy(alpha = if (enabled) 1f else .35f))
    }
}

private fun playerTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0L) / 1_000
    val minutes = seconds / 60
    val tail = (seconds % 60).toString().padStart(2, '0')
    return if (minutes >= 60) "${minutes / 60}:${(minutes % 60).toString().padStart(2, '0')}:$tail" else "$minutes:$tail"
}

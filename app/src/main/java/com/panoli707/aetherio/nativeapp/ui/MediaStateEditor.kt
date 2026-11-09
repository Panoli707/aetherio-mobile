package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.panoli707.aetherio.nativeapp.data.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MediaStateEditor(item: MediaItem, initial: MediaListState, completedEpisodeCount: Int? = null,
    onClose: () -> Unit, onSave: (MediaListState) -> Unit) {
    var status by remember(item.key) { mutableStateOf(initial.status) }
    var progress by remember(item.key) { mutableStateOf(initial.progress.toString()) }
    var score by remember(item.key) { mutableStateOf((initial.score / 10).toString()) }
    val count = progress.toIntOrNull()
    val points = score.replace(',', '.').toDoubleOrNull()
    AlertDialog(onDismissRequest = onClose, title = { Text(item.title) }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Estado")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AnimeStatus.entries.forEach { value -> FilterChip(status == value, {
                    status = value
                    if (value == AnimeStatus.COMPLETED && item.type == MediaType.SERIES && completedEpisodeCount != null) {
                        progress = completedEpisodeCount.toString()
                    }
                }, label = { Text(value.label) }) }
            }
            OutlinedTextField(progress, { progress = it.filter(Char::isDigit).take(6) },
                label = { Text(if (item.type == MediaType.MOVIE) "Vista (0–1)" else "Episodios vistos") }, singleLine = true)
            OutlinedTextField(score, { score = it.take(5) }, label = { Text("Puntuación (0–10)") }, singleLine = true)
        }
    }, confirmButton = { TextButton(enabled = count != null && count in 0..(if (item.type == MediaType.MOVIE) 1 else 100_000) &&
        points != null && points.isFinite() && points in 0.0..10.0, onClick = {
            onSave(MediaListState(status, requireNotNull(count), requireNotNull(points) * 10)); onClose()
        }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } })
}

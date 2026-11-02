package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.panoli707.aetherio.nativeapp.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EpisodeActionsSheet(item: MediaItem, episode: EpisodeItem, episodes: List<EpisodeItem>, progress: List<WatchProgress>,
    onDismiss: () -> Unit, onMark: (EpisodeWatchScope, Boolean) -> Unit, onSources: () -> Unit, onManual: () -> Unit) {
    fun seen(ep: EpisodeItem) = progress.any { it.target.item.key == item.key && it.target.season == ep.season && it.target.episode == ep.number && it.watched }
    val previous = EpisodeWatchRules.targets(episodes, episode, EpisodeWatchScope.PREVIOUS)
    val season = EpisodeWatchRules.targets(episodes, episode, EpisodeWatchScope.SEASON)
    var confirm by remember { mutableStateOf<Pair<EpisodeWatchScope, Boolean>?>(null) }
    fun mark(scope: EpisodeWatchScope, watched: Boolean) {
        if (!watched && scope != EpisodeWatchScope.EPISODE) confirm = scope to watched
        else { onMark(scope, watched); onDismiss() }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(episode.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 22.dp))
            Text("T${episode.season} E${episode.number} · ${item.title}", color = Muted,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp))
            HorizontalDivider()
            EpisodeAction(Icons.Default.CheckCircle, if (seen(episode)) "Marcar como no visto" else "Marcar como visto") {
                mark(EpisodeWatchScope.EPISODE, !seen(episode))
            }
            if (previous.isNotEmpty()) EpisodeAction(Icons.Default.DoneAll,
                if (previous.all(::seen)) "Marcar anteriores como no vistos" else "Marcar anteriores como vistos") {
                mark(EpisodeWatchScope.PREVIOUS, !previous.all(::seen))
            }
            if (season.isNotEmpty()) EpisodeAction(Icons.Default.PlaylistAddCheck,
                if (season.all(::seen)) "Marcar temporada ${episode.season} como no vista" else "Marcar temporada ${episode.season} como vista") {
                mark(EpisodeWatchScope.SEASON, !season.all(::seen))
            }
            Text("Las acciones de temporada incluyen solo capítulos con fecha de emisión confirmada.",
                style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(horizontal = 22.dp, vertical = 4.dp))
            HorizontalDivider()
            EpisodeAction(Icons.Default.PlayArrow, "Elegir fuente") { onDismiss(); onSources() }
            EpisodeAction(Icons.Default.Link, "Reproducir manualmente") { onDismiss(); onManual() }
        }
    }
    confirm?.let { (scope, watched) ->
        val count = EpisodeWatchRules.targets(episodes, episode, scope).size
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text("Marcar $count capítulos como no vistos") },
            text = { Text("Se quitarán sus marcas y se reiniciarán sus posiciones guardadas. Los demás capítulos no cambian.") },
            confirmButton = { TextButton(onClick = { onMark(scope, watched); onDismiss() }) { Text("Confirmar") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancelar") } })
    }
}

@Composable
private fun EpisodeAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    ListItem(headlineContent = { Text(label) }, leadingContent = { Icon(icon, null) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.clickable(onClick = onClick).fillMaxWidth())
}

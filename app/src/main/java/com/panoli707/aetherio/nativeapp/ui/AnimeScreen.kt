package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.panoli707.aetherio.nativeapp.data.*

@Composable
internal fun AnimeScreen(state: AppState, scroll: LazyListState, onSelect: (MediaItem) -> Unit,
    onContinue: (WatchProgress) -> Unit, onAiring: () -> Unit) {
    val continuing = WatchProgressRules.continuing(state.watchProgress).filter { it.target.item.isAnime }
    val hero = continuing.firstOrNull()?.target?.item ?: state.watchingAnime.firstOrNull()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = (maxWidth.value / 150).toInt().coerceIn(2, 5)
        LazyColumn(state = scroll, contentPadding = PaddingValues(bottom = 120.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
            item(key = "animeHeader") {
                if (hero == null) ScreenHeader("Anime") else Box(Modifier.fillMaxWidth().height(260.dp)) {
                    Artwork(hero.backdropUrl ?: hero.posterUrl, Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink))))
                    Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                        Text(if (continuing.isNotEmpty()) "Continuar viendo" else "Estás viendo", style = MaterialTheme.typography.titleLarge)
                        Text(hero.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            item(key = "animeContinue") {
                if (hero == null || continuing.isEmpty()) Text("Continuar viendo", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(18.dp))
                if (continuing.isEmpty()) Text("Los episodios que dejes a medias aparecerán aquí con su posición guardada.", color = Muted, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                else LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(continuing, key = { it.target.key }) { progress ->
                        Column(Modifier.width(280.dp).clickable(onClickLabel = "Continuar ${progress.target.item.title}") { onContinue(progress) }) {
                            Artwork(progress.target.imageUrl ?: progress.target.item.backdropUrl ?: progress.target.item.posterUrl,
                                Modifier.fillMaxWidth().height(158.dp).clip(RoundedCornerShape(14.dp)))
                            LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth(), color = Cream)
                            Text(progress.target.episodeTitle.ifBlank { progress.target.item.title }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                            Text("T${progress.target.season} · E${progress.target.episode} · ${progress.target.item.title}", color = Muted,
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${watchTime(progress.positionMs)} / ${watchTime(progress.durationMs)}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            item(key = "watchingHeading") {
                Text("Estás viendo", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 18.dp).padding(top = 28.dp, bottom = 12.dp))
                Text("Tus animes en seguimiento, también entre un capítulo y el siguiente.", color = Muted, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
            }
            if (state.watchingAnime.isEmpty()) item(key = "watchingEmpty") {
                StatusMessage("Todavía no sigues ningún anime", "Reproduce un episodio o usa Añadir a Estás viendo desde su ficha.", "Ver temporada actual", onAiring)
            }
            itemsIndexed(state.watchingAnime.chunked(columns), key = { _, row -> "watching:${row.first().key}" }) { _, row ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { item ->
                        val watched = state.watchProgress.filter { it.target.item.key == item.key && it.watched && (it.target.season ?: 0) > 0 }
                            .distinctBy { it.target.key }.size
                        val progress = WatchProgressRules.latest(state.watchProgress, item)?.takeIf { !it.watched && it.positionMs > 0 }
                        Column(Modifier.weight(1f)) {
                            Box(Modifier.fillMaxWidth().aspectRatio(.67f).clip(RoundedCornerShape(14.dp)).clickable { onSelect(item) }) {
                                Artwork(item.posterUrl, Modifier.fillMaxSize())
                                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, Ink))))
                                Text(if (item.episodeCount != null) "$watched/${item.episodeCount} vistos" else "$watched vistos",
                                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp), style = MaterialTheme.typography.labelMedium)
                            }
                            Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                            Text(item.animeSeasonLabel.ifBlank { item.year }, color = Muted, style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = { if (progress != null) onContinue(progress) else onSelect(item) }) { Text(if (progress != null) "Continuar" else "Ver capítulos") }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            item(key = "animeCalendar") { OutlinedButton(onClick = onAiring, modifier = Modifier.fillMaxWidth().padding(18.dp)) { Text("Anime emitiéndose ahora · Ver calendario") } }
        }
    }
}

@Composable
internal fun SeasonalAnimeScreen(state: AnimeSeasonState, scroll: LazyListState, onBack: () -> Unit,
    onSelect: (MediaItem) -> Unit, onRetry: () -> Unit) {
    var day by rememberSaveable { mutableStateOf("all") }
    val entries = state.entries.filter { day == "all" || (day == "unknown" && it.weekday == null) || it.weekday?.name == day }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Anime emitiéndose ahora", onBack)
        Text(state.season.label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 18.dp))
        Text("AniList · emisión original en Japón (JST). No indica disponibilidad de subtítulos o doblaje.",
            style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf("all" to "Todos") + AnimeWeekday.entries.map { it.name to it.label } + listOf("unknown" to "Sin horario")).forEach { (key, label) ->
                FilterChip(selected = day == key, onClick = { day = key }, label = { Text(label) })
            }
        }
        if (state.error != null) StatusMessage("No se pudo actualizar la temporada", state.error, "Reintentar", onRetry)
        if (state.loading && state.entries.isEmpty()) LoadingRow("Consultando la temporada actual…")
        else if (entries.isEmpty() && state.loaded) StatusMessage("Sin emisiones para este filtro", "No hay animes en emisión de ${state.season.label} con ese horario confirmado.")
        else BoxWithConstraints(Modifier.weight(1f)) {
            val columns = (maxWidth.value / 150).toInt().coerceIn(2, 5)
            LazyColumn(state = scroll, contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(entries.chunked(columns), key = { row -> row.first().item.key }) { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        row.forEach { entry -> Column(Modifier.weight(1f)) {
                            Poster(entry.item, { onSelect(entry.item) })
                            Text(entry.weekday?.label ?: "Sin horario confirmado", color = Cream, style = MaterialTheme.typography.bodySmall)
                            entry.nextEpisode?.let { Text("Próximo: episodio $it", color = Muted, style = MaterialTheme.typography.bodySmall) }
                        } }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

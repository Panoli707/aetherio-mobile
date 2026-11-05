package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.panoli707.aetherio.nativeapp.data.*

@Composable
internal fun AiringAnimeSection(state: AnimeTodayState, onSelect: (MediaItem) -> Unit, onCalendar: () -> Unit, onRetry: () -> Unit,
    nowMs: Long = System.currentTimeMillis()) {
    val entries = remember(state.entries, nowMs) { AnimeToday.released(state.entries, nowMs) }
    Column(Modifier.padding(vertical = 18.dp).testTag("airingAnimeSection")) {
        Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Ver temporada y días de emisión", onClick = onCalendar)
            .semantics(mergeDescendants = true) { }.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Anime en emisión", style = MaterialTheme.typography.titleLarge)
                Text("Episodios emitidos hoy · Japón (JST)", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Accent)
        }
        if (state.loading && entries.isEmpty()) LoadingRow("Consultando emisiones…")
        if (state.error != null) StatusMessage("No se pudieron actualizar las emisiones", state.error, "Reintentar", onRetry)
        if (state.loaded && !state.loading && state.error == null && entries.isEmpty())
            Text("Todavía no hay episodios emitidos hoy confirmados. Consulta el calendario para ver los próximos.", color = Muted, modifier = Modifier.padding(horizontal = 18.dp))
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val coverWidth = posterWidth(maxWidth)
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.testTag("airingAnimeCards")) {
                items(entries, key = { it.key }, contentType = { "airing-poster" }) { entry -> AiringAnimeCard(
                    SeasonalAnime(entry.item, null, null, null, entry.episode, entry.airedAt), { onSelect(entry.item) },
                    Modifier.width(coverWidth).testTag("airingPoster:${entry.item.key}"),
                    airedLabel = java.text.SimpleDateFormat("HH:mm 'JST'", java.util.Locale.US).apply { timeZone = AnimeSeason.japan }
                        .format(java.util.Date(entry.airedAt * 1000))) }
            }
        }
    }
}

@Composable
internal fun AiringAnimeCard(entry: SeasonalAnime, onClick: () -> Unit, modifier: Modifier = Modifier, airedLabel: String? = null) {
    Column(modifier.clickable(onClickLabel = "Ver ${entry.item.title}", onClick = onClick).semantics(mergeDescendants = true) { }) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(14.dp)).testTag("airingArt:${entry.item.key}")) {
            Artwork(entry.item.posterUrl, Modifier.fillMaxSize())
            Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(65.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .9f)))))
            val episode = entry.latestEpisode
            val label = if (episode != null) "Ep. $episode / ${entry.item.episodeCount ?: "—"}" else "En emisión"
            Text(label, color = Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).background(Ink.copy(alpha = .9f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 5.dp))
        }
        Text(entry.item.title, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp))
        Text(airedLabel?.let { "Emitido · $it" } ?: entry.item.animeSeasonLabel.ifBlank { entry.item.year }, color = Muted, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 3.dp))
    }
}

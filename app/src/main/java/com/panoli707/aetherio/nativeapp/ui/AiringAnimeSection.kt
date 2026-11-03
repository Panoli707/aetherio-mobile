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
internal fun AiringAnimeSection(state: AnimeSeasonState, onSelect: (MediaItem) -> Unit, onCalendar: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.padding(vertical = 18.dp).testTag("airingAnimeSection")) {
        Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Ver temporada y días de emisión", onClick = onCalendar)
            .semantics(mergeDescendants = true) { }.padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Anime emitiéndose ahora", style = MaterialTheme.typography.titleLarge)
                Text(state.season.label + " · Temporada y días de emisión", color = Violet, style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Aqua)
        }
        if (state.loading && state.entries.isEmpty()) LoadingRow("Consultando emisiones…")
        if (state.error != null) StatusMessage("No se pudieron actualizar las emisiones", state.error, "Reintentar", onRetry)
        if (state.loaded && !state.loading && state.error == null && state.entries.isEmpty())
            Text("No hay animes en emisión confirmados para esta temporada.", color = Muted, modifier = Modifier.padding(horizontal = 18.dp))
        val entries = state.entries.sortedByDescending { it.latestAiringAt ?: Long.MIN_VALUE }.distinctBy { SeriesIdentity.item(it.item).key }
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.testTag("airingAnimeCards")) {
            items(entries, key = { it.item.key }) { entry -> AiringAnimeCard(entry, { onSelect(entry.item) }, Modifier.width(148.dp)) }
        }
    }
}

@Composable
internal fun AiringAnimeCard(entry: SeasonalAnime, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.clickable(onClickLabel = "Ver ${entry.item.title}", onClick = onClick).semantics(mergeDescendants = true) { }) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(18.dp))) {
            Artwork(entry.item.posterUrl, Modifier.fillMaxSize())
            Box(Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(65.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = .9f)))))
            val episode = entry.latestEpisode
            val label = if (episode != null) "Ep. $episode / ${entry.item.episodeCount ?: "—"}" else "En emisión"
            Text(label, color = Aqua, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).background(Ink.copy(alpha = .9f), RoundedCornerShape(9.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp))
        }
        Text(entry.item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp))
        Text(entry.item.animeSeasonLabel.ifBlank { entry.item.year }, color = Muted, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 3.dp))
    }
}

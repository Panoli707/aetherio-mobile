package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.panoli707.aetherio.nativeapp.data.*

@Composable
internal fun CatalogNavigationRow(title: String, tag: String, entries: List<Pair<String, String>>, onOpen: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp).testTag(tag)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
        LazyRow(modifier = Modifier.testTag("$tag:cards"), contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(entries, key = { it.first }) { (id, name) ->
                Box(Modifier.width(150.dp).height(84.dp).clip(RoundedCornerShape(16.dp)).background(Panel)
                    .border(1.dp, Outline, RoundedCornerShape(16.dp)).testTag("catalogLink:$id")
                    .clickable(onClickLabel = "Explorar $name") { onOpen(id) }.padding(14.dp), contentAlignment = Alignment.CenterStart) {
                    Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun HomeCatalogSection(section: CatalogSection, onSelect: (MediaItem) -> Unit, onCategory: (String) -> Unit,
    onRetry: () -> Unit, airing: AnimeSeasonState? = null) {
    Column(Modifier.fillMaxWidth().padding(top = 20.dp).testTag("homeSection:${section.id}")) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(section.title, style = MaterialTheme.typography.titleLarge)
            }
            if (section.items.isNotEmpty()) RoundAction("Ver ${section.title}", Icons.AutoMirrored.Filled.ArrowForward,
                { onCategory(if (section.id == "mal.airing_anime") "airing" else section.id) })
        }
        if (section.loading && section.items.isEmpty()) LoadingRow("Cargando ${section.title.lowercase()}…")
        if (section.error != null) StatusMessage("${section.title}: sin actualizar", section.error, "Reintentar", onRetry)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val bannerWidth = (maxWidth - 36.dp).coerceAtMost(520.dp)
            val coverWidth = posterWidth(maxWidth)
            LazyRow(modifier = Modifier.testTag("catalogItems:${section.id}"), contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(section.items, key = { _, item -> item.key }, contentType = { _, _ -> section.style }) { index, item ->
                    val width = Modifier.width(coverWidth).testTag("homePoster:${section.id}:${item.key}")
                    val episode = if (section.id == "mal.airing_anime") airing?.entries?.firstOrNull { SeriesIdentity.item(it.item).key == SeriesIdentity.item(item).key } else null
                    when {
                        section.style == SectionStyle.FEATURED -> Featured(item, { onSelect(item) }, Modifier.width(bannerWidth))
                        episode != null -> AiringAnimeCard(episode.copy(item = item), { onSelect(item) }, width)
                        else -> Poster(item, { onSelect(item) }, width, rank = if (section.style == SectionStyle.RANKED) index + 1 else null)
                    }
                }
            }
        }
    }
}

@Composable
internal fun CatalogBrowseScreen(state: CatalogBrowseState, grid: LazyGridState, scroll: LazyListState,
    onBack: () -> Unit, onSelect: (MediaItem) -> Unit, onCategory: (String) -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().testTag("catalogBrowse")) {
        ScreenHeader(state.title.ifBlank { "Catálogo" }, onBack)
        if (state.id?.startsWith("platform:") == true) {
            Text("Catálogo TMDB · disponibilidad regional. Las fuentes de reproducción se resuelven aparte.", color = Muted,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
            LazyColumn(state = scroll, contentPadding = PaddingValues(bottom = 32.dp)) {
                if (state.loading) item("loading") { LoadingRow("Cargando catálogo…") }
                if (state.error != null) item("error") { StatusMessage("No se pudo actualizar", state.error, "Reintentar", onRetry) }
                items(state.sections, key = { it.id }) { section -> HomeCatalogSection(section, onSelect, onCategory, onRetry) }
            }
        } else {
            if (state.loading) LoadingRow("Cargando catálogo…")
            if (state.error != null) StatusMessage("No se pudo actualizar", state.error, "Reintentar", onRetry)
            val items = state.sections.flatMap { it.items }.distinctBy { it.key }
            if (!state.loading && state.error == null && items.isEmpty()) StatusMessage("No hay títulos disponibles", "Prueba otra categoría.", "Reintentar", onRetry)
            CatalogGrid(items, grid, onSelect, Modifier.weight(1f))
        }
    }
}

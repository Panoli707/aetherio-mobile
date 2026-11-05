package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.panoli707.aetherio.nativeapp.data.*
import java.util.Locale

@Composable
internal fun HomeScreen(state: AppState, scroll: LazyListState, pager: PagerState, active: Boolean,
    onSelect: (MediaItem) -> Unit, onCategory: (String) -> Unit, onRetry: () -> Unit,
    onContinue: (WatchProgress) -> Unit = {}, onRetryAnime: () -> Unit = {}, onProfile: () -> Unit = {}) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val heroes = state.homePreferences.visibleHeroes(state.heroes)
    var touching by remember { mutableStateOf(false) }
    LaunchedEffect(lifecycle, active, pager, scroll, pager.pageCount) {
        if (active && pager.pageCount > 1) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            runHomeAutoAdvance(
                snapshotFlow { !touching && !scroll.isScrollInProgress && scroll.firstVisibleItemIndex == 0 },
                pagerBusy = { pager.isScrollInProgress },
                advance = { if (pager.pageCount > 1) pager.animateScrollToPage((pager.currentPage + 1) % pager.pageCount) })
        }
    }
    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().testTag("homeScroll").pointerInput(Unit) {
        awaitEachGesture {
            try {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                touching = true
                // Observe only: child rows and the vertical list retain ownership of the gesture.
                do { val event = awaitPointerEvent(PointerEventPass.Initial) } while (event.changes.any { it.pressed })
            } finally { touching = false }
        }
    },
        contentPadding = PaddingValues(bottom = 110.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
        if (heroes.isNotEmpty()) item(key = "hero") { HeroCarousel(heroes, pager, onSelect, onProfile, onRetry, state.homeLoading) }
        else item(key = "heading") {
            Box(Modifier.fillMaxWidth()) {
                ScreenHeader("Aetherio")
                ProfileAction(onProfile, Modifier.align(Alignment.TopEnd))
                RoundAction("Actualizar catálogo", Icons.Default.Refresh, onRetry,
                    Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 12.dp, end = 76.dp), enabled = !state.homeLoading)
            }
        }
        val continuing = WatchProgressRules.continuing(state.watchProgress)
        if (continuing.isNotEmpty()) item(key = "continueWatching") {
            Column(Modifier.padding(top = 20.dp)) {
                Text("Continuar viendo", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(continuing, key = { _, progress -> progress.target.key }) { _, progress ->
                        val target = progress.target
                        Column(Modifier.width(260.dp).clip(RoundedCornerShape(14.dp)).background(Panel)) {
                            Column(Modifier.clickable(onClickLabel = "Continuar ${target.item.title}") { onContinue(progress) }) {
                                Artwork(target.imageUrl ?: target.item.backdropUrl ?: target.item.posterUrl,
                                    Modifier.fillMaxWidth().height(140.dp), contentScale =
                                    if (target.imageUrl == null && target.item.backdropUrl == null) ContentScale.Fit else ContentScale.Crop)
                                LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth(), color = Accent, trackColor = Outline)
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(target.item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                    if (target.episode != null) Text("T${target.season} · E${target.episode} ${target.episodeTitle}",
                                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted, style = MaterialTheme.typography.bodySmall)
                                    Text("${watchTime(progress.positionMs)} / ${watchTime(progress.durationMs)}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            TextButton(onClick = { onSelect(target.item) }, modifier = Modifier.padding(horizontal = 4.dp)) { Text("Ver ficha") }
                        }
                    }
                }
            }
        }
        item(key = "streaming-platforms") {
            CatalogNavigationRow("Explora por plataforma", "streamingPlatforms",
                DesktopHomeCatalog.platforms.map { "platform:${it.id}" to it.name }, onCategory)
        }
        if (state.homeError != null) item(key = "error") {
            StatusMessage(if (state.sections.isEmpty()) "No se pudo cargar el catálogo" else "Faltan algunas secciones",
                state.homeError, "Reintentar", onRetry)
        } else if (state.homeLoading) item(key = "loading") { LoadingRow("Cargando catálogo…") }
        if (!state.homeLoading && state.homeError == null && state.sections.all { it.items.isEmpty() } && state.heroes.isEmpty()) {
            item(key = "empty") { StatusMessage("El catálogo está vacío", "No hay títulos disponibles en este momento.", "Reintentar", onRetry) }
        }
        if (state.sections.none { it.id == "mal.airing_anime" } && "mal.airing_anime" !in state.homePreferences.hiddenCatalogIds) item(key = "animeSeasonLink") {
            AiringAnimeSection(state.animeToday, onSelect, { onCategory("airing") }, onRetryAnime)
        }
        val displayed = state.homePreferences.ordered(state.sections)
        for (section in displayed.filter { it.id == "mal.airing_anime" || it.items.isNotEmpty() || it.loading || it.error != null }) {
            item(key = "section:${section.id}") {
                if (section.id == "mal.airing_anime") AiringAnimeSection(state.animeToday, onSelect, { onCategory("airing") }, onRetryAnime)
                else HomeCatalogSection(section, onSelect, onCategory, onRetry, state.animeSeason, state.homePreferences.horizontalPosters)
            }
            if (section.id == "tmdb.top_movie") item(key = "movie-series-genres") {
                CatalogNavigationRow("Explorar géneros - Series y películas", "movieSeriesGenres",
                    DesktopHomeCatalog.genres.map { "genre:${it.id}" to it.title }, onCategory)
            }
            if (section.id == "jikan.recommendations") item(key = "anime-genres") {
                CatalogNavigationRow("Explorar géneros - Anime", "animeGenres",
                    DesktopHomeCatalog.animeGenres.map { "anime-genre:${it.id}" to it.title }, onCategory)
            }
        }
        if (displayed.none { it.id == "tmdb.top_movie" && (it.items.isNotEmpty() || it.loading || it.error != null) }) item(key = "movie-series-genres") {
            CatalogNavigationRow("Explorar géneros - Series y películas", "movieSeriesGenres",
                DesktopHomeCatalog.genres.map { "genre:${it.id}" to it.title }, onCategory)
        }
        if (displayed.none { it.id == "jikan.recommendations" && (it.items.isNotEmpty() || it.loading || it.error != null) }) item(key = "anime-genres") {
            CatalogNavigationRow("Explorar géneros - Anime", "animeGenres",
                DesktopHomeCatalog.animeGenres.map { "anime-genre:${it.id}" to it.title }, onCategory)
        }
        if (state.sections.isNotEmpty() && displayed.isEmpty() && !state.homeLoading) item(key = "hiddenCatalogs") {
            StatusMessage("Catálogos ocultos", "Puedes volver a mostrarlos en Ajustes > Diseño. La búsqueda y la biblioteca siguen disponibles.")
        }
    }
}

@Composable
private fun HeroCarousel(heroes: List<MediaItem>, pager: PagerState, onSelect: (MediaItem) -> Unit,
    onProfile: () -> Unit, onRefresh: () -> Unit, loading: Boolean) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    Column {
        Box {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val portrait = maxWidth < 600.dp
                val height = homeHeroHeight(maxWidth, screenHeight)
                HorizontalPager(state = pager, key = { heroes.getOrNull(it)?.key ?: "pending:$it" }, verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth().testTag("homeHero")) { page ->
                    // A progressive catalog refresh can shrink this list while the pager still composes
                    // an old page. Never index the new snapshot with the previous page count.
                    val item = heroes.getOrNull(page)
                    if (item != null) Column(Modifier.fillMaxWidth().clickable(onClickLabel = "Ver ${item.title}", onClick = { onSelect(item) }),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        HeroArtwork(item, portrait, screenHeight, maxArtworkHeight = height)
                        Column(Modifier.widthIn(max = 600.dp).fillMaxWidth()
                            .padding(horizontal = 24.dp).padding(bottom = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HeroMetadata(item)
                            if (item.overview.isNotBlank()) Text(item.overview, style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = Cream.copy(alpha = .9f))
                        }
                    }
                }
            }
             ProfileAction(onProfile, Modifier.align(Alignment.TopEnd))
             RoundAction("Actualizar catálogo", Icons.Default.Refresh, onRefresh,
                 Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 12.dp, end = 76.dp), enabled = !loading)
        }
        if (heroes.size > 1) Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp)
            .semantics { contentDescription = "Destacado ${pager.currentPage.coerceIn(0, heroes.lastIndex) + 1} de ${heroes.size}" },
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            // A bounded indicator stays readable even when the API returns many heroes.
            val start = (pager.currentPage - 3).coerceIn(0, (heroes.size - 7).coerceAtLeast(0))
            for (index in start until minOf(start + 7, heroes.size)) {
                Box(Modifier.padding(horizontal = 4.dp).size(width = if (index == pager.currentPage) 22.dp else 5.dp, height = 5.dp)
                    .background(if (index == pager.currentPage) Accent else Outline, CircleShape))
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun HeroMetadata(item: MediaItem) {
    val details = listOf(item.year, item.genres.firstOrNull().orEmpty(), item.certification.orEmpty())
        .filter(String::isNotBlank)
    FlowRow(Modifier.fillMaxWidth().testTag("homeHeroMetadata"),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (item.score.isFinite() && item.score > 0) {
            Text(String.format(Locale.forLanguageTag("es"), "%.1f", item.score), color = Rating,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { contentDescription = "Valoración ${item.score} sobre 10" })
        }
        item.voteCount?.takeIf { it > 0 }?.let { votes ->
            Text(votes.toString(), color = Muted.copy(alpha = .75f), style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { contentDescription = "$votes votos" })
        }
        details.forEachIndexed { index, detail ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) Text("·", color = Muted, style = MaterialTheme.typography.bodySmall)
                Text(detail, color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ProfileAction(onClick: () -> Unit, modifier: Modifier = Modifier) {
    RoundAction("Abrir perfil", Icons.Default.Person, onClick,
        modifier.statusBarsPadding().padding(top = 12.dp, end = 18.dp).testTag("homeProfile"))
}

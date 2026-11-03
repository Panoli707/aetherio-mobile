package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil.compose.AsyncImage
import com.panoli707.aetherio.nativeapp.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun HomeScreen(state: AppState, scroll: LazyListState, pager: PagerState, active: Boolean,
    onSelect: (MediaItem) -> Unit, onCategory: (String) -> Unit, onRetry: () -> Unit,
    onContinue: (WatchProgress) -> Unit = {}, onRetryAnime: () -> Unit = {}, onProfile: () -> Unit = {}) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, active, pager.pageCount) {
        if (active && pager.pageCount > 1) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(8_000)
                if (!pager.isScrollInProgress && pager.pageCount > 1) {
                    launch { pager.animateScrollToPage((pager.currentPage + 1) % pager.pageCount) }
                }
            }
        }
    }
    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().testTag("homeScroll"),
        contentPadding = PaddingValues(bottom = 110.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
        if (state.heroes.isNotEmpty()) item(key = "hero") { HeroCarousel(state.heroes, pager, onSelect, onProfile) }
        else item(key = "heading") {
            Box(Modifier.fillMaxWidth()) {
                ScreenHeader("Aetherio")
                ProfileAction(onProfile, Modifier.align(Alignment.TopEnd))
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
                                    Modifier.fillMaxWidth().height(140.dp))
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
        if (state.homeError != null) item(key = "error") {
            StatusMessage(if (state.sections.isEmpty()) "No se pudo cargar el catálogo" else "Faltan algunas secciones",
                state.homeError, "Reintentar", onRetry)
        } else if (state.homeLoading) item(key = "loading") { LoadingRow("Cargando catálogo…") }
        if (!state.homeLoading && state.homeError == null && state.sections.all { it.items.isEmpty() } && state.heroes.isEmpty()) {
            item(key = "empty") { StatusMessage("El catálogo está vacío", "No hay títulos disponibles en este momento.", "Reintentar", onRetry) }
        }
        item(key = "animeSeasonLink") {
            AiringAnimeSection(state.animeSeason, onSelect, { onCategory("airing") }, onRetryAnime)
        }
        for (section in state.sections.filter { it.items.isNotEmpty() }) item(key = "section:${section.id}") {
            Column(Modifier.padding(top = 20.dp)) {
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(section.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    RoundAction("Ver ${section.title}", Icons.Default.ArrowForward, { onCategory(section.id) })
                }
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val bannerWidth = (maxWidth - 36.dp).coerceAtMost(520.dp)
                    val coverWidth = posterWidth(maxWidth)
                    LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(section.items, key = { _, item -> item.key }) { index, item ->
                            when (section.style) {
                                SectionStyle.FEATURED -> Featured(item, { onSelect(item) }, Modifier.width(bannerWidth))
                                SectionStyle.POSTERS, SectionStyle.RANKED -> Poster(item, { onSelect(item) },
                                    Modifier.width(coverWidth).testTag("homePoster:${section.id}:${item.key}"),
                                    rank = if (section.style == SectionStyle.RANKED) index + 1 else null)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCarousel(heroes: List<MediaItem>, pager: PagerState, onSelect: (MediaItem) -> Unit,
    onProfile: () -> Unit) {
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    Column {
        Box {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val portrait = maxWidth < 600.dp
                val height = homeHeroHeight(maxWidth, screenHeight)
                HorizontalPager(state = pager, key = { heroes[it].key },
                    modifier = Modifier.fillMaxWidth().height(height).testTag("homeHero")) { page ->
                    val item = heroes[page]
                    Box(Modifier.fillMaxSize().clickable(onClickLabel = "Ver ${item.title}", onClick = { onSelect(item) })) {
                        // Prefer a clean backdrop: posters often already contain a title and credits.
                        Artwork(ArtworkImages.hero(item, portrait = false), Modifier.fillMaxSize(), highResolution = true)
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = .28f),
                            .35f to Color.Transparent,
                            .55f to Ink.copy(alpha = .12f),
                            .78f to Ink.copy(alpha = .78f),
                            1f to Ink)))
                        Column(Modifier.align(Alignment.BottomCenter).widthIn(max = 600.dp).fillMaxWidth()
                            .padding(horizontal = 24.dp).padding(bottom = 18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            HeroTitle(item, compact = !portrait)
                            HeroMetadata(item)
                            if (item.overview.isNotBlank()) Text(item.overview, style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                color = Cream.copy(alpha = .9f))
                        }
                    }
                }
            }
            ProfileAction(onProfile, Modifier.align(Alignment.TopEnd))
        }
        if (heroes.size > 1) Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp)
            .semantics { contentDescription = "Destacado ${pager.currentPage + 1} de ${heroes.size}" },
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

@Composable
private fun HeroTitle(item: MediaItem, compact: Boolean) {
    var failed by remember(item.logoUrl) { mutableStateOf(false) }
    val logo = remember(item.logoUrl) { ArtworkImages.highResolution(item.logoUrl) }
    if (!logo.isNullOrBlank() && !failed && !LocalInspectionMode.current) {
        AsyncImage(model = logo, contentDescription = item.title, contentScale = ContentScale.Fit,
            onError = { failed = true }, modifier = Modifier.fillMaxWidth().height(if (compact) 80.dp else 104.dp))
    } else {
        Text(item.title, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

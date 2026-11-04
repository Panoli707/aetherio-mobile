package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.panoli707.aetherio.nativeapp.data.*
import java.text.NumberFormat
import java.util.Locale

private val DetailNight = Ink
private val DetailPanel = Panel
private val DetailText = Cream
private val DetailMuted = Muted

@Composable
internal fun DetailScreen(state: AppState, item: MediaItem, scroll: LazyListState, onBack: () -> Unit,
    onToggleSaved: (MediaItem) -> Unit, onRetry: () -> Unit, onSeason: (Int) -> Unit, onLink: () -> Unit,
    onRequestSources: (MediaItem, Int?, Int?) -> Unit,
    onEpisodeWatchAction: (MediaItem, EpisodeItem, EpisodeWatchScope, Boolean) -> Unit = { _, _, _, _ -> },
    onToggleWatching: (MediaItem) -> Unit = {}, onOpenExternal: (String) -> Unit = {}, onSelectRelated: (MediaItem) -> Unit = {},
    onPlayTrailer: (MediaTrailer) -> Unit = {}) {
    MaterialTheme(colorScheme = MaterialTheme.colorScheme) {
        val detail = state.detail?.takeIf { it.item.key == item.key }
        val media = detail?.item ?: item
        val recent = WatchProgressRules.latest(state.watchProgress.filter { progress ->
            media.type != MediaType.SERIES || detail?.selectedSeason == null || progress.target.season == detail.selectedSeason
        }, media)?.takeIf { !it.watched && it.positionMs > 0 }
        val episodes = detail?.episodes.orEmpty().filter { it.season == detail?.selectedSeason }
        val first = episodes.firstOrNull().takeUnless { state.episodesLoading }
        val canPlay = media.type == MediaType.MOVIE || (!state.episodesLoading && (recent != null || first != null))
        val play: () -> Unit = {
            if (recent != null) onRequestSources(media, recent.target.season, recent.target.episode)
            else if (media.type == MediaType.MOVIE) onRequestSources(media, null, null)
            else first?.let { onRequestSources(media, it.season, it.number) }
        }
        val playLabel = if (recent != null) "Reproducir${recent.target.episode?.let { " · T${recent.target.season} E$it" }.orEmpty()} · ${watchTime(recent.positionMs)}"
            else if (media.type == MediaType.MOVIE) "Reproducir"
            else first?.let { "Reproducir · T${it.season} E${it.number}" } ?: "Reproducir"
        var actions by remember(media.key) { mutableStateOf<EpisodeItem?>(null) }
        var infoOpen by rememberSaveable(media.key) { mutableStateOf(false) }
        BoxWithConstraints(Modifier.fillMaxSize().background(DetailNight)) {
            val portrait = maxWidth < 600.dp
             val viewportHeight = maxHeight
             val artworkWidth = maxWidth
            val episodeWidth = (maxWidth * .79f).coerceIn(260.dp, 420.dp)
            val relatedWidth = posterWidth(maxWidth)
            LazyColumn(state = scroll, modifier = Modifier.fillMaxSize().testTag("detailScroll"),
                contentPadding = PaddingValues(bottom = 28.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
                item(key = "hero") {
                    Column(Modifier.fillMaxWidth().background(DetailNight).testTag("detailHero"),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                         if (detail == null && (state.detailLoading || state.detailError != null)) {
                             Box(Modifier.fillMaxWidth().height(heroArtworkHeight(artworkWidth, viewportHeight, 2f / 3f))
                                 .testTag("detailArtworkPlaceholder"), contentAlignment = Alignment.Center) {
                                 if (state.detailLoading) CircularProgressIndicator(color = Accent)
                             }
                         } else HeroArtwork(media, portrait, viewportHeight)
                        Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 14.dp, top = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            val metadata = buildList {
                                if (media.score > 0) add("★ ${String.format(Locale.forLanguageTag("es"), "%.1f", media.score)}")
                                detail?.voteCount?.let { add("${NumberFormat.getIntegerInstance(Locale.forLanguageTag("es")).format(it)} votos") }
                                media.year.takeIf(String::isNotBlank)?.let(::add)
                                media.genres.firstOrNull()?.let(::add)
                                detail?.certification?.let(::add)
                                detail?.runtimeMinutes?.let { add("$it min") }
                            }
                            Text(metadata.joinToString("  ·  "), color = DetailText, textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.semantics {
                                    if (media.score > 0) contentDescription = "Valoración ${media.score} sobre 10. ${metadata.joinToString(", ")}"
                                })
                        }
                    }
                }
                item(key = "play") {
                    Column(Modifier.padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(onClick = play, enabled = canPlay, shape = RoundedCornerShape(20.dp),
                            color = Color.Transparent, modifier = Modifier.fillMaxWidth().testTag("detailPrimaryPlay")) {
                            Row(Modifier.background(if (canPlay) Accent else DetailPanel)
                                .padding(horizontal = 18.dp, vertical = 17.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayArrow, null, tint = if (canPlay) DetailNight else DetailMuted)
                                Spacer(Modifier.width(8.dp))
                                Text(playLabel, color = if (canPlay) DetailNight else DetailMuted, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                            }
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            val saved = state.savedItems.any { it.key == media.key }
                            DetailSmallAction(if (saved) "Guardado" else "Biblioteca", if (saved) Icons.Default.Check else Icons.Default.Add,
                                if (saved) "Quitar de la biblioteca" else "Guardar en biblioteca", saved, Modifier.weight(1f)) { onToggleSaved(media) }
                            if (media.isAnime && media.type == MediaType.SERIES) {
                                val following = state.watchingAnime.any { it.key == media.key }
                                DetailSmallAction(if (following) "Siguiendo" else "Seguir", Icons.Default.BookmarkBorder,
                                    if (following) "Quitar de Estás viendo" else "Añadir a Estás viendo", following, Modifier.weight(1f)) { onToggleWatching(media) }
                            }
                            DetailSmallAction("Enlace", Icons.Default.Link, "Reproducir por enlace propio", false, Modifier.weight(1f), onLink)
                            DetailSmallAction("Información", Icons.Default.Info, "Información de las fuentes", false, Modifier.weight(1f)) { infoOpen = true }
                        }
                    }
                }
                item(key = "synopsis") { DetailSynopsis(media.overview, media.key) }
                if (state.detailError != null) item(key = "error") { StatusMessage("No se pudo cargar la ficha", state.detailError, "Reintentar", onRetry) }
                else if (state.detailLoading) item(key = "loading") { LoadingRow("Cargando ficha…") }
                if (detail != null && detail.seasons.isNotEmpty()) item(key = "episodes:${detail.selectedSeason}") {
                    Column {
                        var seasonsOpen by remember { mutableStateOf(false) }
                        Box(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                            TextButton(onClick = { seasonsOpen = true }, colors = ButtonDefaults.textButtonColors(contentColor = Accent),
                                modifier = Modifier.background(AccentContainer, RoundedCornerShape(16.dp))) {
                                val requested = state.pendingSeason ?: detail.selectedSeason
                                Text(detail.seasons.firstOrNull { it.number == requested }?.name ?: "Temporadas", style = MaterialTheme.typography.titleLarge)
                                Icon(Icons.Default.KeyboardArrowDown, "Seleccionar temporada")
                            }
                            DropdownMenu(expanded = seasonsOpen, onDismissRequest = { seasonsOpen = false }, containerColor = DetailPanel) {
                                detail.seasons.forEach { season -> DropdownMenuItem(
                                    text = { Text("${season.name} · ${season.episodeCount} episodios") },
                                    onClick = { seasonsOpen = false; onSeason(season.number) }) }
                            }
                        }
                        if (state.episodesLoading) LoadingRow("Cargando temporada ${state.pendingSeason ?: detail.selectedSeason}…")
                        else if (episodes.isEmpty() && state.detailError == null) StatusMessage("Sin episodios disponibles", "No hay episodios publicados para esta temporada.")
                        else {
                            val selected = episodes.indexOfFirst { it.season == recent?.target?.season && it.number == recent?.target?.episode }.coerceAtLeast(0)
                            val episodeScroll = rememberLazyListState(initialFirstVisibleItemIndex = selected)
                            LazyRow(state = episodeScroll, modifier = Modifier.testTag("detailEpisodes"), contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                items(episodes, key = { "${it.season}:${it.number}" }) { episode ->
                                    val progress = state.watchProgress.firstOrNull { it.target.item.key == media.key && it.target.season == episode.season && it.target.episode == episode.number }
                                    DetailEpisodeCard(episode, media, progress, episodeWidth,
                                        onPlay = { onRequestSources(media, episode.season, episode.number) }, onActions = { actions = episode })
                                }
                            }
                        }
                    }
                }
                if (detail != null && detail.trailers.isNotEmpty()) item(key = "trailers") {
                    DetailFold("Tráilers", "detailTrailers") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            items(detail.trailers, key = { it.id }) { trailer -> Column(Modifier.width(episodeWidth)
                                .clickable(onClickLabel = "Abrir tráiler ${trailer.title}") { onPlayTrailer(trailer) }
                                .semantics(mergeDescendants = true) { contentDescription = "Abrir tráiler ${trailer.title}" }) {
                                Box(Modifier.fillMaxWidth().aspectRatio(1.78f).clip(RoundedCornerShape(18.dp))) {
                                    DetailArtwork(trailer.thumbnailUrl, Modifier.fillMaxSize())
                                }
                                Text(trailer.title, color = DetailText, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                                Text("YouTube · Reproducir en Aetherio", color = DetailMuted, style = MaterialTheme.typography.bodySmall)
                            } }
                        }
                    }
                }
                if (detail != null && detail.cast.isNotEmpty()) item(key = "cast") {
                    DetailFold("Reparto", "detailCast") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(detail.cast, key = { index, member -> "$index:${member.name}" }) { _, member ->
                                Column(Modifier.width(110.dp)) {
                                    DetailArtwork(member.imageUrl, Modifier.fillMaxWidth().height(146.dp).clip(RoundedCornerShape(18.dp)))
                                    Text(member.name, color = DetailText, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.titleSmall)
                                    Text(member.role, color = DetailMuted, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                item(key = "about") {
                    Column(Modifier.padding(18.dp).fillMaxWidth()) {
                        DetailHeading("Acerca de")
                        val facts = detail?.facts.orEmpty() + listOf(DetailFact("Tipo", if (media.isAnime) "Anime · ${media.type.label}" else media.type.label), DetailFact("Catálogo", media.source.name))
                        facts.forEachIndexed { index, fact ->
                            Row(Modifier.fillMaxWidth().background(if (index % 2 == 0) Panel else RaisedPanel, RoundedCornerShape(12.dp))
                                .padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text(fact.label, color = DetailText, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(.8f))
                                Text(fact.value, color = DetailMuted, textAlign = TextAlign.End, modifier = Modifier.weight(1.2f))
                            }
                        }
                        if (media.source == CatalogSource.ANILIST) Text("Numeración propia de cada entrega de anime; puede diferir de las temporadas de TV.",
                            color = DetailMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
                    }
                }
                if (detail != null && detail.availability.isNotEmpty()) item(key = "availability") { DetailAvailability(detail.availability, media.key, onOpenExternal) }
                if (detail != null && detail.reviews.isNotEmpty()) item(key = "reviews") { DetailReviews(detail.reviews, episodeWidth, onOpenExternal) }
                if (detail != null && detail.related.isNotEmpty()) item(key = "related") {
                    Column {
                        DetailHeading("Relacionados", Modifier.padding(horizontal = 18.dp))
                        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(detail.related, key = { it.key }) { related -> Poster(related, { onSelectRelated(related) },
                                Modifier.width(relatedWidth).testTag("relatedPoster:${related.key}")) }
                        }
                    }
                }
            }
            DetailIcon("Volver", Icons.AutoMirrored.Filled.ArrowBack, onBack,
                Modifier.align(Alignment.TopStart).statusBarsPadding().padding(start = 18.dp, top = 12.dp))
        }
        actions?.let { episode -> EpisodeActionsSheet(media, episode, detail?.episodes.orEmpty(), state.watchProgress,
            onDismiss = { actions = null }, onMark = { scope, watched -> onEpisodeWatchAction(media, episode, scope, watched) },
            onSources = { onRequestSources(media, episode.season, episode.number) }, onManual = onLink) }
        if (infoOpen) AlertDialog(onDismissRequest = { infoOpen = false }, title = { Text("Fuentes y reproducción") },
            text = { Text(if (media.source == CatalogSource.ANILIST)
                "Esta ficha conserva su identidad de AniList y la numeración de su entrega. AnimeAV1 busca por título exacto; no se inventan equivalencias IMDb. El botón principal permite elegir una fuente, no garantiza que exista un vídeo disponible."
                else "El botón principal abre las fuentes disponibles de la película o episodio. Puedes elegir un proveedor o usar un enlace HTTPS propio con el botón Enlace. La disponibilidad depende de cada servicio.") },
            confirmButton = { TextButton(onClick = { infoOpen = false }) { Text("Entendido") } })
    }
}

@Composable
private fun DetailIcon(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp).background(DetailPanel.copy(alpha = .8f), CircleShape)) { Icon(icon, label, tint = DetailText) }
}

@Composable
private fun DetailArtwork(url: String?, modifier: Modifier) {
    Artwork(url, modifier, placeholderColor = DetailPanel)
}

@Composable
private fun DetailSmallAction(label: String, icon: ImageVector, description: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.widthIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = description; this.selected = selected }.padding(horizontal = 4.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (selected) Accent else DetailMuted)
        Text(label, color = if (selected) Accent else DetailMuted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun DetailSynopsis(overview: String, key: String) {
    var expanded by rememberSaveable(key) { mutableStateOf(false) }
    var truncated by remember(key) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp).animateContentSize().testTag("detailSynopsis"), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(overview.ifBlank { "No hay sinopsis disponible para este título." }, color = DetailMuted, textAlign = TextAlign.Center,
            maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!expanded) truncated = it.hasVisualOverflow }, style = MaterialTheme.typography.bodyMedium)
        if (truncated || expanded) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Ver menos" else "Leer sinopsis completa", color = Accent) }
    }
}

@Composable
private fun DetailEpisodeCard(episode: EpisodeItem, media: MediaItem, progress: WatchProgress?, width: androidx.compose.ui.unit.Dp,
    onPlay: () -> Unit, onActions: () -> Unit) {
    Surface(color = DetailPanel, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Divider),
        modifier = Modifier.width(width).combinedClickable(onClick = onPlay, onClickLabel = "Reproducir", onLongClick = onActions, onLongClickLabel = "Opciones del episodio")) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio(1.78f)) {
                DetailArtwork(episode.imageUrl ?: media.backdropUrl ?: media.posterUrl, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, DetailNight.copy(alpha = .55f)))))
                IconButton(onClick = onActions, modifier = Modifier.align(Alignment.TopStart).padding(4.dp).size(44.dp)) {
                    Icon(if (progress?.watched == true) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        "Opciones del episodio T${episode.season} E${episode.number}", tint = if (progress?.watched == true) Success else Cream,
                        modifier = Modifier.background(DetailNight.copy(alpha = .65f), CircleShape))
                }
                episode.runtimeMinutes?.let { Text("$it min", color = DetailText, style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).background(DetailNight.copy(alpha = .8f), RoundedCornerShape(6.dp)).padding(5.dp)) }
            }
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("T${episode.season} · Episodio ${episode.number}", color = DetailMuted, style = MaterialTheme.typography.labelMedium)
                Text(episode.title, color = DetailText, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (episode.overview.isNotBlank()) Text(episode.overview, color = DetailMuted, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                if (episode.airedOn.isNotBlank()) Text(episode.airedOn, color = DetailMuted, style = MaterialTheme.typography.bodySmall)
                if (progress?.watched == true) Text("Visto", color = Success, style = MaterialTheme.typography.labelMedium)
                if (progress != null && progress.durationMs > 0) {
                    Text("${watchTime(progress.positionMs)} / ${watchTime(progress.durationMs)}", color = DetailMuted, style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { progress.fraction }, color = Accent, trackColor = Outline, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun DetailHeading(title: String, modifier: Modifier = Modifier) {
    Text(title, color = DetailText, style = MaterialTheme.typography.titleLarge, modifier = modifier.padding(top = 14.dp, bottom = 14.dp))
}

@Composable
private fun DetailFold(title: String, tag: String, content: @Composable () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(true) }
    Column(Modifier.padding(top = 12.dp, bottom = 8.dp).testTag(tag)) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.padding(horizontal = 10.dp)) {
            Text(title, color = DetailText, style = MaterialTheme.typography.titleLarge)
            Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, if (expanded) "Contraer $title" else "Expandir $title", tint = DetailMuted)
        }
        if (expanded) content()
    }
}

@Composable
private fun DetailAvailability(regions: List<WatchRegion>, key: String, onOpen: (String) -> Unit) {
    var country by rememberSaveable(key) { mutableStateOf(Locale.getDefault().country.takeIf { it.matches(Regex("[A-Z]{2}")) } ?: "ES") }
    var menu by remember { mutableStateOf(false) }
    val selected = regions.firstOrNull { it.country == country }
    Column(Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
        DetailHeading("Dónde ver")
        Text("Disponibilidad de JustWatch a través de TMDB", color = DetailMuted, style = MaterialTheme.typography.bodySmall)
        Box {
            TextButton(onClick = { menu = true }) { Text("Región: $country", color = Accent); Icon(Icons.Default.KeyboardArrowDown, "Elegir región", tint = Accent) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = DetailPanel) {
                (listOf(country) + regions.map { it.country }).distinct().sorted().forEach { code ->
                    DropdownMenuItem(text = { Text(Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.forLanguageTag("es")) + " ($code)") },
                        onClick = { country = code; menu = false })
                }
            }
        }
        if (selected == null) Text("No hay información disponible para esta región.", color = DetailMuted)
        else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(selected.platforms, key = { "${it.id}:${it.offer}" }) { provider ->
                    Column(Modifier.width(100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        DetailArtwork(provider.logoUrl, Modifier.size(76.dp).clip(RoundedCornerShape(18.dp)))
                        Text(provider.name, color = DetailText, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
                        Text(provider.offer, color = DetailMuted, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            selected.link?.let { link -> TextButton(onClick = { onOpen(link) }) { Text("Ver opciones en TMDB", color = Accent) } }
        }
    }
}

@Composable
private fun DetailReviews(reviews: List<MediaReview>, width: androidx.compose.ui.unit.Dp, onOpen: (String) -> Unit) {
    var selected by remember { mutableStateOf<MediaReview?>(null) }
    Column(Modifier.padding(vertical = 10.dp)) {
        DetailHeading("Comentarios", Modifier.padding(horizontal = 18.dp))
        Text("Reseñas públicas de usuarios de TMDB", color = DetailMuted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(reviews, key = { _, review -> review.id }) { index, review ->
                Column(Modifier.width(width).clip(RoundedCornerShape(20.dp)).background(if (index % 2 == 0) Panel else RaisedPanel)
                    .clickable(onClickLabel = "Leer reseña de ${review.author}") { selected = review }.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(review.content, color = DetailText, maxLines = 5, overflow = TextOverflow.Ellipsis)
                    Text(review.author, color = DetailText, style = MaterialTheme.typography.titleSmall)
                    if (review.publishedOn.isNotBlank()) Text(review.publishedOn, color = DetailMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    selected?.let { review -> AlertDialog(onDismissRequest = { selected = null }, title = { Text(review.author) },
        text = { Text(review.content, modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { selected = null }) { Text("Cerrar") } },
        dismissButton = { review.url?.let { url -> TextButton(onClick = { onOpen(url) }) { Text("Ver en TMDB") } } }) }
}

package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.panoli707.aetherio.nativeapp.data.*
import com.panoli707.aetherio.nativeapp.R

@Composable
internal fun CatalogGrid(items: List<MediaItem>, scroll: LazyGridState, onSelect: (MediaItem) -> Unit,
    modifier: Modifier = Modifier, footer: Boolean = false, ranked: Boolean = false) {
    LazyVerticalGrid(columns = GridCells.Adaptive(120.dp), state = scroll, modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp,
            bottom = (if (footer) 110.dp else 24.dp) + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
            Poster(item, { onSelect(item) }, rank = if (ranked) index + 1 else null)
        }
    }
}

@Composable
internal fun SearchScreen(state: AppState, scroll: LazyGridState, requestFocus: Boolean, onFocusConsumed: () -> Unit,
    onSearch: (String) -> Unit, onSelect: (MediaItem) -> Unit, onBack: () -> Unit) {
    val requester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    LaunchedEffect(requestFocus) {
        if (requestFocus) { requester.requestFocus(); keyboard?.show(); onFocusConsumed() }
    }
    Column(Modifier.fillMaxSize().imePadding().padding(top = 8.dp)
        .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(Panel)) {
        ScreenHeader("Buscar", onBack)
        OutlinedTextField(value = state.searchQuery, onValueChange = onSearch, singleLine = true,
            label = { Text("Películas y series") }, placeholder = { Text("Buscar por título") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (state.searchQuery.isNotEmpty()) IconButton(onClick = { onSearch("") }) { Icon(Icons.Default.Close, "Borrar búsqueda") } },
            shape = RoundedCornerShape(24.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); keyboard?.hide() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).focusRequester(requester))
        when {
            state.searchError != null -> StatusMessage("No se pudo buscar", "Comprueba tu conexión y vuelve a intentarlo.",
                "Reintentar", { onSearch(state.searchQuery) })
            state.searchLoading -> LoadingRow("Buscando títulos…")
            state.searchQuery.isBlank() -> StatusMessage("¿Qué te apetece ver?", "Escribe el título de una película o una serie.")
            state.searchResults.isEmpty() -> StatusMessage("Sin resultados", "Prueba con otro título o revisa cómo está escrito.")
            else -> CatalogGrid(state.searchResults, scroll, { focus.clearFocus(); keyboard?.hide(); onSelect(it) }, Modifier.weight(1f))
        }
    }
}

@Composable
internal fun LibraryScreen(state: AppState, scroll: LazyGridState, filter: String, onFilter: (String) -> Unit,
    onSelect: (MediaItem) -> Unit, onSearch: () -> Unit) {
    val items = state.savedItems.filter { filter == "all" || it.type.path == filter }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Biblioteca")
        Text("Tus títulos guardados", color = Muted, modifier = Modifier.padding(horizontal = 18.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "Todos", "movie" to "Películas", "tv" to "Series").forEach { (key, label) ->
                FilterChip(selected = filter == key, onClick = { onFilter(key) }, label = { Text(label) })
            }
        }
        if (items.isEmpty()) StatusMessage("Todavía no hay títulos aquí",
            if (state.savedItems.isEmpty()) "Guarda películas y series desde su ficha para encontrarlas en tu biblioteca."
            else "No tienes títulos guardados en este filtro.", "Buscar", onSearch)
        else CatalogGrid(items, scroll, onSelect, Modifier.weight(1f), footer = true)
    }
}

@Composable
internal fun SettingsScreen(state: AppState, onRename: (String) -> Unit, onLink: () -> Unit,
    onAddAddon: (String) -> Unit, onRemoveAddon: (String) -> Unit, onDesktopSourceToggle: (String, Boolean) -> Unit,
    updates: UpdateState, onUpdateAction: (UpdateAction) -> Unit, onAutomaticUpdatesChange: (Boolean) -> Unit) {
    val profileName = state.profileName
    var name by rememberSaveable(profileName) { mutableStateOf(profileName) }
    var saved by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState())
        .padding(bottom = 110.dp).navigationBarsPadding()) {
        ScreenHeader("Ajustes")
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.aetherio_logo), contentDescription = null, modifier = Modifier.size(40.dp))
                Text("Aetherio", style = MaterialTheme.typography.titleLarge)
            }
            Text("Tu espacio", style = MaterialTheme.typography.titleLarge)
            Text("Este perfil se guarda en el dispositivo. No necesitas una cuenta.", color = Muted)
            OutlinedTextField(value = name, onValueChange = { name = it.take(32); saved = false },
                label = { Text("Nombre del perfil") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text("${name.length}/32") }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
            Button(onClick = { focus.clearFocus(); onRename(name.trim()); saved = true }, enabled = name.isNotBlank()) { Text("Guardar nombre") }
            if (saved) Text("Nombre guardado en este dispositivo.", color = Rating,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            UpdateSettings(updates, onUpdateAction, onAutomaticUpdatesChange)
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            AddonsSettings(state, onAddAddon, onRemoveAddon, onDesktopSourceToggle)
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text("Vídeo por enlace", style = MaterialTheme.typography.titleLarge)
            Text("Reproduce un vídeo mediante su URL HTTPS directa. Solo enlaces propios o autorizados; " +
                "esta opción es independiente de los proveedores configurados.", color = Muted)
            OutlinedButton(onClick = onLink) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Reproducir por enlace") }
        }
    }
}

@Composable
internal fun DetailScreen(state: AppState, item: MediaItem, scroll: LazyListState, onBack: () -> Unit,
    onToggleSaved: (MediaItem) -> Unit, onRetry: () -> Unit, onSeason: (Int) -> Unit, onLink: () -> Unit,
    onRequestSources: (MediaItem, Int?, Int?) -> Unit,
    onToggleWatched: (MediaItem, EpisodeItem) -> Unit = { _, _ -> }) {
    val detail = state.detail?.takeIf { it.item.key == item.key }
    val media = detail?.item ?: item
    val saved = state.savedItems.any { it.key == item.key }
    val firstEpisode = if (detail != null && !state.episodesLoading)
        detail.episodes.firstOrNull { it.season == detail.selectedSeason } else null
    var seasonsOpen by remember { mutableStateOf(false) }
    val recent = WatchProgressRules.latest(state.watchProgress, media)?.takeIf { !it.watched && it.positionMs > 0 }
    LazyColumn(state = scroll, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
        item(key = "artwork") {
            Box(Modifier.fillMaxWidth().height(440.dp)) {
                Artwork(media.backdropUrl ?: media.posterUrl, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .25f), Color.Transparent, Ink))))
                RoundAction("Volver", Icons.Default.ArrowBack, onBack,
                    Modifier.statusBarsPadding().padding(start = 18.dp, top = 12.dp).align(Alignment.TopStart))
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MediaTitle(media)
                    Metadata(media)
                    if (detail?.runtimeMinutes != null) Text("${detail.runtimeMinutes} min", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item(key = "actions") {
            Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (recent != null) {
                    Button(onClick = { onRequestSources(media, recent.target.season, recent.target.episode) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Continuar${recent.target.episode?.let { " · T${recent.target.season} E$it" }.orEmpty()} · ${watchTime(recent.positionMs)}")
                    }
                }
                Button(onClick = {
                    if (media.type == MediaType.MOVIE) onRequestSources(media, null, null)
                    else firstEpisode?.let { onRequestSources(media, detail?.selectedSeason, it.number) }
                }, enabled = media.type == MediaType.MOVIE || firstEpisode != null, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(8.dp))
                    Text(if (media.type == MediaType.MOVIE) "Buscar fuentes"
                        else firstEpisode?.let { "Ver fuentes · T${it.season} E${it.number}" } ?: "Selecciona un episodio")
                }
                TextButton(onClick = onLink, modifier = Modifier.fillMaxWidth()) { Text("Reproducir por enlace propio") }
                OutlinedButton(onClick = { onToggleSaved(media) }, modifier = Modifier.fillMaxWidth()
                    .semantics { contentDescription = if (saved) "Quitar de la biblioteca" else "Guardar en biblioteca" }) {
                    Icon(if (saved) Icons.Default.Check else Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp)); Text(if (saved) "Quitar de la biblioteca" else "Guardar en biblioteca")
                }
                Text("Sinopsis", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                Text(media.overview.ifBlank { "No hay sinopsis disponible para este título." }, style = MaterialTheme.typography.bodyLarge)
            }
        }
        if (state.detailError != null) item(key = "error") {
            StatusMessage("No se pudo cargar la ficha", "Puedes volver a intentar cargar sus datos.", "Reintentar", onRetry)
        } else if (state.detailLoading) item(key = "loading") { LoadingRow("Cargando ficha…") }
        if (detail != null && detail.cast.isNotEmpty()) item(key = "cast") {
            Text("Reparto", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(18.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(detail.cast) { member ->
                    Column(Modifier.width(100.dp).semantics(mergeDescendants = true) { }) {
                        Artwork(member.imageUrl, Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(14.dp)))
                        Text(member.name, style = MaterialTheme.typography.titleSmall, maxLines = 2,
                            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
                        Text(member.role, color = Muted, style = MaterialTheme.typography.bodySmall,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        if (detail != null && detail.seasons.isNotEmpty()) item(key = "seasons") {
            Box(Modifier.padding(18.dp)) {
                OutlinedButton(onClick = { seasonsOpen = true }) {
                    Text(detail.seasons.firstOrNull { it.number == detail.selectedSeason }?.name ?: "Seleccionar temporada")
                    Icon(Icons.Default.KeyboardArrowDown, null)
                }
                DropdownMenu(expanded = seasonsOpen, onDismissRequest = { seasonsOpen = false }) {
                    detail.seasons.forEach { season ->
                        DropdownMenuItem(text = { Text("${season.name} · ${season.episodeCount} episodios") },
                            onClick = { seasonsOpen = false; onSeason(season.number) })
                    }
                }
            }
        }
        if (state.episodesLoading) item(key = "episodesLoading") { LoadingRow("Cargando episodios…") }
        else if (detail != null && detail.seasons.isNotEmpty() && detail.episodes.isEmpty() && state.detailError == null) {
            item(key = "noEpisodes") { StatusMessage("Sin episodios disponibles", "No hay episodios publicados para esta temporada.") }
        }
        if (!state.episodesLoading && detail != null) items(detail.episodes, key = { "episode:${it.season}:${it.number}" }) { episode ->
            val progress = state.watchProgress.firstOrNull { it.target.item.key == media.key && it.target.season == episode.season && it.target.episode == episode.number }
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable(role = Role.Button, onClickLabel = "Ver fuentes") { onRequestSources(media, episode.season, episode.number) }
                    .semantics(mergeDescendants = true) { contentDescription = "Ver fuentes: ${media.title}, temporada ${episode.season}, episodio ${episode.number}, ${episode.title}" },
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Artwork(episode.imageUrl, Modifier.width(118.dp).height(76.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("T${episode.season} · E${episode.number}", color = Muted, style = MaterialTheme.typography.bodySmall)
                        Text(episode.title, style = MaterialTheme.typography.titleSmall)
                        Text(if (progress?.watched == true) "Visto" else if ((progress?.positionMs ?: 0) > 0) "Continuar" else "Ver fuentes",
                            color = Cream, style = MaterialTheme.typography.labelMedium)
                        if (progress != null && progress.durationMs > 0) {
                            Text("${watchTime(progress.positionMs)} / ${watchTime(progress.durationMs)}", style = MaterialTheme.typography.bodySmall)
                            LinearProgressIndicator(progress = { progress.fraction }, modifier = Modifier.fillMaxWidth(), color = Cream)
                        }
                        if (episode.overview.isNotBlank()) Text(episode.overview, maxLines = 3, overflow = TextOverflow.Ellipsis,
                            color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(onClick = { onToggleWatched(media, episode) }, modifier = Modifier.align(Alignment.End)
                    .semantics { contentDescription = "${if (progress?.watched == true) "Marcar como no visto" else "Marcar como visto"}: T${episode.season} E${episode.number}" }) {
                    Icon(if (progress?.watched == true) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (progress?.watched == true) "Marcar como no visto" else "Marcar como visto")
                }
            }
        }
    }
}

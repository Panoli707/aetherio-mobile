package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.panoli707.aetherio.nativeapp.data.*

@Composable
internal fun LibraryScreen(state: AppState, scroll: LazyGridState, filter: String, onFilter: (String) -> Unit,
    onSelect: (MediaItem) -> Unit, onSearch: () -> Unit,
    onFilters: (LibraryFilters) -> Unit = {}, onDiscover: (DiscoverSelection, Boolean, Boolean) -> Unit = { _, _, _ -> },
    personalActions: PersonalActions = PersonalActions()) {
    var discover by rememberSaveable { mutableStateOf(false) }
    androidx.activity.compose.BackHandler(discover) { discover = false }
    val discoverScroll = rememberLazyGridState()
    val entries = remember(state.savedItems, state.savedAddedAt, state.watchProgress, state.watchingAnime, state.animeTracking) { LibraryFiltering.entries(state) }
    val filtered = remember(entries, state.libraryFilters, filter) { LibraryFiltering.filter(entries, state.libraryFilters.copy(type = filter)) }
    val filters = filtered.filters
    val discovery = state.discover
    // Only a filter change resets scroll, not recomposition or returning from a detail screen.
    var previousLibrarySelection by rememberSaveable { mutableStateOf("${filters.list}|${filters.type}|${filters.genre}|${filters.sort}") }
    val librarySelection = "${filters.list}|${filters.type}|${filters.genre}|${filters.sort}"
    LaunchedEffect(librarySelection) {
        if (previousLibrarySelection != librarySelection) { scroll.scrollToItem(0); previousLibrarySelection = librarySelection }
    }
    var previousDiscoverSelection by rememberSaveable { mutableStateOf(discovery.selection.toString()) }
    LaunchedEffect(discovery.selection) {
        if (previousDiscoverSelection != discovery.selection.toString()) {
            discoverScroll.scrollToItem(0); previousDiscoverSelection = discovery.selection.toString()
        }
    }
    LaunchedEffect(discover, state.addons, state.addonsLoading) { if (discover) onDiscover(discovery.selection, false, false) }
    fun change(value: LibraryFilters) { onFilter(value.type); onFilters(value) }
    Column(Modifier.fillMaxSize().testTag("libraryBrowser")) {
        ScreenHeader(if (discover) "Descubrir" else "Biblioteca")
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            FilterChip(!discover, { discover = false }, label = { Text("Mi biblioteca") }, leadingIcon = { Icon(Icons.Default.Inventory2, null, Modifier.size(18.dp)) })
            FilterChip(discover, { discover = true }, label = { Text("Descubrir") }, leadingIcon = { Icon(Icons.Default.Explore, null, Modifier.size(18.dp)) }, modifier = Modifier.testTag("discoverTab"))
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            LazyVerticalGrid(columns = GridCells.Fixed(posterColumns(maxWidth)), state = if (discover) discoverScroll else scroll,
                modifier = Modifier.fillMaxSize().testTag(if (discover) "discoverGrid" else "libraryGrid"),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp,
                    bottom = 132.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                if (!discover) {
                    item(key = "summary", span = { GridItemSpan(maxLineSpan) }) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("En curso, vistos y guardados", color = Muted)
                            Text("${entries.size} en tu biblioteca · ${entries.count { it.item.type == MediaType.MOVIE }} películas · ${entries.count { it.item.type == MediaType.SERIES }} series y anime",
                                style = MaterialTheme.typography.bodySmall, color = Muted)
                            LibraryAniList(state.animeTracking.connections.getValue(AnimeService.ANILIST), personalActions)
                        }
                    }
                    item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            LazyRow(Modifier.testTag("libraryFilters"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                item { LibrarySelector("Lista", filters.list, filtered.lists.options(), { change(filters.copy(list = it, type = "all", genre = "all")) }) }
                                item { LibrarySelector("Tipo", filters.type, filtered.types.options(), { change(filters.copy(type = it)) }) }
                                if (filtered.genres.size > 1) item { LibrarySelector("Género", filters.genre, filtered.genres.options(), { change(filters.copy(genre = it)) }) }
                                item { LibrarySelector("Orden", filters.sort, listOf("added_desc" to "Añadidos recientemente", "added_asc" to "Añadidos primero", "title_asc" to "Título A–Z", "title_desc" to "Título Z–A"), { change(filters.copy(sort = it)) }) }
                            }
                            if (filters != LibraryFilters()) TextButton(onClick = { change(LibraryFilters()) }) { Text("Restablecer filtros") }
                        }
                    }
                    if (filtered.entries.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        if (entries.isEmpty()) StatusMessage("Todavía no hay títulos aquí", "Las películas y series que veas o guardes aparecerán aquí automáticamente.", "Buscar", onSearch)
                        else StatusMessage("No hay títulos con estos filtros", "Cambia la lista, el tipo o el género.", "Restablecer filtros", { change(LibraryFilters()) })
                    }
                    items(filtered.entries, key = { it.item.key }) { entry -> Poster(entry.item, { onSelect(entry.item) }) }
                } else {
                    val selection = DesktopDiscover.normalize(discovery.selection, discovery.catalogs)
                    val catalog = discovery.catalogs.firstOrNull { it.key == selection.catalog }
                    item(key = "discoverFilters", span = { GridItemSpan(maxLineSpan) }) {
                        LazyRow(Modifier.testTag("discoverFilters"), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            item { LibrarySelector("Tipo", selection.type, discovery.catalogs.map { it.type }.distinct().map { it to when (it) { "movie" -> "Películas"; "tv" -> "Series"; else -> "Anime" } },
                                { onDiscover(DiscoverSelection(it, "", ""), false, false) }) }
                            item { LibrarySelector("Catálogo", selection.catalog, discovery.catalogs.filter { it.type == selection.type }.map { it.key to "${it.name} · ${it.provider}" },
                                { onDiscover(selection.copy(catalog = it, genre = ""), false, false) }, width = 260) }
                            if (catalog?.genres?.isNotEmpty() == true) item { LibrarySelector("Género", selection.genre, listOf("" to "Todos") + catalog.genres.keys.map { it to it },
                                { onDiscover(selection.copy(genre = it), false, false) }) }
                        }
                    }
                    if (discovery.catalogsLoading) item(span = { GridItemSpan(maxLineSpan) }) { Text("Consultando catálogos de extensiones…", color = Muted, style = MaterialTheme.typography.bodySmall) }
                    discovery.catalogsError?.let { error -> item(span = { GridItemSpan(maxLineSpan) }) {
                        Column { Text(error, color = Muted); TextButton(onClick = { onDiscover(discovery.selection, false, true) }) { Text("Reintentar extensiones") } }
                    } }
                    when {
                        discovery.loading -> item(span = { GridItemSpan(maxLineSpan) }) { LoadingRow("Cargando catálogo…") }
                        discovery.error != null && discovery.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                            StatusMessage("No se pudo cargar", discovery.error, "Reintentar", { onDiscover(discovery.selection, false, true) })
                        }
                        discovery.items.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                            StatusMessage("Sin resultados", "Este catálogo no devolvió títulos compatibles con los filtros seleccionados.")
                        }
                        else -> {
                            items(discovery.items, key = { it.key }) { item -> Poster(item, { onSelect(item) }) }
                            if (discovery.error != null) item(span = { GridItemSpan(maxLineSpan) }) { Text(discovery.error, color = MaterialTheme.colorScheme.error) }
                            if (discovery.hasMore) item(key = "more", span = { GridItemSpan(maxLineSpan) }) {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    OutlinedButton(onClick = { onDiscover(discovery.selection, true, false) }, enabled = !discovery.loadingMore,
                                        modifier = Modifier.testTag("discoverMore")) { Text(if (discovery.loadingMore) "Cargando…" else if (discovery.error != null) "Reintentar página" else "Mostrar más") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun List<LibraryFacet>.options() = map { it.key to "${it.label} (${it.count})" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySelector(label: String, value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit, width: Int = 170) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, Outline),
        modifier = Modifier.width(width.dp).heightIn(min = 64.dp).testTag("filter:$label"), contentPadding = PaddingValues(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Muted)
            Text(options.firstOrNull { it.first == value }?.second.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis, color = Cream)
        }
        Icon(Icons.Default.ExpandMore, null, Modifier.size(20.dp))
    }
    if (open) ModalBottomSheet(onDismissRequest = { open = false }, containerColor = Panel) {
        Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp))
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(options, key = { it.first }) { (key, title) ->
                Surface(onClick = { open = false; onSelect(key) }, color = if (key == value) RaisedPanel else Panel, modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f))
                        if (key == value) Icon(Icons.Default.Check, "Seleccionado", tint = Accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryAniList(connection: AnimeConnection, actions: PersonalActions) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("AniList · ${connection.account?.name ?: "Sin conectar"}", Modifier.weight(1f), color = Muted, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { if (connection.account == null || connection.authorizationRequired) actions.connect(AnimeService.ANILIST) else actions.refresh(AnimeService.ANILIST) }, enabled = !connection.busy) {
            Text(if (connection.busy) "Sincronizando…" else if (connection.account == null || connection.authorizationRequired) "Conectar" else "Sincronizar")
        }
    }
    connection.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
}

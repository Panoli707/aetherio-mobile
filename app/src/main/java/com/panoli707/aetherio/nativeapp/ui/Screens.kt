package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
    val items = LibraryCatalog.all(state).filter { filter == "all" || it.type.path == filter }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Biblioteca")
        Text("En curso, vistos y guardados", color = Muted, modifier = Modifier.padding(horizontal = 18.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("all" to "Todos", "movie" to "Películas", "tv" to "Series").forEach { (key, label) ->
                FilterChip(selected = filter == key, onClick = { onFilter(key) }, label = { Text(label) })
            }
        }
        if (items.isEmpty()) StatusMessage("Todavía no hay títulos aquí",
            if (LibraryCatalog.all(state).isEmpty()) "Las películas y series que veas o guardes aparecerán aquí automáticamente."
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
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            LicenseSettings()
        }
    }
}

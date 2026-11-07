package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.panoli707.aetherio.nativeapp.data.*

@Composable
internal fun CatalogGrid(items: List<MediaItem>, scroll: LazyGridState, onSelect: (MediaItem) -> Unit,
    modifier: Modifier = Modifier, footer: Boolean = false, ranked: Boolean = false) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        LazyVerticalGrid(columns = GridCells.Fixed(posterColumns(maxWidth)), state = scroll, modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 10.dp,
                bottom = (if (footer) 110.dp else 24.dp) + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            itemsIndexed(items, key = { _, item -> item.key }) { index, item ->
                Poster(item, { onSelect(item) }, rank = if (ranked) index + 1 else null)
            }
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

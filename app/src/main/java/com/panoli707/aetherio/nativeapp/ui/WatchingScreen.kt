package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.panoli707.aetherio.nativeapp.data.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
internal fun WatchingScreen(state: AppState, onSelect: (MediaItem) -> Unit, onWatching: () -> Unit,
    onLoad: (WatchingRequest, Boolean) -> Unit, onLeaveMovieTv: () -> Unit = {}) {
    var categoryName by rememberSaveable { mutableStateOf(WatchingCategory.ANIME.name) }
    val category = WatchingCategory.valueOf(categoryName)
    val holder = rememberSaveableStateHolder()
    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("watchingScreen")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Calendario", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            if (category == WatchingCategory.ANIME) TextButton(onClick = onWatching, modifier = Modifier.testTag("openWatchingAnime")) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null)
                Spacer(Modifier.width(4.dp))
                Text("Estás viendo")
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WatchingCategory.entries.forEach { value ->
                FilterChip(selected = category == value, onClick = { categoryName = value.name },
                    label = { Text(if (value == WatchingCategory.ANIME) "Anime" else "Películas y series") },
                    modifier = Modifier.testTag("watchingCategory:${value.name}"))
            }
        }
        holder.SaveableStateProvider(category.name) {
            WatchingAgenda(if (category == WatchingCategory.ANIME) state.animeCalendar else state.movieTvCalendar,
                category, onSelect, onLoad, onLeaveMovieTv)
        }
    }
}

@Composable
private fun ColumnScope.WatchingAgenda(state: WatchingCalendarState, category: WatchingCategory, onSelect: (MediaItem) -> Unit,
    onLoad: (WatchingRequest, Boolean) -> Unit, onLeaveMovieTv: () -> Unit) {
    val anime = category == WatchingCategory.ANIME
    val leaveMovieTv by rememberUpdatedState(onLeaveMovieTv)
    DisposableEffect(category) { onDispose { if (!anime) leaveMovieTv() } }
    val zone = if (anime) AnimeSeason.japan else WatchingDates.utc
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selected by rememberSaveable { mutableStateOf(WatchingDates.date(now, zone)) }
    var month by rememberSaveable { mutableStateOf(false) }
    val request = remember(category, selected, month) { WatchingDates.request(category, selected, month) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val load by rememberUpdatedState(onLoad)
    LaunchedEffect(request, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            load(request, false)
            var ticks = 0
            while (true) {
                delay(60_000); now = System.currentTimeMillis()
                if (++ticks % 5 == 0) load(request, false)
            }
        }
    }
    val matching = state.request == request
    val events = if (matching) state.events else emptyList()
    val countsKnown = matching && state.loaded
    val days = remember(request) { WatchingDates.days(request.start, request.end) }
    val locale = Locale.forLanguageTag("es")
    fun label(date: String, pattern: String): String = SimpleDateFormat(pattern, locale).apply { timeZone = WatchingDates.utc }
        .format(Date(WatchingDates.millis(date)))
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { selected = WatchingDates.shift(request.start, if (month) -1 else -7,
            if (month) Calendar.MONTH else Calendar.DAY_OF_MONTH) }) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Periodo anterior")
        }
        Text(if (month) label(request.start, "MMMM yyyy") else "${label(request.start, "d MMM")} – ${label(WatchingDates.shift(request.end, -1), "d MMM yyyy")}",
            style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        IconButton(onClick = { selected = request.end }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Periodo siguiente") }
        TextButton(onClick = { selected = WatchingDates.date(now, zone) }) { Text("Hoy") }
    }
    Row(Modifier.padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(selected = !month, onClick = { month = false }, label = { Text("Semana") })
        FilterChip(selected = month, onClick = { month = true }, label = { Text("Mes") })
    }
    val dayScroll = rememberLazyListState()
    LaunchedEffect(request, month) { if (!month) dayScroll.scrollToItem(days.indexOf(selected).coerceAtLeast(0)) }
    val scroll = if (anime) rememberLazyListState() else remember { LazyListState() }
    var lastAgendaSelection by rememberSaveable { mutableStateOf("$selected:$month") }
    LaunchedEffect(selected, month) {
        val selection = "$selected:$month"
        if (lastAgendaSelection != selection) { scroll.scrollToItem(0); lastAgendaSelection = selection }
    }
    LazyColumn(state = scroll, modifier = Modifier.weight(1f).testTag("watchingAgenda"),
        contentPadding = PaddingValues(bottom = 130.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item("dates") {
            if (month) {
                val offset = Calendar.getInstance(WatchingDates.utc).apply { timeInMillis = WatchingDates.millis(request.start) }
                    .let { (it.get(Calendar.DAY_OF_WEEK) + 5) % 7 }
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    Row { listOf("L", "M", "X", "J", "V", "S", "D").forEach { Text(it, modifier = Modifier.weight(1f).padding(8.dp), color = Muted) } }
                    (List<String?>(offset) { null } + days).chunked(7).forEach { week ->
                        Row {
                            week.forEach { day ->
                                if (day == null) Spacer(Modifier.weight(1f).height(50.dp))
                                else CalendarDay(day.takeLast(2).toInt().toString(), if (countsKnown) events.count { it.date == day } else null, day == selected,
                                    { selected = day }, Modifier.weight(1f), day)
                            }
                            repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            } else LazyRow(state = dayScroll, contentPadding = PaddingValues(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(days, key = { it }) { day -> CalendarDay(label(day, "EEE d"), if (countsKnown) events.count { it.date == day } else null, day == selected,
                    { selected = day }, Modifier.width(82.dp), day) }
            }
        }
        item("source") {
            Text(if (anime) "AniList · emisión original en Japón (JST). No indica disponibilidad de vídeo."
                else "TMDB · estreno original y episodios; fechas sin hora confirmada.",
                style = MaterialTheme.typography.bodySmall, color = Muted, modifier = Modifier.padding(horizontal = 18.dp))
        }
        if (matching && state.error != null) item("error") {
            StatusMessage("No se pudo actualizar la agenda", state.error, "Reintentar", { onLoad(request, true) })
        }
        if (!matching || state.loading) item("loading") { LoadingRow("Consultando emisiones y estrenos…") }
        val daily = events.filter { it.date == selected }
        item("heading") {
            val summary = if (countsKnown) "${daily.size} estrenos y emisiones" else if (!matching || state.loading) "Consultando agenda" else "Agenda no disponible"
            Text("${if (selected == WatchingDates.date(now, zone)) "Hoy" else label(selected, "EEEE d MMMM")} · $summary",
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
        }
        if (matching && state.loaded && !state.loading && state.error == null && daily.isEmpty()) item("empty") {
            StatusMessage(if (state.hasMore) "Sin resultados cargados para este día" else "Sin emisiones confirmadas",
                if (state.hasMore) "Carga más títulos para ampliar la agenda de este periodo." else "No hay fechas confirmadas para este día. Prueba otra fecha o periodo.")
        }
        var previousGroup: String? = null
        daily.forEach { event ->
            val hour = event.at?.let { SimpleDateFormat("HH", Locale.US).apply { timeZone = zone }.format(Date(it * 1000)).toInt() }
            val group = when { hour == null -> "Sin hora confirmada"; hour < 6 -> "Madrugada"; hour < 12 -> "Mañana"; hour < 18 -> "Tarde"; else -> "Noche" }
            if (group != previousGroup) item("group:$group") {
                Text(group, color = Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
            }
            previousGroup = group
            item(event.key) { WatchingEventCard(event, now, anime, onSelect) }
        }
        if (matching && state.hasMore) item("more") {
            OutlinedButton(onClick = { onLoad(request.copy(page = state.page + 1), false) }, enabled = !state.loading,
                modifier = Modifier.padding(horizontal = 18.dp).fillMaxWidth().testTag("watchingLoadMore")) {
                Text("Cargar más títulos")
            }
        }
    }
}

@Composable
private fun CalendarDay(label: String, count: Int?, selected: Boolean, onClick: () -> Unit, modifier: Modifier, date: String) {
    Surface(color = if (selected) AccentContainer else Panel, shape = RoundedCornerShape(12.dp),
        modifier = modifier.padding(2.dp).testTag("watchingDay:$date").clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Tab, onClick = onClick).semantics { this.selected = selected }) {
        Column(Modifier.heightIn(min = 48.dp).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, color = if (selected) Accent else Cream, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            Text(count?.toString() ?: "—", color = Muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun WatchingEventCard(event: WatchingEvent, now: Long, anime: Boolean, onSelect: (MediaItem) -> Unit) {
    val time = event.at?.let { SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = AnimeSeason.japan }.format(Date(it * 1000)) } ?: "—"
    val status = if (event.at != null) { if (event.at <= now / 1000) "Emitido" else "Próximo" }
        else if (event.date > WatchingDates.date(now, if (anime) AnimeSeason.japan else WatchingDates.utc)) "Próximo" else "Fecha anunciada"
    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(time, color = Muted, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(50.dp))
        Surface(shape = RoundedCornerShape(14.dp), color = Panel, modifier = Modifier.weight(1f)
            .testTag("watchingEvent:${event.key}").clip(RoundedCornerShape(14.dp))
            .clickable(onClickLabel = "Ver ${event.item.title}") { onSelect(event.item) }) {
            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CanonicalPoster(event.item, Modifier.width(40.dp).height(60.dp).clip(RoundedCornerShape(6.dp)))
                Column(Modifier.weight(1f)) {
                    Text(event.item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                    Text(if (event.episode != null) "${event.season?.let { "T$it · " }.orEmpty()}Episodio ${event.episode}${if (event.premiere) " · Estreno" else ""}"
                        else "Estreno · Película", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                Text(status, color = Accent, style = MaterialTheme.typography.labelSmall, modifier = Modifier.widthIn(max = 74.dp))
            }
        }
    }
}

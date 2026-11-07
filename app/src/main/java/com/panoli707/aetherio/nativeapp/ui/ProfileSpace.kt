package com.panoli707.aetherio.nativeapp.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.panoli707.aetherio.nativeapp.data.*
import kotlinx.coroutines.delay

data class PersonalActions(
    val connect: (AnimeService) -> Unit = {}, val refresh: (AnimeService) -> Unit = {}, val disconnect: (AnimeService) -> Unit = {},
    val updateAnime: (AnimeService, AnimeEntry, AnimeStatus, Int, Double) -> Unit = { _, _, _, _, _ -> },
    val editAnime: (AnimeService, Int) -> Unit = { _, _ -> }, val dismissAnime: () -> Unit = {},
    val importAnime: (AnimeEntry) -> Unit = {}, val appearance: (ProfileAppearance) -> Unit = {},
    val importImage: (Uri, Boolean) -> Unit = { _, _ -> }, val playDownload: (String) -> Unit = {},
    val downloadAction: (String, String, Int?) -> Unit = { _, _, _ -> },
)

private val ProfileColors = listOf(Color(0xFF164E63), Color(0xFF4C1D95), Color(0xFF065F46), Color(0xFF7C2D12), Color(0xFF1E3A8A), Color(0xFF831843))

@Composable
internal fun ProfileAvatar(appearance: ProfileAppearance, modifier: Modifier = Modifier) {
    Box(modifier.clip(CircleShape).background(ProfileColors[appearance.avatarStyle % ProfileColors.size])
        .border(2.dp, Accent, CircleShape), contentAlignment = Alignment.Center) {
        if (appearance.avatarPath != null) AsyncImage(appearance.avatarPath, "Foto del perfil local", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Icon(listOf(Icons.Default.Person, Icons.Default.Nightlight, Icons.Default.AutoAwesome, Icons.Default.Pets,
            Icons.Default.Movie, Icons.Default.RocketLaunch, Icons.Default.Headphones, Icons.Default.Star)[appearance.avatarStyle],
            "Avatar del perfil local", tint = Cream, modifier = Modifier.fillMaxSize(.55f))
    }
}

@Composable
private fun ProfileBanner(appearance: ProfileAppearance, modifier: Modifier = Modifier) {
    Box(modifier.background(Brush.linearGradient(listOf(ProfileColors[appearance.bannerStyle], Ink)))) {
        if (appearance.bannerPath != null) AsyncImage(appearance.bannerPath, "Banner del perfil local", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink))))
    }
}

@Composable
internal fun ProfileSpace(state: AppState, onRename: (String) -> Unit, actions: PersonalActions, onBack: () -> Unit,
    onSelect: (MediaItem) -> Unit, initialDownloads: Boolean = false, navigationRequest: Int = 0) {
    var page by rememberSaveable { mutableStateOf(if (initialDownloads) "downloads" else "profile") }
    var handledRequest by rememberSaveable { mutableStateOf(-1) }
    LaunchedEffect(navigationRequest) {
        if (navigationRequest > 0 && handledRequest != navigationRequest) page = if (initialDownloads) "downloads" else "profile"
        handledRequest = navigationRequest
    }
    BackHandler(page != "profile") { page = "profile" }
    when (page) {
        "colors" -> ThemeColorSettings { page = "profile" }
        "edit" -> ProfileEditor(state, onRename, actions) { page = "profile" }
        "downloads" -> DownloadsScreen(state, actions) { page = "profile" }
        else -> Box(Modifier.fillMaxSize().background(Ink).testTag("profileSpace")) {
            ProfileBanner(state.profileAppearance, Modifier.fillMaxWidth().height(380.dp))
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 132.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                item {
                    Column(Modifier.statusBarsPadding().padding(horizontal = 22.dp).padding(top = 12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            RoundAction("Volver", Icons.Default.ArrowBack, onBack)
                            Spacer(Modifier.weight(1f))
                            ProfileAvatar(state.profileAppearance, Modifier.size(48.dp).clickable { page = "edit" })
                        }
                        Column(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                            Text("Mi espacio", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                            Text("Tu biblioteca, listas y descargas.", color = Muted)
                        }
                    }
                }
                item {
                    Surface(Modifier.padding(horizontal = 20.dp).fillMaxWidth(), shape = RoundedCornerShape(26.dp),
                        color = Panel.copy(alpha = if (TransparentControlsEnabled) .20f else .85f), border = BorderStroke(1.dp, Outline)) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            ProfileAvatar(state.profileAppearance, Modifier.size(72.dp))
                            Column(Modifier.weight(1f)) {
                                Text(state.profileName, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("Avatar, banner y nombre", color = Muted, style = MaterialTheme.typography.bodySmall,
                                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            OutlinedButton(onClick = { page = "edit" },
                                modifier = Modifier.testTag("editProfile"),
                                shape = RoundedCornerShape(50),
                                border = BorderStroke(1.dp, Accent.copy(alpha = if (TransparentControlsEnabled) .5f else .7f)),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent)) {
                                Text("Editar perfil ›", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
                state.profileMessage?.let { message -> item { Text(message, color = Muted, modifier = Modifier.padding(horizontal = 24.dp)) } }
                AnimeService.entries.forEach { service -> item(key = service.name) {
                    AnimeConnectionCard(service, state.animeTracking.connections.getValue(service), state.watchProgress, actions, onSelect)
                } }
                item {
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("PERSONALIZACIÓN", color = Muted, style = MaterialTheme.typography.labelLarge)
                        ProfileDestination("Diseño de Aetherio", "Colores y apariencia de la app", Icons.Default.Palette, { page = "colors" })
                        ProfileDestination("Descargas", "Cola, almacenamiento y vídeos sin conexión", Icons.Default.Download, { page = "downloads" })
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileDestination(title: String, summary: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = Panel.copy(alpha = if (TransparentControlsEnabled) .22f else 1f),
        border = BorderStroke(1.dp, Outline), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, null, tint = Accent)
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(summary, color = Muted, style = MaterialTheme.typography.bodySmall) }
            Text("›", color = Muted)
        }
    }
}

@Composable
private fun AnimeConnectionCard(service: AnimeService, connection: AnimeConnection, history: List<WatchProgress>, actions: PersonalActions, onSelect: (MediaItem) -> Unit) {
    var category by rememberSaveable(service.name) { mutableStateOf(if (service == AnimeService.ANILIST) "favorites" else AnimeStatus.WATCHING.name) }
    var logout by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<AnimeEntry?>(null) }
    var suggestedProgress by remember { mutableStateOf<Int?>(null) }
    var review by remember { mutableStateOf(false) }
    var visibleError by remember(service) { mutableStateOf<String?>(null) }
    LaunchedEffect(connection.error) {
        visibleError = connection.error
        if (connection.error != null) {
            delay(5_000)
            visibleError = null
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().testTag("account:${service.name}")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("LISTAS · ${service.label}", color = Muted, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (connection.account != null) {
                IconButton(onClick = { actions.refresh(service) }, enabled = !connection.busy) { Icon(Icons.Default.Sync, "Actualizar ${service.label}") }
                TextButton(onClick = { logout = true }, enabled = !connection.busy) { Text("Desconectar") }
            }
        }
        if (connection.account == null) {
            OutlinedButton(onClick = { actions.connect(service) }, enabled = !connection.busy,
                shape = RoundedCornerShape(20.dp), modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth().heightIn(min = 64.dp).testTag("connect:${service.name}")) {
                Text("Conectar ${service.label}")
            }
        } else {
            val account = connection.account
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                AsyncImage(account.avatar, "Avatar de ${account.name} en ${service.label}", contentScale = ContentScale.Crop,
                    modifier = Modifier.size(66.dp).clip(CircleShape).background(Panel))
                Column(Modifier.weight(1f)) {
                    Text(account.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(if (connection.authorizationRequired) "Autorización caducada" else "Conectado", color = if (connection.authorizationRequired) Accent else Success)
                    Text(if (connection.updatedAt > 0) "Lista guardada para consultar sin conexión" else "Lista pendiente de actualizar", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (connection.authorizationRequired) OutlinedButton(onClick = { actions.connect(service) }, enabled = !connection.busy, modifier = Modifier.padding(horizontal = 22.dp)) {
                Text("Volver a conectar ${service.label}")
            }
            val filters = buildList {
                if (service == AnimeService.ANILIST) add("favorites" to "Favoritos")
                AnimeStatus.entries.forEach { add(it.name to it.label) }
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters, key = { it.first }) { (id, title) ->
                    val count = connection.entries.count { if (id == "favorites") it.favorite else it.status?.name == id }
                    FilterChip(selected = category == id, onClick = { category = id }, label = { Text("$title ($count)") })
                }
            }
            val visible = connection.entries.filter { if (category == "favorites") it.favorite else it.status?.name == category }
            if (visible.isEmpty() && !connection.busy) Text("No hay títulos en esta categoría.", color = Muted, modifier = Modifier.padding(horizontal = 24.dp))
            else LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                items(visible, key = { it.id }) { entry ->
                    Column(Modifier.width(230.dp)) {
                        Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(18.dp)).background(Panel)
                            .clickable(enabled = entry.item() != null) { entry.item()?.let(onSelect) }) {
                            AsyncImage(entry.banner ?: entry.poster, entry.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).padding(top = 8.dp)) {
                                Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
                                Text("${if (entry.movie) "Película" else "EP ${entry.progress}"}${entry.status?.label?.let { " · $it" }.orEmpty()}", color = Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { suggestedProgress = null; editing = entry }, enabled = !connection.busy) { Icon(Icons.Default.Edit, "Editar ${entry.title} en ${service.label}", Modifier.size(20.dp)) }
                        }
                    }
                }
            }
            TextButton(onClick = { review = true }, enabled = !connection.busy, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Revisar sincronización del progreso local") }
        }
        if (connection.busy) LinearProgressIndicator(Modifier.padding(horizontal = 22.dp).fillMaxWidth())
        visibleError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 24.dp)) }
    }
    if (logout) AlertDialog(onDismissRequest = { logout = false }, title = { Text("Desconectar ${service.label}") },
        text = { Text("Se borrarán la autorización y la copia local de esta lista. No se borrará tu cuenta, lista remota, biblioteca ni historial. Puedes revocar el permiso también en los ajustes de ${service.label}.") },
        confirmButton = { TextButton(onClick = { logout = false; actions.disconnect(service) }) { Text("Desconectar") } },
        dismissButton = { TextButton(onClick = { logout = false }) { Text("Cancelar") } })
    editing?.let { entry -> AnimeEntryEditor(service, entry, actions, { editing = null }, suggestedProgress) }
    if (review) AnimeSyncReview(service, connection, history, onClose = { review = false }, onEdit = { entry, local -> suggestedProgress = local; editing = entry; review = false })
}

@Composable
private fun ProfileEditor(state: AppState, onRename: (String) -> Unit, actions: PersonalActions, onBack: () -> Unit) {
    val focus = LocalFocusManager.current
    var avatars by rememberSaveable { mutableStateOf(true) }
    var name by rememberSaveable(state.profileName) { mutableStateOf(state.profileName) }
    var pickingAvatar by rememberSaveable { mutableStateOf(true) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { actions.importImage(it, pickingAvatar) } }
    val appearance = state.profileAppearance
    Box(Modifier.fillMaxSize().background(Ink).testTag("profileEditor")) {
        ProfileBanner(appearance, Modifier.fillMaxWidth().height(300.dp))
        LazyColumn(Modifier.fillMaxSize().imePadding(), contentPadding = PaddingValues(bottom = 132.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { ScreenHeader("Editar perfil", onBack) }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileAvatar(appearance, Modifier.size(128.dp))
                    OutlinedTextField(name, { name = it.take(32) }, singleLine = true, label = { Text("Nombre local") }, modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
                    Button(onClick = { focus.clearFocus(); onRename(name.trim()) }, enabled = LocalLibraryStore.normalizeName(name) != null) { Text("Guardar nombre") }
                    Text("La personalización se guarda solo en este dispositivo.", color = Muted, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(avatars, { avatars = true }, label = { Text("Avatares") })
                        FilterChip(!avatars, { avatars = false }, label = { Text("Banners") })
                    }
                    OutlinedButton(onClick = { pickingAvatar = avatars; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        enabled = !state.profileBusy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Icon(Icons.Default.PhotoLibrary, null); Spacer(Modifier.width(8.dp)); Text(if (avatars) "Elegir foto de la galería" else "Elegir banner de la galería")
                    }
                    if (state.profileBusy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.profileMessage?.let { Text(it, color = Muted) }
                }
            }
            if (avatars) items((0..7).chunked(4)) { row ->
                Row(Modifier.padding(horizontal = 22.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    row.forEach { style -> ProfileAvatar(appearance.copy(avatarStyle = style, avatarPath = null), Modifier.size(64.dp)
                        .clickable(enabled = !state.profileBusy) { actions.appearance(appearance.copy(avatarStyle = style, avatarPath = null)) }) }
                }
            } else items((0..5).chunked(2)) { row ->
                Row(Modifier.padding(horizontal = 22.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { style -> ProfileBanner(appearance.copy(bannerStyle = style, bannerPath = null), Modifier.weight(1f).height(100.dp)
                        .clip(RoundedCornerShape(18.dp)).border(if (appearance.bannerPath == null && appearance.bannerStyle == style) 2.dp else 0.dp, Accent, RoundedCornerShape(18.dp))
                        .clickable(enabled = !state.profileBusy) { actions.appearance(appearance.copy(bannerStyle = style, bannerPath = null)) }) }
                }
            }
            item {
                TextButton(onClick = { actions.appearance(ProfileAppearance()) }, enabled = !state.profileBusy, modifier = Modifier.padding(horizontal = 22.dp)) { Text("Restablecer avatar y banner") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AnimeEntryEditor(service: AnimeService, entry: AnimeEntry, actions: PersonalActions, onClose: () -> Unit, suggestedProgress: Int? = null) {
    var progress by remember(entry.id) { mutableStateOf((suggestedProgress ?: entry.progress).toString()) }
    var score by remember(entry.id) { mutableStateOf((entry.score / 10).toString()) }
    var status by remember(entry.id) { mutableStateOf(entry.status ?: AnimeStatus.PLANNED) }
    var import by remember { mutableStateOf(false) }
    val count = progress.toIntOrNull()
    val points = score.replace(',', '.').toDoubleOrNull()
    AlertDialog(onDismissRequest = onClose, title = { Text(entry.title, maxLines = 2, overflow = TextOverflow.Ellipsis) }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Cambios en ${service.label}. No se enviarán al otro servicio.", color = Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { AnimeStatus.entries.forEach { value ->
                FilterChip(status == value, { status = value }, label = { Text(value.label, style = MaterialTheme.typography.labelSmall) })
            } }
            OutlinedTextField(progress, { progress = it.filter(Char::isDigit).take(6) }, label = { Text("Episodios vistos${if (entry.episodes > 0) " / ${entry.episodes}" else ""}") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(score, { score = it.take(5) }, label = { Text(if (service == AnimeService.MAL) "Puntuación (0–10, enteros)" else "Puntuación (0–10)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = if (service == AnimeService.MAL) KeyboardType.Number else KeyboardType.Decimal))
            if (entry.item() != null) TextButton(onClick = { import = true }) { Text("Importar progreso remoto a Aetherio") }
        }
    }, confirmButton = { TextButton(enabled = count != null && count in 0..100_000 && (entry.episodes <= 0 || count <= entry.episodes) &&
        points != null && points.isFinite() && points in 0.0..10.0 && (service != AnimeService.MAL || points % 1.0 == 0.0),
        onClick = { actions.updateAnime(service, entry, status, requireNotNull(count), requireNotNull(points) * 10); onClose() }) { Text("Guardar en ${service.label}") } },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancelar") } })
    if (import) AlertDialog(onDismissRequest = { import = false }, title = { Text("Importar a Aetherio") },
        text = { Text("Se añadirá este anime a tu biblioteca y se marcarán como vistos sus primeros ${entry.progress} episodios usando su identidad exacta. No se eliminarán marcas existentes ni se inventarán posiciones de reproducción. No cambia la lista del otro servicio.") },
        confirmButton = { TextButton(onClick = { actions.importAnime(entry); onClose() }) { Text("Importar") } }, dismissButton = { TextButton(onClick = { import = false }) { Text("Cancelar") } })
}

@Composable
private fun AnimeSyncReview(service: AnimeService, connection: AnimeConnection, history: List<WatchProgress>, onClose: () -> Unit, onEdit: (AnimeEntry, Int) -> Unit) {
    val changes = remember(connection.entries, history) { connection.entries.map { it to AnimeSyncRules.contiguous(it, history, SeriesIdentity.index) }
        .filter { (entry, local) -> local > 0 && local != entry.progress } }
    AlertDialog(onDismissRequest = onClose, title = { Text("Revisar progreso · ${service.label}") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Solo episodios consecutivos confirmados y equivalencias por ID. Revisa cada cambio antes de enviarlo; no hay sobrescrituras automáticas ni transferencia entre cuentas.", color = Muted)
            if (changes.isEmpty()) Text("No hay diferencias aplicables. Los títulos sin equivalencia exacta o fuera de tu lista remota no se envían.")
            LazyColumn(Modifier.heightIn(max = 320.dp)) { items(changes, key = { it.first.id }) { (entry, local) ->
                ListItem(headlineContent = { Text(entry.title, maxLines = 2) }, supportingContent = { Text("Aetherio: $local · ${service.label}: ${entry.progress}") },
                    trailingContent = { TextButton(onClick = { onEdit(entry, local) }) { Text("Revisar") } })
            } }
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Cerrar") } })
}

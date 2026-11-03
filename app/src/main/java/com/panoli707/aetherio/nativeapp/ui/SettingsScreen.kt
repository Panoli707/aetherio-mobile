package com.panoli707.aetherio.nativeapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panoli707.aetherio.nativeapp.BuildConfig
import com.panoli707.aetherio.nativeapp.R
import com.panoli707.aetherio.nativeapp.data.*
import java.text.Normalizer
import java.util.Locale

// Only destinations backed by existing Aetherio features; no placeholder account or integration screens.
private enum class SettingsSection(val title: String, val summary: String, val icon: ImageVector, val keywords: String) {
    PROFILE("Perfil local", "Tu nombre y espacio en este dispositivo", Icons.Default.Person, "nombre usuario biblioteca"),
    SOURCES("Fuentes y contenido", "Provider Hub, fuentes adicionales y proveedores propios", Icons.Default.Explore, "addons animeav1 manifest catálogo"),
    PLAYBACK("Reproducción", "Reproductor integrado y vídeo por enlace", Icons.Default.PlayArrow, "audio subtítulos velocidad torrent https vídeo"),
    UPDATES("Actualizaciones", "Buscar versiones y gestionar las descargas", Icons.Default.SystemUpdate, "versión descargar instalar automáticas"),
    ABOUT("Acerca de", "Aetherio, licencias y software libre", Icons.Default.Info, "información gpl código motor nuvio")
}

@Composable
internal fun SettingsScreen(state: AppState, onRename: (String) -> Unit, onLink: () -> Unit,
    onAddAddon: (String) -> Unit, onRemoveAddon: (String) -> Unit, onDesktopSourceToggle: (String, Boolean) -> Unit,
    updates: UpdateState, onUpdateAction: (UpdateAction) -> Unit, onAutomaticUpdatesChange: (Boolean) -> Unit) {
    var destination by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    val pages = rememberSaveableStateHolder()
    val focus = LocalFocusManager.current
    val selected = SettingsSection.entries.firstOrNull { it.name == destination }
    val back = { focus.clearFocus(); destination = null }
    BackHandler(enabled = selected != null, onBack = back)

    pages.SaveableStateProvider(selected?.name ?: "overview") {
        Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).testTag("settingsScroll")
            .padding(bottom = 128.dp).navigationBarsPadding()) {
            if (selected == null) {
                Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp).padding(top = 22.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("Configuración", fontSize = 36.sp, fontWeight = FontWeight.Bold, lineHeight = 42.sp,
                        modifier = Modifier.semantics { heading() })
                    OutlinedTextField(value = search, onValueChange = { search = it.take(120) },
                        placeholder = { Text("Buscar en ajustes…") }, singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = { if (search.isNotBlank()) IconButton(onClick = { search = "" }) {
                            Icon(Icons.Default.Close, "Borrar búsqueda de ajustes")
                        } },
                        colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Cream.copy(alpha = .045f),
                            focusedContainerColor = Cream.copy(alpha = .045f), unfocusedBorderColor = Cream.copy(alpha = .09f)),
                        shape = RoundedCornerShape(24.dp), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
                        modifier = Modifier.fillMaxWidth().testTag("settingsSearch"))
                }
                val query = search.normalizedSetting()
                val sections = SettingsSection.entries.filter { "${it.title} ${it.summary} ${it.keywords}".normalizedSetting().contains(query) }
                val groups = listOf("Tu espacio" to listOf(SettingsSection.PROFILE),
                    "Contenido y reproducción" to listOf(SettingsSection.SOURCES, SettingsSection.PLAYBACK),
                    "Aplicación" to listOf(SettingsSection.UPDATES, SettingsSection.ABOUT))
                groups.forEach { (groupTitle, group) ->
                    val visible = group.filter { it in sections }
                    if (visible.isNotEmpty()) Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 20.dp)) {
                        Text(groupTitle, color = Muted, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 8.dp, bottom = 10.dp).semantics { heading() })
                        Surface(color = Cream.copy(alpha = .045f), shape = RoundedCornerShape(28.dp),
                            border = BorderStroke(1.dp, Cream.copy(alpha = .07f))) {
                            Column {
                                visible.forEachIndexed { index, section ->
                                    if (index > 0) HorizontalDivider(Modifier.padding(start = 84.dp, end = 20.dp), color = Cream.copy(alpha = .06f))
                                    SettingsDestination(section) { focus.clearFocus(); destination = section.name }
                                }
                            }
                        }
                    }
                }
                if (sections.isEmpty()) StatusMessage("Sin ajustes coincidentes", "Prueba con perfil, fuentes, reproducción o actualizaciones.")
                SettingsBrand()
            } else {
                ScreenHeader(selected.title, back)
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    when (selected) {
                        SettingsSection.PROFILE -> ProfileSettings(state.profileName, onRename)
                        SettingsSection.SOURCES -> AddonsSettings(state, onAddAddon, onRemoveAddon, onDesktopSourceToggle)
                        SettingsSection.PLAYBACK -> {
                            SettingsInfo(Icons.Default.PlayCircle, "Reproductor integrado",
                                "Reproduce enlaces de vídeo y torrents compatibles dentro de Aetherio. La disponibilidad y los formatos dependen de cada fuente.")
                            SettingsInfo(Icons.Default.Subtitles, "Audio y subtítulos",
                                "Elige las pistas disponibles, la velocidad y el ajuste de imagen desde los controles del reproductor.")
                            HorizontalDivider(color = Cream.copy(alpha = .10f))
                            Text("Vídeo por enlace", style = MaterialTheme.typography.titleLarge)
                            Text("Abre una URL HTTPS directa de un vídeo propio o autorizado, sin usar los proveedores de las fichas.", color = Muted)
                            Button(onClick = { focus.clearFocus(); onLink() }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Default.PlayArrow, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Reproducir por enlace")
                            }
                        }
                        SettingsSection.UPDATES -> UpdateSettings(updates, onUpdateAction, onAutomaticUpdatesChange)
                        SettingsSection.ABOUT -> {
                            SettingsBrand()
                            Text("Películas, series y anime en una aplicación nativa para Android.", color = Muted)
                            Text("Versión ${BuildConfig.VERSION_NAME} · Compilación ${BuildConfig.VERSION_CODE}", color = Muted)
                            HorizontalDivider(color = Cream.copy(alpha = .10f))
                            LicenseSettings()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsDestination(section: SettingsSection, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, modifier = Modifier.fillMaxWidth().testTag("setting:${section.name}")) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 22.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.size(48.dp).background(Cream.copy(alpha = .08f), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Icon(section.icon, null, tint = Cream, modifier = Modifier.size(25.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(section.title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(section.summary, color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Muted.copy(alpha = .65f), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ProfileSettings(profileName: String, onRename: (String) -> Unit) {
    var name by rememberSaveable(profileName) { mutableStateOf(profileName) }
    var saved by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    Text("Tu espacio", style = MaterialTheme.typography.titleLarge)
    Text("Este perfil se guarda en el dispositivo. No necesitas una cuenta.", color = Muted)
    OutlinedTextField(value = name, onValueChange = { name = it.take(32); saved = false }, singleLine = true,
        label = { Text("Nombre del perfil") }, supportingText = { Text("${name.length}/32") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }), modifier = Modifier.fillMaxWidth())
    Button(onClick = { focus.clearFocus(); onRename(name.trim()); saved = true }, enabled = name.isNotBlank()) { Text("Guardar nombre") }
    if (saved) Text("Nombre guardado en este dispositivo.", color = Rating,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}

@Composable
private fun SettingsInfo(icon: ImageVector, title: String, description: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = Aqua, modifier = Modifier.padding(top = 2.dp).size(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SettingsBrand() {
    Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(painterResource(R.drawable.aetherio_logo), null, modifier = Modifier.size(40.dp))
            Text("Aetherio", style = MaterialTheme.typography.titleLarge)
        }
        Text("Versión ${BuildConfig.VERSION_NAME}", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

private fun String.normalizedSetting(): String = Normalizer.normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .filterNot { Character.getType(it) == Character.NON_SPACING_MARK.toInt() }.trim()

package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.panoli707.aetherio.nativeapp.data.*
import com.panoli707.aetherio.nativeapp.player.VideoFormat

@Composable
internal fun ProviderIcon(name: String, logo: String?, modifier: Modifier = Modifier) {
    var loaded by remember(logo) { mutableStateOf(false) }
    Box(modifier.clip(RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
        if (!loaded) Text(name.take(1).uppercase(), color = Accent, style = MaterialTheme.typography.labelLarge)
        if (logo != null) AsyncImage(model = logo, contentDescription = null, contentScale = ContentScale.Fit,
            onSuccess = { loaded = true }, onError = { loaded = false }, modifier = Modifier.fillMaxSize())
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SourceResultCard(source: StreamSource, state: AppState, query: SourceQuery,
    onPlay: (StreamSource, VideoFormat) -> Unit, onPreferencesChange: (SourcePreferences) -> Unit = {}) {
    val metadata = source.display
    val duration = metadata.durationMinutes ?: state.detail?.takeIf { it.item.key == query.item.key }?.let { detail ->
        if (query.episode == null) detail.runtimeMinutes else detail.episodes.firstOrNull {
            it.season == query.season && it.number == query.episode
        }?.runtimeMinutes ?: detail.runtimeMinutes
    }
    val title = metadata.title ?: if (source.name == "AnimeAV1") query.item.title + (query.episode?.let { " - Episodio $it" } ?: "")
        else source.name.ifBlank { query.item.title }
    val subtitles = source.subtitles.map { it.lang.trim() }.filter(String::isNotEmpty).distinct()
    val languages = metadata.languages
    Surface(onClick = { onPlay(source, if (source.isTorrent) VideoFormat.AUTO else source.format) },
        enabled = source.playable, color = Ink.copy(alpha = .60f), shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Cream.copy(alpha = .12f)),
        modifier = Modifier.fillMaxWidth().testTag("source:${source.addonId}:${source.id}").semantics {
            contentDescription = "Seleccionar fuente $title, proveedor ${source.providerName}"
        }) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProviderIcon(source.providerName, metadata.logoUrl, Modifier.size(30.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text("Presentado por ${source.providerName}", color = Cream, style = MaterialTheme.typography.labelLarge,
                     maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (SourcePreferenceRules.providerKey(source) in state.sourcePreferences.favoriteProviders)
                    Text("Proveedor favorito", color = Accent, style = MaterialTheme.typography.labelSmall)
                val facts = listOfNotNull(metadata.size?.let { "Tamaño: $it" }, metadata.indexer?.let { "Fuente: $it" },
                    duration?.let { "Duración: $it min" })
                if (facts.isNotEmpty()) Text(facts.joinToString(" | "), color = Muted, style = MaterialTheme.typography.bodySmall)
                if (source.isTorrent) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    val level = SeederLevel.of(metadata.seeders)
                    val color = when (level) {
                        SeederLevel.LOW -> Color(0xFFFCA5A5)
                        SeederLevel.MEDIUM -> Color(0xFFFCD34D)
                        SeederLevel.HIGH -> Color(0xFF6EE7B7)
                        SeederLevel.UNKNOWN -> Muted
                    }
                    Icon(Icons.Default.People, null, Modifier.size(15.dp), tint = color)
                    Text(if (level == SeederLevel.UNKNOWN) "Seeders sin reportar" else "${metadata.seeders} seeders",
                        color = color, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.semantics { stateDescription = level.description })
                    Text("Torrent", color = Cream, style = MaterialTheme.typography.labelMedium)
                }
                if (languages.isNotEmpty()) Text("Idiomas: ${languages.joinToString(", ")}", color = Cream,
                    style = MaterialTheme.typography.bodySmall)
                if (subtitles.isNotEmpty()) Text(subtitles.joinToString(" · ") { "Sub: $it" }, color = Muted,
                    style = MaterialTheme.typography.bodySmall)
                // Keep unstructured addon details only when no structured display data was supplied.
                if (metadata.title == null && metadata.size == null && languages.isEmpty() && source.description.isNotBlank()) {
                    Text(source.description, color = Muted, style = MaterialTheme.typography.bodySmall,
                        maxLines = 4, overflow = TextOverflow.Ellipsis)
                }
                val badges = (metadata.badges + languages.mapNotNull(SourceMetadata::languageBadge) +
                    listOfNotNull(source.format.takeIf { it != VideoFormat.AUTO }?.label)).distinct()
                if (badges.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    badges.forEach { SourceBadge(it) }
                }
                if (source.url.startsWith("http://", true)) Text("Vídeo HTTP sin cifrar en un dominio permitido.",
                    color = Muted, style = MaterialTheme.typography.bodySmall)
                if (!source.playable) Text("Fuente no compatible", color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            val favorite = SourcePreferenceRules.sourceKey(source) in state.sourcePreferences.favoriteSources
            IconToggleButton(checked = favorite, onCheckedChange = {
                onPreferencesChange(SourcePreferenceRules.toggleSource(source, state.sourcePreferences))
            }, modifier = Modifier.size(48.dp).testTag("favorite:${source.addonId}:${source.id}").semantics {
                contentDescription = if (favorite) "Quitar fuente de favoritos" else "Marcar fuente favorita"
            }) {
                Icon(if (favorite) Icons.Default.Star else Icons.Outlined.StarBorder, null, tint = if (favorite) Accent else Muted)
            }
        }
    }
}

private val badgeAssets = mapOf("FHD" to "1080p", "HD" to "720p", "4K" to "2160p", "SD" to "gr-480p-sd",
    "AVC" to "video-codec-avc", "HEVC" to "video-codec-hevc", "AAC" to "a-aac", "FLAC" to "a-flac",
    "OPUS" to "a-opus", "DTS" to "dts", "DD+" to "dolby-digital-plus", "Atmos" to "atmos",
    "HDR" to "hdr", "HDR10" to "hdr10", "HDR10+" to "hdr10-plus", "Dolby Vision" to "dolby-vision",
    "EN" to "l-en", "ES" to "l-es", "FR" to "l-fr", "DE" to "l-de", "IT" to "l-it", "PT" to "l-pt-pt",
    "JA" to "l-ja", "RU" to "l-ru", "AR" to "l-ar", "HI" to "l-hi", "KO" to "l-ko", "ZH" to "l-zh")

@Composable
private fun SourceBadge(label: String) {
    Surface(color = Cream.copy(alpha = .06f), shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, Cream.copy(alpha = .12f))) {
        val asset = badgeAssets[label]
        Box(Modifier.defaultMinSize(minWidth = 42.dp, minHeight = 30.dp).padding(horizontal = 8.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center) {
            var loaded by remember(asset) { mutableStateOf(false) }
            if (asset != null) AsyncImage("file:///android_asset/stream-tags/$asset.png", label,
                modifier = Modifier.size(width = 42.dp, height = 20.dp), contentScale = ContentScale.Fit,
                onSuccess = { loaded = true }, onError = { loaded = false })
            if (!loaded) Text(label, color = Cream, style = MaterialTheme.typography.labelLarge)
        }
    }
}

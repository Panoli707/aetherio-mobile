package com.panoli707.aetherio.nativeapp.data

import com.panoli707.aetherio.nativeapp.player.VideoFormat

data class AddonConfig(
    val id: String,
    val manifestId: String,
    val name: String,
    val manifestUrl: String,
    val types: Set<MediaType>,
    val idPrefixes: List<String> = emptyList(),
    val logoUrl: String? = null,
    // Runtime provenance, never restored from a user addon or its manifest.
    val hubManaged: Boolean = false,
    val payloadAttribution: Boolean = false
)

data class SourceQuery(val item: MediaItem, val season: Int? = null, val episode: Int? = null) {
    val key: String get() = "${item.key}:${season ?: 0}:${episode ?: 0}"
}

data class StreamSource(
    val id: String,
    val addonId: String,
    val addonName: String,
    val name: String,
    val description: String,
    val url: String = "",
    val format: VideoFormat = VideoFormat.AUTO,
    val headers: Map<String, String> = emptyMap(),
    val allowDesktopHttp: Boolean = false,
    val infoHash: String? = null,
    val fileIdx: Int? = null,
    val sources: List<String> = emptyList(),
    val subtitles: List<StreamSubtitle> = emptyList(),
    val behaviorHints: String = "{}",
    val rawStream: String = "{}",
    val display: SourceDisplay = SourceDisplay(),
    val allowProviderHeaders: Boolean = false
) {
    val isTorrent: Boolean get() = infoHash != null || url.startsWith("magnet:", true)
    val playable: Boolean get() = isTorrent || url.isNotEmpty()
    val providerKey: String get() = display.providerKey ?: addonId
    val providerName: String get() = display.providerName ?: addonName
}

/** Presentation only: never used to choose playback transport, headers or identifiers. */
data class SourceDisplay(
    val providerKey: String? = null,
    val providerName: String? = null,
    val logoUrl: String? = null,
    val title: String? = null,
    val size: String? = null,
    val indexer: String? = null,
    val durationMinutes: Int? = null,
    val seeders: Int? = null,
    val languages: List<String> = emptyList(),
    val badges: List<String> = emptyList(),
    val resolutionHeight: Int? = null
)

data class StreamSubtitle(val id: String, val url: String, val lang: String, val mimeType: String? = null)

/** Torrents have a separate session resolver; a magnet/infoHash must never reach the HTTP datasource. */
internal object SourcePlayback {
    fun httpUrl(source: StreamSource): String {
        require(!source.isTorrent) { "El torrent requiere una sesión del motor, no una URL HTTP directa" }
        return com.panoli707.aetherio.nativeapp.player.MediaUrlPolicy.validate(source.url, source.allowDesktopHttp)
    }
}

data class SourceResult(val sources: List<StreamSource>, val unsupportedCount: Int = 0)

package com.panoli707.aetherio.nativeapp.data

import com.panoli707.aetherio.nativeapp.player.VideoFormat

data class AddonConfig(
    val id: String,
    val manifestId: String,
    val name: String,
    val manifestUrl: String,
    val types: Set<MediaType>,
    val idPrefixes: List<String> = emptyList()
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
    val url: String,
    val format: VideoFormat = VideoFormat.AUTO
)

data class SourceResult(val sources: List<StreamSource>, val unsupportedCount: Int = 0)

package com.panoli707.aetherio.nativeapp.data

import org.json.JSONObject

data class MediaListState(val status: AnimeStatus = AnimeStatus.PLANNED, val progress: Int = 0, val score: Double = 0.0)

fun mediaStateKey(item: MediaItem, season: Int? = null): String =
    if (item.type == MediaType.SERIES && item.isAnime && season != null) "${item.key}:season:$season" else item.key

fun effectiveMediaListState(item: MediaItem, season: Int?, states: Map<String, MediaListState>,
    progress: List<WatchProgress>, saved: Boolean): MediaListState {
    val explicit = states[mediaStateKey(item, season)] ?: MediaListState().takeIf { saved } ?: MediaListState()
    if (item.isAnime && item.type == MediaType.SERIES && explicit.status in setOf(AnimeStatus.PLANNED, AnimeStatus.WATCHING)) {
        val hasWatched = progress.any { it.watched && it.target.item.key == item.key &&
            (season == null || it.target.season == season) }
        return explicit.copy(status = if (hasWatched) AnimeStatus.WATCHING else AnimeStatus.PLANNED)
    }
    return explicit
}

internal object MediaListCodec {
    fun encode(values: Map<String, MediaListState>): String = JSONObject().apply {
        values.forEach { (key, value) -> put(key, JSONObject().put("status", value.status.name)
            .put("progress", value.progress).put("score", value.score)) }
    }.toString()

    fun decode(raw: String): Map<String, MediaListState> = runCatching {
        val json = JSONObject(raw)
        json.keys().asSequence().mapNotNull { key -> runCatching {
            val row = json.getJSONObject(key)
            normalizeStateKey(key) to MediaListState(AnimeStatus.valueOf(row.getString("status")),
                row.getInt("progress").coerceIn(0, 100_000), row.getDouble("score").coerceIn(0.0, 100.0))
        }.getOrNull() }.toMap()
    }.getOrDefault(emptyMap())

    private fun normalizeStateKey(key: String): String {
        val match = Regex("^(.*):season:([0-9]{1,4})$").matchEntire(key) ?: return SeriesIdentity.key(key)
        return "${SeriesIdentity.key(match.groupValues[1])}:season:${match.groupValues[2]}"
    }
}

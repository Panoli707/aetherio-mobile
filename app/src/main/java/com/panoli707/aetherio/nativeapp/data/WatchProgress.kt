package com.panoli707.aetherio.nativeapp.data

import org.json.JSONArray
import org.json.JSONObject

data class WatchTarget(
    val item: MediaItem,
    val season: Int? = null,
    val episode: Int? = null,
    val episodeTitle: String = "",
    val imageUrl: String? = null
) {
    val key: String get() = "${item.key}:${season ?: -1}:${episode ?: -1}"
}

data class WatchProgress(
    val target: WatchTarget,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val watched: Boolean = false,
    val updatedAt: Long = 0
) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toDouble() / durationMs).coerceIn(0.0, 1.0).toFloat() else 0f
    val resumePositionMs: Long get() = if (watched) 0 else positionMs
}

object WatchProgressRules {
    /** First unwatched episode in the selected season; advance to the next season after completion. */
    fun nextEpisode(season: Int?, episodes: List<EpisodeItem>, seasons: List<SeasonInfo>,
        watched: Set<Pair<Int, Int>>): Pair<Int, Int>? {
        val selected = season ?: episodes.firstOrNull()?.season ?: seasons.minByOrNull { it.number }?.number ?: return null
        val currentEpisodes = episodes.filter { it.season == selected }.sortedBy { it.number }
        currentEpisodes.firstOrNull { (it.season to it.number) !in watched }?.let { return it.season to it.number }
        if (currentEpisodes.isEmpty()) return selected to 1
        val nextSeason = seasons.asSequence().filter { it.number > selected && it.episodeCount > 0 }
            .minByOrNull { it.number }
        return nextSeason?.number?.let { it to 1 } ?: (selected to 1)
    }
    fun valid(target: WatchTarget): Boolean = target.item.id.matches(
        if (target.item.source != CatalogSource.CINEMETA) Regex("[0-9]{1,12}") else Regex("tt[0-9]{5,12}")
    ) && target.item.title.length in 1..500 && when (target.item.type) {
        MediaType.MOVIE -> target.season == null && target.episode == null
        MediaType.SERIES -> target.season != null && target.season in 0..10000 && target.episode != null && target.episode in 1..100000
    }

    fun sample(previous: WatchProgress?, target: WatchTarget, positionMs: Long, durationMs: Long,
        ended: Boolean, now: Long): WatchProgress? {
        if (!valid(target) || positionMs < 0 || durationMs <= 0 || durationMs > 7 * 24 * 60 * 60 * 1000L) return null
        val position = positionMs.coerceAtMost(durationMs)
        if (position == 0L && previous == null && !ended) return null
        val complete = ended || position.toDouble() / durationMs >= .95
        return WatchProgress(target, position, durationMs, previous?.watched == true || complete, now)
    }

    fun continuing(entries: List<WatchProgress>): List<WatchProgress> = entries.sortedByDescending { it.updatedAt }
        .distinctBy { it.target.item.key }.filter { !it.watched && it.positionMs > 0 }.take(30)

    fun latest(entries: List<WatchProgress>, item: MediaItem): WatchProgress? =
        entries.filter { it.target.item.key == item.key }.maxByOrNull { it.updatedAt }
}

fun watchTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "${seconds / 3600}:${(seconds / 60 % 60).toString().padStart(2, '0')}:${(seconds % 60).toString().padStart(2, '0')}"
    else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

internal object WatchProgressCodec {
    const val MAX_ENTRIES = 5000
    private fun targetJson(target: WatchTarget) = JSONObject().apply {
        // Store catalog metadata only, never playback URLs, headers or provider credentials.
        val compact = target.item.copy(overview = "", genres = emptyList(), logoUrl = null,
            posterUrl = target.item.posterUrl?.takeIf { it.length <= 1024 },
            backdropUrl = target.item.backdropUrl?.takeIf { it.length <= 1024 },
            portraitArtworkUrl = target.item.portraitArtworkUrl?.takeIf { it.length <= 1024 })
        put("item", JSONArray(CatalogJson.encode(listOf(compact))).getJSONObject(0))
        put("season", target.season); put("episode", target.episode)
        put("episodeTitle", target.episodeTitle.take(500)); put("image", CatalogJson.image(target.imageUrl?.takeIf { it.length <= 1024 }))
    }
    fun encodeTarget(target: WatchTarget): String {
        require(WatchProgressRules.valid(target))
        return targetJson(target).toString()
    }
    fun decodeTarget(raw: String?): WatchTarget? = runCatching {
        if (raw == null || raw.length > 16000) return null
        val json = JSONObject(raw)
        val item = CatalogJson.decode(JSONArray().put(json.getJSONObject("item")).toString()).singleOrNull() ?: return null
        WatchTarget(item, if (json.has("season")) json.getInt("season") else null,
            if (json.has("episode")) json.getInt("episode") else null,
            json.optString("episodeTitle").take(500), CatalogJson.image(json.optString("image")))
            .takeIf(WatchProgressRules::valid)
    }.getOrNull()

    fun encode(entries: List<WatchProgress>): String = JSONObject().put("schema", 1).put("entries", JSONArray().apply {
        entries.sortedByDescending { it.updatedAt }.distinctBy { it.target.key }.take(MAX_ENTRIES).forEach { entry ->
            put(JSONObject().put("target", targetJson(entry.target)).put("position", entry.positionMs)
                .put("duration", entry.durationMs).put("watched", entry.watched).put("updated", entry.updatedAt))
        }
    }).toString()

    fun decode(raw: String): List<WatchProgress> = runCatching {
        if (raw.length > 24 * 1024 * 1024) return emptyList()
        val json = JSONObject(raw)
        if (json.optInt("schema") != 1) return emptyList()
        val entries = json.getJSONArray("entries")
        (0 until minOf(entries.length(), MAX_ENTRIES)).mapNotNull { index -> runCatching {
            val entry = entries.getJSONObject(index)
            val target = decodeTarget(entry.getJSONObject("target").toString()) ?: return@mapNotNull null
            val duration = entry.getLong("duration")
            val position = entry.getLong("position")
            if (duration !in 0..604800000L || position < 0 || (duration > 0 && position > duration)) return@mapNotNull null
            WatchProgress(target, position, duration, entry.optBoolean("watched"), entry.getLong("updated").coerceAtLeast(0))
        }.getOrNull() }.sortedByDescending { it.updatedAt }.distinctBy { it.target.key }
    }.getOrDefault(emptyList())
}

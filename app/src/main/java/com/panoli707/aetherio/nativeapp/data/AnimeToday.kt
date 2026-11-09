package com.panoli707.aetherio.nativeapp.data

import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

data class AnimeAiringEpisode(val item: MediaItem, val episode: Int, val airedAt: Long) {
    val key: String get() = "${item.key}:$episode:$airedAt"
}
data class AnimeTodayState(val dayStart: Long = 0, val entries: List<AnimeAiringEpisode> = emptyList(),
    val loading: Boolean = false, val loaded: Boolean = false, val error: String? = null)

internal object AnimeToday {
    const val HISTORY_DAYS = 30

    fun start(nowMs: Long): Long = Calendar.getInstance(AnimeSeason.japan).apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis / 1000

    fun historyStart(nowMs: Long): Long = start(nowMs) - (HISTORY_DAYS - 1) * 86400L

    /** Latest confirmed airing per AniList entry, including earlier Japanese calendar days. */
    fun released(entries: List<AnimeAiringEpisode>, nowMs: Long): List<AnimeAiringEpisode> {
        val oldest = historyStart(nowMs)
        val latest = nowMs / 1000
        return entries.filter { it.episode > 0 && it.airedAt >= oldest && it.airedAt <= latest }
            .sortedWith(compareByDescending<AnimeAiringEpisode> { it.airedAt }.thenBy { it.item.key }.thenByDescending { it.episode })
            .distinctBy { it.item.key }
    }

    fun relativeAge(airedAt: Long, nowMs: Long): String {
        val minutes = ((nowMs / 1000 - airedAt).coerceAtLeast(0) / 60)
        return when {
            minutes < 1 -> "Hace menos de 1 min"
            minutes < 60 -> "Hace $minutes min"
            minutes < 1440 -> "Hace ${minutes / 60} h"
            minutes < 2880 -> "Hace 1 día"
            else -> "Hace ${minutes / 1440} días"
        }
    }

    /** An airing-feed confirmation takes precedence over a conflicting future timestamp in detail data. */
    fun applyConfirmedAiring(item: MediaItem, episodes: List<EpisodeItem>, entries: List<AnimeAiringEpisode>, nowMs: Long,
        index: SeriesIndex = SeriesIdentity.index): List<EpisodeItem> {
        val confirmations = entries.filter { it.episode > 0 && it.airedAt in 1..(nowMs / 1000) }.mapNotNull { entry ->
            val part = index.part(entry.item)
            val target = when {
                entry.item.key == item.key -> 1 to entry.episode
                part != null && item.source == CatalogSource.TMDB && item.type == MediaType.SERIES && part.tmdbId == item.id -> {
                    if (item.seriesNumbering || item.imdbNumbering)
                        part.season to (entry.episode + part.offset - index.baseOffset(index.parts(item), part.season))
                    else part.tmdbSeason to (entry.episode + part.offset)
                }
                else -> return@mapNotNull null
            }
            target to entry.airedAt
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.max() }
        return episodes.map { episode ->
            val time = confirmations[episode.season to episode.number]
            if (time != null) episode.copy(released = true, airedOn = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .apply { timeZone = AnimeSeason.japan }.format(Date(time * 1000))) else episode
        }
    }

    fun episode(json: JSONObject): AnimeAiringEpisode? {
        val media = json.optJSONObject("media") ?: return null
        if (media.optBoolean("isAdult") || media.optString("type", "ANIME") != "ANIME") return null
        val item = AnimeSeasonProtocol.item(media) ?: return null
        val episode = json.optInt("episode", 0).takeIf { it > 0 } ?: return null
        val time = json.optLong("airingAt", 0).takeIf { it > 0 } ?: return null
        return AnimeAiringEpisode(item, episode, time)
    }
}

internal class AnimeTodayLoader(private val scope: CoroutineScope,
    private val fetch: suspend (Long) -> List<AnimeAiringEpisode>,
    private val update: ((AnimeTodayState) -> AnimeTodayState) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis) {
    private var job: Job? = null
    fun load() {
        if (job?.isActive == true) return
        val now = clock()
        val start = AnimeToday.start(now)
        job = scope.launch {
            update { it.copy(dayStart = start, entries = AnimeToday.released(it.entries, now), loading = true, error = null) }
            try {
                val entries = fetch(now)
                ensureActive()
                // Previous days remain valid after midnight, but aged-out/future events do not.
                val completedAt = clock()
                update { AnimeTodayState(AnimeToday.start(completedAt), AnimeToday.released(entries, completedAt), loaded = true) }
            } catch (error: CancellationException) { update { it.copy(loading = false) }; throw error }
            catch (error: Exception) {
                update { it.copy(loading = false, error = (error as? AnimeCalendarException)?.userMessage()
                    ?: "No se pudieron actualizar las emisiones recientes. Inténtalo de nuevo.") }
            }
        }
    }
}

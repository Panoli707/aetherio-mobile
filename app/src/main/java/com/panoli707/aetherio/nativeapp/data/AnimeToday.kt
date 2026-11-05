package com.panoli707.aetherio.nativeapp.data

import java.util.Calendar
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
    fun start(nowMs: Long): Long = Calendar.getInstance(AnimeSeason.japan).apply {
        timeInMillis = nowMs
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis / 1000

    fun released(entries: List<AnimeAiringEpisode>, nowMs: Long): List<AnimeAiringEpisode> {
        val start = start(nowMs)
        return entries.filter { it.episode > 0 && it.airedAt >= start && it.airedAt < start + 86400 && it.airedAt <= nowMs / 1000 }
            .distinctBy { it.key }.sortedByDescending { it.airedAt }
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
            update { if (it.dayStart == start) it.copy(loading = true, error = null) else AnimeTodayState(start, loading = true) }
            try {
                val entries = fetch(now)
                ensureActive()
                // Never publish yesterday's response as today's after midnight.
                if (AnimeToday.start(clock()) == start) update { AnimeTodayState(start, entries, loaded = true) }
                else update { AnimeTodayState(AnimeToday.start(clock())) }
            } catch (error: CancellationException) { update { it.copy(loading = false) }; throw error }
            catch (error: Exception) {
                update { it.copy(loading = false, error = (error as? AnimeCalendarException)?.userMessage()
                    ?: "No se pudieron actualizar los episodios de hoy. Inténtalo de nuevo.") }
            }
        }
    }
}

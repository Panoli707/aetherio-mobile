package com.panoli707.aetherio.nativeapp.data

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import org.json.JSONObject

enum class WatchingCategory { ANIME, MOVIES_TV }

data class WatchingRequest(val category: WatchingCategory, val start: String, val end: String, val page: Int = 1) {
    init {
        require(WatchingDates.valid(start) && WatchingDates.valid(end) && start < end)
        require(WatchingDates.days(start, end).size <= 31)
        require(page in 1..500)
    }
}

/** end is exclusive; date-only releases must never acquire an invented midnight airing time. */
data class WatchingEvent(val item: MediaItem, val date: String, val at: Long? = null,
    val season: Int? = null, val episode: Int? = null, val premiere: Boolean = false) {
    val key: String get() = "${item.key}:$date:${season ?: 0}:${episode ?: 0}"
}

data class WatchingCalendarState(val request: WatchingRequest? = null, val events: List<WatchingEvent> = emptyList(),
    val loading: Boolean = false, val loaded: Boolean = false, val error: String? = null,
    val page: Int = 0, val hasMore: Boolean = false)

data class WatchingPage(val events: List<WatchingEvent>, val hasMore: Boolean = false)

object WatchingDates {
    private fun format(zone: TimeZone) = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = zone; isLenient = false }
    val utc: TimeZone get() = TimeZone.getTimeZone("UTC")
    fun valid(value: String): Boolean {
        if (!value.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}"))) return false
        val position = ParsePosition(0)
        return format(utc).parse(value, position) != null && position.index == value.length
    }
    fun date(ms: Long, zone: TimeZone = utc): String = format(zone).format(Date(ms))
    fun millis(value: String, zone: TimeZone = utc): Long = requireNotNull(format(zone).parse(value)).time
    fun shift(value: String, amount: Int, field: Int = Calendar.DAY_OF_MONTH): String {
        val calendar = Calendar.getInstance(utc).apply { timeInMillis = millis(value); add(field, amount) }
        return date(calendar.timeInMillis)
    }
    fun start(value: String, month: Boolean): String {
        val calendar = Calendar.getInstance(utc).apply { timeInMillis = millis(value) }
        if (month) calendar.set(Calendar.DAY_OF_MONTH, 1)
        else calendar.add(Calendar.DAY_OF_MONTH, -((calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7))
        return date(calendar.timeInMillis)
    }
    fun days(start: String, end: String): List<String> = buildList {
        var day = start
        while (day < end && size <= 31) { add(day); day = shift(day, 1) }
    }
    fun request(category: WatchingCategory, anchor: String, month: Boolean): WatchingRequest {
        val start = start(anchor, month)
        return WatchingRequest(category, start, shift(start, if (month) 1 else 7, if (month) Calendar.MONTH else Calendar.DAY_OF_MONTH))
    }
    fun normalize(events: List<WatchingEvent>, request: WatchingRequest): List<WatchingEvent> = events
        .filter { it.date >= request.start && it.date < request.end && it.item.isAnime == (request.category == WatchingCategory.ANIME) }
        .distinctBy { it.key }.sortedWith(compareBy<WatchingEvent> { it.date }.thenBy { it.at ?: Long.MAX_VALUE }.thenBy { it.item.title })

    fun movie(json: JSONObject): WatchingEvent? {
        if (json.optBoolean("adult") || isJapaneseAnime(json)) return null
        val date = CatalogJson.text(json, "release_date").takeIf(::valid) ?: return null
        return CatalogJson.item(json, MediaType.MOVIE, CatalogSource.TMDB)?.let { WatchingEvent(it, date, premiere = true) }
    }
    fun isJapaneseAnime(json: JSONObject): Boolean {
        val genres = json.optJSONArray("genre_ids")
        val detailed = json.optJSONArray("genres")
        val animation = (0 until (genres?.length() ?: 0)).any { genres?.optInt(it) == 16 } ||
            (0 until (detailed?.length() ?: 0)).any { detailed?.optJSONObject(it)?.optInt("id") == 16 }
        return json.optString("original_language") == "ja" && animation
    }
}

/** One loader per category: cancelling a range never lets its late response overwrite the next one. */
internal class WatchingCalendarLoader(private val scope: CoroutineScope,
    private val fetch: suspend (WatchingRequest) -> WatchingPage,
    private val update: (WatchingCalendarState) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis) {
    private var job: Job? = null
    private var active: WatchingRequest? = null
    private val cache = linkedMapOf<WatchingRequest, Pair<Long, WatchingCalendarState>>()
    private val firstPages = linkedMapOf<WatchingRequest, Pair<Long, WatchingCalendarState>>()

    fun resetToFirstPage() {
        job?.cancel()
        job = null
        val base = active?.copy(page = 1)
        active = null
        cache.clear()
        cache.putAll(firstPages)
        update(base?.let { firstPages[it]?.second } ?: WatchingCalendarState())
    }

    fun load(request: WatchingRequest, force: Boolean = false) {
        if (!force && active == request && job?.isActive == true) return
        job?.cancel()
        active = request
        val base = request.copy(page = 1)
        val cached = cache[base]
        val previous = cached?.second ?: WatchingCalendarState(request = base)
        val more = request.page > 1
        if (more && (!previous.hasMore || request.page != previous.page + 1)) return
        if (!more && !force && cached != null && clock() - cached.first in 0 until 5 * 60_000L) {
            update(previous); return
        }
        update(previous.copy(loading = true, error = null))
        job = scope.launch {
            try {
                val result = fetch(request)
                val events = WatchingDates.normalize((if (more) previous.events else emptyList()) + result.events, base)
                ensureActive()
                if (cache.size >= 8 && base !in cache) {
                    val oldest = cache.keys.first()
                    cache.remove(oldest)
                    firstPages.remove(oldest)
                }
                val completed = WatchingCalendarState(base, events, loaded = true, page = request.page, hasMore = result.hasMore)
                val cachedResult = clock() to completed
                cache[base] = cachedResult
                if (!more) firstPages[base] = cachedResult
                update(completed)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                ensureActive()
                update(previous.copy(loading = false,
                    error = (error as? AnimeCalendarException)?.userMessage() ?: "No se pudo actualizar la agenda. Inténtalo de nuevo."))
            }
        }
    }
}

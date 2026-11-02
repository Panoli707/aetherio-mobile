package com.panoli707.aetherio.nativeapp.data

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.text.SimpleDateFormat
import java.util.Date
import org.json.JSONObject

enum class AnimeWeekday(val label: String) {
    MONDAY("Lunes"), TUESDAY("Martes"), WEDNESDAY("Miércoles"), THURSDAY("Jueves"),
    FRIDAY("Viernes"), SATURDAY("Sábado"), SUNDAY("Domingo")
}

data class AnimeSeason(val year: Int, val code: String) {
    val label: String get() = "${when (code) { "WINTER" -> "Invierno"; "SPRING" -> "Primavera"; "SUMMER" -> "Verano"; else -> "Otoño" }} $year"
    companion object {
        val japan: TimeZone get() = TimeZone.getTimeZone("Asia/Tokyo")
        fun current(nowMs: Long = System.currentTimeMillis()): AnimeSeason = Calendar.getInstance(japan).apply { timeInMillis = nowMs }.let {
            AnimeSeason(it.get(Calendar.YEAR), listOf("WINTER", "SPRING", "SUMMER", "FALL")[it.get(Calendar.MONTH) / 3])
        }
        fun weekday(epochSeconds: Long): AnimeWeekday = Calendar.getInstance(japan).apply { timeInMillis = epochSeconds * 1000 }.let {
            AnimeWeekday.entries[(it.get(Calendar.DAY_OF_WEEK) + 5) % 7]
        }
    }
}

data class SeasonalAnime(val item: MediaItem, val weekday: AnimeWeekday?, val nextEpisode: Int?, val nextAiringAt: Long?)
data class AnimeSeasonState(
    val season: AnimeSeason = AnimeSeason.current(),
    val entries: List<SeasonalAnime> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val loaded: Boolean = false
)

internal object AnimeSeasonProtocol {
    fun item(json: JSONObject): MediaItem? {
        val id = json.optInt("id", 0).takeIf { it > 0 } ?: return null
        val title = json.optJSONObject("title")?.optString("romaji")?.takeIf { it.isNotBlank() && it != "null" }
            ?: return null
        val season = json.optString("season")
        val year = json.optInt("seasonYear", 0)
        val label = if (year in 1900..2200 && season in listOf("WINTER", "SPRING", "SUMMER", "FALL")) AnimeSeason(year, season).label else ""
        val genres = json.optJSONArray("genres")
        return MediaItem(id.toString(), MediaType.SERIES, title.take(500), CatalogSource.ANILIST,
            posterUrl = CatalogJson.image(json.optJSONObject("coverImage")?.optString("large")),
            backdropUrl = CatalogJson.image(json.optString("bannerImage")),
            overview = json.optString("description").takeUnless { it == "null" }.orEmpty()
                .replace(Regex("<[^>]*>"), " ").replace("&amp;", "&").replace("&#039;", "'").trim().take(10000),
            year = year.takeIf { it > 0 }?.toString().orEmpty(),
            score = json.optDouble("averageScore", 0.0).takeIf { it.isFinite() }?.div(10) ?: 0.0,
            genres = (0 until (genres?.length() ?: 0)).mapNotNull { genres?.optString(it) }.take(20), isAnime = true,
            episodeCount = json.optInt("episodes", 0).takeIf { it in 1..100000 }, animeSeasonLabel = label)
    }

    fun seasonal(json: JSONObject, season: AnimeSeason, nowSeconds: Long): SeasonalAnime? {
        if (json.optString("status") != "RELEASING" || json.optString("season") != season.code ||
            json.optInt("seasonYear") != season.year || json.optBoolean("isAdult")) return null
        val item = item(json) ?: return null
        val next = json.optJSONObject("nextAiringEpisode")
        val nextTime = next?.optLong("airingAt", 0)?.takeIf { it > nowSeconds && it < nowSeconds + 370 * 86400L }
        val nextEpisode = next?.optInt("episode", 0)?.takeIf { it > 0 }
        return SeasonalAnime(item, nextTime?.let(AnimeSeason::weekday), nextEpisode, nextTime)
    }

    fun detail(json: JSONObject, nowSeconds: Long): MediaDetail {
        val media = item(json) ?: throw IllegalArgumentException("Anime no valido")
        val schedule = json.optJSONObject("airingSchedule")
        if (schedule?.optJSONObject("pageInfo")?.optBoolean("hasNextPage") == true) throw IllegalArgumentException("Horario incompleto")
        val nodes = schedule?.optJSONArray("nodes")
        val released = (0 until (nodes?.length() ?: 0)).mapNotNull { index ->
            val node = nodes?.optJSONObject(index) ?: return@mapNotNull null
            val episode = node.optInt("episode", 0)
            val time = node.optLong("airingAt", 0)
            episode.takeIf { it > 0 && time in 1..nowSeconds }
        }.distinct().sorted()
        val episodes = if (released.isNotEmpty()) released else if (json.optString("status") == "FINISHED")
            (1..(media.episodeCount ?: 0).coerceAtMost(1000)).toList() else emptyList()
        val dates = (0 until (nodes?.length() ?: 0)).mapNotNull { index ->
            val node = nodes?.optJSONObject(index) ?: return@mapNotNull null
            val time = node.optLong("airingAt", 0)
            if (time !in 1..nowSeconds) return@mapNotNull null
            node.optInt("episode") to SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = AnimeSeason.japan }.format(Date(time * 1000))
        }.toMap()
        val trailer = json.optJSONObject("trailer")?.takeIf { it.optString("site").equals("youtube", true) }
            ?.let { DetailLinks.trailer(it.optString("id"), "Tráiler · ${media.title}") }
        val edges = json.optJSONObject("characters")?.optJSONArray("edges")
        val cast = (0 until (edges?.length() ?: 0)).mapNotNull { index ->
            val edge = edges?.optJSONObject(index) ?: return@mapNotNull null
            val actor = edge.optJSONArray("voiceActors")?.optJSONObject(0) ?: return@mapNotNull null
            val name = actor.optJSONObject("name")?.optString("full")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            CastMember(name, edge.optJSONObject("node")?.optJSONObject("name")?.optString("full").orEmpty(), CatalogJson.image(actor.optJSONObject("image")?.optString("large")))
        }.distinctBy { it.name }.take(12)
        val relatedEdges = json.optJSONObject("relations")?.optJSONArray("edges")
        val related = (0 until (relatedEdges?.length() ?: 0)).mapNotNull { index ->
            relatedEdges?.optJSONObject(index)?.optJSONObject("node")?.takeIf { it.optString("format") in listOf("TV", "TV_SHORT", "ONA", "OVA") }?.let(::item)
        }.filter { it.key != media.key }.distinctBy { it.key }.take(15)
        val facts = buildList {
            json.optString("status").takeIf { it.isNotBlank() && it != "null" }?.let { add(DetailFact("Estado", DetailMetadata.status(it))) }
            json.optString("countryOfOrigin").takeIf { it.matches(Regex("[A-Z]{2}")) }?.let {
                add(DetailFact("País", Locale.Builder().setRegion(it).build().getDisplayCountry(Locale.forLanguageTag("es"))))
            }
            val studios = json.optJSONObject("studios")?.optJSONArray("nodes")
            val names = (0 until (studios?.length() ?: 0)).mapNotNull { studios?.optJSONObject(it)?.optString("name") }.filter { it.isNotBlank() }
            if (names.isNotEmpty()) add(DetailFact("Producción", names.distinct().take(8).joinToString(", ")))
            media.episodeCount?.let { add(DetailFact("Episodios", it.toString())) }
        }
        return MediaDetail(media, json.optInt("duration", 0).takeIf { it > 0 }, cast = cast,
            seasons = listOf(SeasonInfo(1, media.animeSeasonLabel.ifBlank { "Temporada de anime" }, media.episodeCount ?: episodes.size)),
            episodes = episodes.map { EpisodeItem(it, 1, "Episodio $it", airedOn = dates[it].orEmpty()) }, selectedSeason = 1,
            trailers = listOfNotNull(trailer), facts = facts, related = related)
    }
}

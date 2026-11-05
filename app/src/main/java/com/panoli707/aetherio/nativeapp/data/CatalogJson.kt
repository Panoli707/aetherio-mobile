package com.panoli707.aetherio.nativeapp.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object CatalogJson {
    private fun imdb(value: String): String? = value.takeIf { it.matches(Regex("tt[0-9]{5,12}")) }
    fun text(json: JSONObject, key: String): String = when (val value = json.opt(key)) {
        is String -> value.trim().takeUnless { it == "null" }.orEmpty()
        is Number -> value.toString()
        else -> ""
    }
    private fun objects(array: JSONArray?): List<JSONObject> =
        (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }
    private fun strings(array: JSONArray?): List<String> =
        (0 until (array?.length() ?: 0)).mapNotNull { (array?.opt(it) as? String)?.trim()?.takeIf(String::isNotEmpty) }
    fun image(value: String?): String? = value?.takeIf { url ->
        runCatching { URI(url).let { it.scheme.equals("https", true) && !it.host.isNullOrEmpty() && it.rawUserInfo == null } }.getOrDefault(false)
    }
    private fun tmdbImage(path: String, size: String): String? =
        path.takeIf { it.startsWith("/") && !it.startsWith("//") && !it.contains('\\') }
            ?.let { image("https://image.tmdb.org/t/p/$size$it") }
    fun logos(json: JSONObject): List<String> = objects(json.optJSONArray("logos"))
        .sortedWith(compareBy<JSONObject> { when (text(it, "iso_639_1")) { "es" -> 0; "en" -> 1; "" -> 2; else -> 3 } }
            .thenByDescending { minOf(it.optInt("width", 0), 2000) }.thenByDescending { it.optDouble("vote_average", 0.0) })
        .mapNotNull { tmdbImage(text(it, "file_path"), "w500") }.distinct().take(8)
    fun logo(json: JSONObject): String? = logos(json).firstOrNull()

    /** A missing default poster does not mean that images.posters has no usable vertical artwork. */
    fun posterFromImages(images: JSONObject): String? = objects(images.optJSONArray("posters"))
        .filter { image ->
            val ratio = image.optDouble("aspect_ratio", 0.0).takeIf { it > 0 }
                ?: (image.optDouble("width", 0.0) / image.optDouble("height", 1.0))
            ratio in 0.35..0.95
        }
        .sortedWith(compareBy<JSONObject> { when (text(it, "iso_639_1")) { "es" -> 0; "en" -> 1; "" -> 2; else -> 3 } }
            .thenByDescending { it.optInt("width", 0) }.thenByDescending { it.optDouble("vote_average", 0.0) })
        .firstNotNullOfOrNull { tmdbImage(text(it, "file_path"), "original") }

    fun seasonPoster(json: JSONObject): String? = tmdbImage(text(json, "poster_path"), "original")
        ?: posterFromImages(json.optJSONObject("images") ?: JSONObject())

    /** Language-neutral portrait candidates; the language tag does NOT prove that text is absent. */
    fun portraitArtwork(json: JSONObject): String? = objects(json.optJSONArray("posters"))
        .filter { text(it, "iso_639_1").isEmpty() && it.optInt("width") >= 1080 &&
            it.optDouble("aspect_ratio", 0.0) in 0.55..0.85 }
        .sortedWith(compareByDescending<JSONObject> { it.optDouble("vote_average", 0.0) }
            .thenByDescending { it.optInt("width") })
        .firstNotNullOfOrNull { tmdbImage(text(it, "file_path"), "original") }

    fun portraitAlternatives(json: JSONObject): List<String> = objects(json.optJSONArray("posters"))
        .filter { it.optDouble("aspect_ratio", 0.0) in 0.45..0.85 && it.optInt("width") >= 500 }
        .sortedWith(compareBy<JSONObject> { if (text(it, "iso_639_1").isEmpty()) 0 else 1 }
            .thenByDescending { minOf(it.optInt("width"), 2000) }.thenByDescending { it.optDouble("vote_average", 0.0) })
        .mapNotNull { tmdbImage(text(it, "file_path"), "w780") }.distinct().take(8)

    fun item(json: JSONObject, type: MediaType, source: CatalogSource, anime: Boolean = false): MediaItem? {
        val id = text(json, "id")
        if (!id.matches(if (source == CatalogSource.TMDB) Regex("[0-9]+") else Regex("tt[0-9]+"))) return null
        val tmdb = source == CatalogSource.TMDB
        val title = text(json, if (tmdb && type == MediaType.MOVIE) "title" else "name")
            .ifEmpty { text(json, "original_title").ifEmpty { text(json, "original_name") } }
        if (title.isEmpty()) return null
        val date = if (tmdb) text(json, if (type == MediaType.MOVIE) "release_date" else "first_air_date")
            else text(json, "year").ifEmpty { text(json, "releaseInfo").ifEmpty { text(json, "released") } }
        val score = text(json, if (tmdb) "vote_average" else "imdbRating").toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
        return MediaItem(id, type, title, source,
            if (tmdb) tmdbImage(text(json, "poster_path"), "w500")
                ?: posterFromImages(json.optJSONObject("images") ?: JSONObject()) else image(text(json, "poster")),
            if (tmdb) tmdbImage(text(json, "backdrop_path"), "w1280") else image(text(json, "background")),
            if (tmdb) logo(json.optJSONObject("images") ?: JSONObject()) else image(text(json, "logo")),
            text(json, if (tmdb) "overview" else "description"), Regex("[0-9]{4}").find(date)?.value.orEmpty(), score,
            if (tmdb) objects(json.optJSONArray("genres")).mapNotNull { text(it, "name").takeIf(String::isNotEmpty) }
            else strings(json.optJSONArray("genres")), anime,
            if (tmdb) imdb(text(json.optJSONObject("external_ids") ?: JSONObject(), "imdb_id")) else imdb(id),
            voteCount = if (tmdb) json.optInt("vote_count", 0).takeIf { it > 0 } else null,
            certification = if (tmdb) DetailMetadata.certification(json, type) else null,
            portraitArtworkUrl = if (tmdb) portraitArtwork(json.optJSONObject("images") ?: JSONObject()) else null,
            logoAlternatives = if (tmdb) logos(json.optJSONObject("images") ?: JSONObject()).drop(1) else emptyList(),
            portraitAlternatives = if (tmdb) portraitAlternatives(json.optJSONObject("images") ?: JSONObject()) else emptyList())
    }
    fun catalog(json: JSONObject, type: MediaType, source: CatalogSource, anime: Boolean = false): List<MediaItem> =
        objects(json.optJSONArray(if (source == CatalogSource.TMDB) "results" else "metas"))
            .mapNotNull { item(it, type, source, anime) }.distinctBy { it.key }
    fun search(json: JSONObject): List<MediaItem> = objects(json.optJSONArray("results")).mapNotNull {
        val type = when (text(it, "media_type")) { "movie" -> MediaType.MOVIE; "tv" -> MediaType.SERIES; else -> return@mapNotNull null }
        item(it, type, CatalogSource.TMDB)
    }.distinctBy { it.key }
    private fun merged(base: MediaItem, json: JSONObject): MediaItem {
        val titleKey = if (base.source == CatalogSource.TMDB && base.type == MediaType.MOVIE) "title" else "name"
        val data = JSONObject(json.toString()).apply { if (text(this, titleKey).isEmpty()) put(titleKey, base.title) }
        val parsed = item(data, base.type, base.source, base.isAnime) ?: return base
        return parsed.copy(posterUrl = parsed.posterUrl ?: base.posterUrl, backdropUrl = parsed.backdropUrl ?: base.backdropUrl,
            logoUrl = parsed.logoUrl ?: base.logoUrl, overview = parsed.overview.ifEmpty { base.overview },
            year = parsed.year.ifEmpty { base.year }, score = parsed.score.takeIf { it > 0 } ?: base.score,
             genres = parsed.genres.ifEmpty { base.genres }, imdbId = parsed.imdbId ?: base.imdbId,
              episodeCount = json.optInt("number_of_episodes", 0).takeIf { it > 0 } ?: base.episodeCount,
               seasonHint = base.seasonHint, animeEntryId = base.animeEntryId, seriesNumbering = base.seriesNumbering,
                voteCount = parsed.voteCount ?: base.voteCount, certification = parsed.certification ?: base.certification,
                 portraitArtworkUrl = parsed.portraitArtworkUrl ?: base.portraitArtworkUrl,
                 posterTitlePresence = if (parsed.posterUrl == null || parsed.posterUrl == base.posterUrl) base.posterTitlePresence else ArtworkTitlePresence.UNKNOWN,
                 portraitTitlePresence = if (parsed.portraitArtworkUrl == null || parsed.portraitArtworkUrl == base.portraitArtworkUrl) base.portraitTitlePresence else ArtworkTitlePresence.UNKNOWN,
                 backdropTitlePresence = if (parsed.backdropUrl == null || parsed.backdropUrl == base.backdropUrl) base.backdropTitlePresence else ArtworkTitlePresence.UNKNOWN,
                 logoAlternatives = (parsed.logoAlternatives + listOfNotNull(base.logoUrl) + base.logoAlternatives)
                      .filter { it != (parsed.logoUrl ?: base.logoUrl) }.distinct().take(8),
                  portraitAlternatives = (parsed.portraitAlternatives + base.portraitAlternatives).distinct().take(4))
    }
    fun episodes(json: JSONObject, season: Int, cinemeta: Boolean = false): List<EpisodeItem> =
        objects(json.optJSONArray(if (cinemeta) "videos" else "episodes")).mapNotNull {
            val number = it.optInt(if (cinemeta) "episode" else "episode_number", 0)
            if (number <= 0 || (cinemeta && it.optInt("season", 0) != season)) return@mapNotNull null
            EpisodeItem(number, season, text(it, if (cinemeta) "title" else "name").ifEmpty { text(it, "name") }.ifEmpty { "Episodio $number" },
                text(it, "overview").ifEmpty { if (cinemeta) text(it, "description") else "" },
                 if (cinemeta) image(text(it, "thumbnail")) else tmdbImage(text(it, "still_path"), "w500"),
                 text(it, if (cinemeta) "released" else "air_date").take(10).let { date ->
                     date.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) && date <= SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) },
                 DetailMetadata.date(text(it, if (cinemeta) "released" else "air_date")),
                 it.optInt("runtime", 0).takeIf { minutes -> minutes in 1..1440 })
        }.distinctBy { it.number }.sortedBy { it.number }
    fun detail(base: MediaItem, json: JSONObject): MediaDetail {
        val tmdb = base.source == CatalogSource.TMDB
        val seasons = if (tmdb) objects(json.optJSONArray("seasons")).mapNotNull {
            val number = it.optInt("season_number", 0)
            if (number <= 0) null else SeasonInfo(number, text(it, "name").ifEmpty { "Temporada $number" }, it.optInt("episode_count", 0).coerceAtLeast(0))
        } else objects(json.optJSONArray("videos")).filter { it.optInt("season", 0) > 0 && it.optInt("episode", 0) > 0 }
            .groupBy { it.optInt("season") }.map { (number, _) -> SeasonInfo(number, "Temporada $number", episodes(json, number, true).size) }
        val ordered = seasons.distinctBy { it.number }.sortedBy { it.number }
        val selected = ordered.firstOrNull()?.number
        val cast = if (tmdb) objects(json.optJSONObject("credits")?.optJSONArray("cast")).mapNotNull {
            text(it, "name").takeIf(String::isNotEmpty)?.let { name -> CastMember(name, text(it, "character"), tmdbImage(text(it, "profile_path"), "w500")) }
        } else strings(json.optJSONArray("cast")).map { CastMember(it, "") }
        val runtime = if (tmdb) json.optInt("runtime", 0).takeIf { it > 0 }
            ?: json.optJSONArray("episode_run_time")?.let { times -> (0 until times.length()).map { times.optInt(it) }.firstOrNull { it > 0 } }
            else Regex("[0-9]+").find(text(json, "runtime"))?.value?.toIntOrNull()?.takeIf { it > 0 }
        val detail = MediaDetail(merged(base, json), runtime, cast.take(12), ordered,
             if (!tmdb && selected != null) episodes(json, selected, true) else emptyList(), selected)
        return if (tmdb) DetailMetadata.enrich(detail, json) else detail.copy(trailers = DetailMetadata.cinemetaTrailers(json))
    }
    fun encode(items: List<MediaItem>): String = JSONArray().apply {
        items.forEach { item -> put(JSONObject().apply {
            put("id", item.id); put("type", item.type.name); put("source", item.source.name); put("name", item.title)
            put("poster", image(item.posterUrl)); put("background", image(item.backdropUrl)); put("logo", image(item.logoUrl))
            put("description", item.overview); put("year", item.year); put("imdbRating", item.score)
            put("genres", JSONArray(item.genres)); put("anime", item.isAnime)
            put("imdb", item.imdbId)
            put("episodeCount", item.episodeCount); put("animeSeasonLabel", item.animeSeasonLabel)
             put("seasonHint", item.seasonHint); put("animeEntryId", item.animeEntryId); put("seriesNumbering", item.seriesNumbering)
              put("voteCount", item.voteCount); put("certification", item.certification)
               put("portraitArtwork", image(item.portraitArtworkUrl))
               put("posterTitlePresence", item.posterTitlePresence.name); put("portraitTitlePresence", item.portraitTitlePresence.name)
               put("backdropTitlePresence", item.backdropTitlePresence.name)
                put("logoAlternatives", JSONArray(item.logoAlternatives.mapNotNull(::image).take(8)))
                put("portraitAlternatives", JSONArray(item.portraitAlternatives.mapNotNull(::image).take(4)))
        }) }
    }.toString()
    fun decode(raw: String): List<MediaItem> = runCatching {
        objects(JSONArray(raw)).mapNotNull { json ->
            val type = MediaType.entries.find { it.name == text(json, "type") } ?: return@mapNotNull null
            val source = CatalogSource.entries.find { it.name == text(json, "source") } ?: return@mapNotNull null
            val id = text(json, "id")
            val valid = id.matches(if (source != CatalogSource.CINEMETA) Regex("[0-9]+") else Regex("tt[0-9]+"))
            if (!valid || text(json, "name").isEmpty()) return@mapNotNull null
            MediaItem(id, type, text(json, "name"), source, image(text(json, "poster")), image(text(json, "background")),
                image(text(json, "logo")), text(json, "description"), text(json, "year"),
                json.optDouble("imdbRating", 0.0).takeIf { it.isFinite() } ?: 0.0, strings(json.optJSONArray("genres")), json.optBoolean("anime"), imdb(text(json, "imdb")),
                json.optInt("episodeCount", 0).takeIf { it in 1..100000 }, text(json, "animeSeasonLabel").take(40),
                json.optInt("seasonHint", 0).takeIf { it in 1..1000 }, text(json, "animeEntryId").takeIf { it.matches(Regex("[0-9]{1,12}")) },
                json.optBoolean("seriesNumbering"), json.optInt("voteCount", 0).takeIf { it > 0 },
                text(json, "certification").takeIf(String::isNotBlank), image(text(json, "portraitArtwork")),
                titlePresence(json, "posterTitlePresence"), titlePresence(json, "portraitTitlePresence"), titlePresence(json, "backdropTitlePresence"),
                strings(json.optJSONArray("logoAlternatives")).mapNotNull(::image).distinct().take(8),
                strings(json.optJSONArray("portraitAlternatives")).mapNotNull(::image).distinct().take(4))
        }.distinctBy { it.key }
    }.getOrDefault(emptyList())
    private fun titlePresence(json: JSONObject, key: String): ArtworkTitlePresence =
        ArtworkTitlePresence.entries.firstOrNull { it.name == text(json, key) } ?: ArtworkTitlePresence.UNKNOWN
}

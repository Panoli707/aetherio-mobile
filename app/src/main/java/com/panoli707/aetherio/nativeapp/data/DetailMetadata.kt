package com.panoli707.aetherio.nativeapp.data

import java.net.URI
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** External actions are restricted to real trailer and TMDB metadata pages, never media embeds. */
internal object DetailLinks {
    private val youtubeId = Regex("[A-Za-z0-9_-]{11}")
    fun trailer(id: String, title: String): MediaTrailer? = id.takeIf(youtubeId::matches)?.let {
        MediaTrailer(it, title.ifBlank { "Tráiler" }.take(200), "https://www.youtube.com/watch?v=$it", "https://i.ytimg.com/vi/$it/hqdefault.jpg")
    }
    /** Only explicit direct media from catalogue metadata; never resolve/extract a YouTube page. */
    fun directTrailer(url: String, title: String, thumbnail: String = ""): MediaTrailer? = runCatching {
        val safe = com.panoli707.aetherio.nativeapp.player.MediaUrlPolicy.validate(url)
        val uri = URI(safe)
        require(uri.rawFragment == null && uri.path.substringAfterLast('.').lowercase(Locale.ROOT) in
            setOf("mp4", "m4v", "webm", "mov", "m3u8", "mpd"))
        require(uri.host.lowercase(Locale.ROOT) !in setOf("youtube.com", "www.youtube.com", "youtu.be", "www.youtube-nocookie.com"))
        val id = java.security.MessageDigest.getInstance("SHA-256").digest(safe.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        MediaTrailer(id, title.ifBlank { "Tráiler" }.take(200), safe,
            CatalogJson.image(thumbnail).orEmpty(), TrailerKind.DIRECT)
    }.getOrNull()
    fun validate(raw: String?): String? = runCatching {
        if (raw == null || raw.length > 2048 || raw.any(Char::isISOControl)) return null
        val uri = URI(raw)
        if (uri.scheme != "https" || uri.rawUserInfo != null || uri.rawFragment != null || uri.port !in listOf(-1, 443)) return null
        when (uri.host) {
            "www.youtube.com", "youtube.com" -> if (uri.rawPath == "/watch" && uri.rawQuery?.startsWith("v=") == true && youtubeId.matches(uri.rawQuery.removePrefix("v="))) raw else null
            "www.themoviedb.org" -> if (Regex("/(?:movie|tv)/[0-9]+/watch").matches(uri.rawPath) &&
                (uri.rawQuery == null || Regex("locale=[A-Z]{2}").matches(uri.rawQuery))) raw
                else if (Regex("/review/[A-Za-z0-9_-]{1,80}").matches(uri.rawPath) && uri.rawQuery == null) raw else null
            else -> null
        }
    }.getOrNull()
}

internal object DetailMetadata {
    private fun objects(array: JSONArray?) = (0 until (array?.length() ?: 0)).mapNotNull { array?.optJSONObject(it) }
    private fun text(json: JSONObject, key: String) = CatalogJson.text(json, key)
    private fun image(path: String) = path.takeIf { it.startsWith("/") && !it.startsWith("//") && '\\' !in it }
        ?.let { "https://image.tmdb.org/t/p/w185$it" }
    fun cinemetaTrailers(json: JSONObject): List<MediaTrailer> =
        objects(json.optJSONArray("trailerStreams")).take(24).mapNotNull { value ->
            val title = text(value, "title").ifBlank { "Tráiler" }
            DetailLinks.directTrailer(text(value, "url"), title, text(value, "thumbnail"))
                ?: DetailLinks.trailer(text(value, "ytId"), title)
        }.distinctBy { it.id }.take(12)
    fun plain(value: String): String = value.replace(Regex("<[^>]*>"), " ").replace("&amp;", "&").replace("&#039;", "'").trim()
    fun date(value: String): String = value.take(10).takeIf { it.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) }.orEmpty()
    fun status(value: String): String = when (value) {
        "Returning Series", "RELEASING" -> "En emisión"
        "Ended", "FINISHED" -> "Finalizada"
        "Canceled", "CANCELLED" -> "Cancelada"
        "Released" -> "Estrenada"
        "Post Production" -> "Posproducción"
        "In Production" -> "En producción"
        "Planned", "NOT_YET_RELEASED" -> "Anunciada"
        "HIATUS" -> "En pausa"
        else -> value
    }

    fun enrich(detail: MediaDetail, json: JSONObject): MediaDetail {
        val trailers = objects(json.optJSONObject("videos")?.optJSONArray("results"))
            .filter { text(it, "site").equals("YouTube", true) && text(it, "type") in listOf("Trailer", "Teaser") }
            .sortedWith(compareBy<JSONObject> { if (it.optBoolean("official")) 0 else 1 }.thenBy { if (text(it, "iso_639_1") == "es") 0 else 1 })
            .mapNotNull { DetailLinks.trailer(text(it, "key"), text(it, "name")) }.distinctBy { it.id }.take(12)
        val facts = buildList {
            text(json, "status").takeIf(String::isNotBlank)?.let { add(DetailFact("Estado", status(it))) }
            val countries = objects(json.optJSONArray("production_countries")).map { country ->
                val iso = text(country, "iso_3166_1")
                if (iso.matches(Regex("[A-Z]{2}"))) Locale.Builder().setRegion(iso).build().getDisplayCountry(Locale.forLanguageTag("es")) else text(country, "name")
            }.filter(String::isNotBlank).distinct()
            if (countries.isNotEmpty()) add(DetailFact("País", countries.joinToString(", ")))
            val companies = objects(json.optJSONArray("production_companies")).map { text(it, "name") }.filter(String::isNotBlank).distinct()
            if (companies.isNotEmpty()) add(DetailFact("Producción", companies.take(8).joinToString(", ")))
            detail.item.episodeCount?.let { add(DetailFact("Episodios", it.toString())) }
        }
        val regions = json.optJSONObject("watch/providers")?.optJSONObject("results")
        val availability = regions?.keys()?.asSequence()?.toList().orEmpty().filter { it.matches(Regex("[A-Z]{2}")) }.sorted().mapNotNull { code ->
            val region = regions?.optJSONObject(code) ?: return@mapNotNull null
            val platforms = listOf("flatrate" to "Suscripción", "free" to "Gratis", "ads" to "Con anuncios", "rent" to "Alquiler", "buy" to "Compra")
                .flatMap { (key, label) -> objects(region.optJSONArray(key)).mapNotNull { provider ->
                    val name = text(provider, "provider_name").takeIf(String::isNotBlank) ?: return@mapNotNull null
                    val id = provider.optInt("provider_id", 0).takeIf { it > 0 } ?: return@mapNotNull null
                    WatchPlatform(id, name, image(text(provider, "logo_path")), label)
                } }.distinctBy { "${it.id}:${it.offer}" }.take(50)
            WatchRegion(code, DetailLinks.validate(text(region, "link")), platforms).takeIf { platforms.isNotEmpty() }
        }
        val reviews = objects(json.optJSONObject("reviews")?.optJSONArray("results")).mapNotNull { review ->
            val id = text(review, "id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val content = plain(text(review, "content")).take(30000).takeIf(String::isNotBlank) ?: return@mapNotNull null
            MediaReview(id, text(review, "author").ifBlank { "Usuario de TMDB" }.take(100), content,
                date(text(review, "created_at")), DetailLinks.validate(text(review, "url")))
        }.distinctBy { it.id }.take(20)
        val related = CatalogJson.catalog(json.optJSONObject("recommendations") ?: JSONObject(), detail.item.type, CatalogSource.TMDB)
            .filter { it.key != detail.item.key }.take(20)
        return detail.copy(trailers = trailers, facts = facts, availability = availability, reviews = reviews, related = related,
            voteCount = json.optInt("vote_count", 0).takeIf { it > 0 } ?: detail.item.voteCount,
            certification = certification(json, detail.item.type) ?: detail.item.certification)
    }

    fun certification(json: JSONObject, type: MediaType): String? {
        val ratings = if (type == MediaType.SERIES) objects(json.optJSONObject("content_ratings")?.optJSONArray("results"))
            .mapNotNull { value -> text(value, "rating").takeIf(String::isNotBlank)?.let { text(value, "iso_3166_1") to it } }
        else objects(json.optJSONObject("release_dates")?.optJSONArray("results")).flatMap { country ->
            objects(country.optJSONArray("release_dates")).mapNotNull { value -> text(value, "certification").takeIf(String::isNotBlank)?.let { text(country, "iso_3166_1") to it } }
        }
        val rating = ratings.sortedBy { when (it.first) { "ES" -> 0; "US" -> 1; else -> 2 } }.firstOrNull()
        return rating?.let { "${it.second} (${it.first})" }
    }
}

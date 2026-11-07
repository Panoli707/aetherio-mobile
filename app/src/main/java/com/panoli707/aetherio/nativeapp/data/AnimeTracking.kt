package com.panoli707.aetherio.nativeapp.data

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import org.json.JSONObject

enum class AnimeService(val label: String, val route: String) { ANILIST("AniList", "anilist"), MAL("MyAnimeList", "mal") }
enum class AnimeStatus(val label: String, val aniList: String, val mal: String) {
    WATCHING("Viendo", "CURRENT", "watching"), COMPLETED("Completados", "COMPLETED", "completed"),
    PLANNED("Pendientes", "PLANNING", "plan_to_watch"), PAUSED("En pausa", "PAUSED", "on_hold"),
    DROPPED("Abandonados", "DROPPED", "dropped"), REWATCHING("Volviendo a ver", "REPEATING", "watching")
}
data class AnimeEntry(val id: Int, val aniListId: Int?, val malId: Int?, val title: String,
    val poster: String? = null, val banner: String? = null, val movie: Boolean = false,
    val episodes: Int = 0, val progress: Int = 0, val score: Double = 0.0,
    val status: AnimeStatus? = null, val favorite: Boolean = false) {
    fun item(): MediaItem? = aniListId?.takeIf { it > 0 }?.let {
        MediaItem(it.toString(), if (movie) MediaType.MOVIE else MediaType.SERIES, title,
            CatalogSource.ANILIST, posterUrl = poster, backdropUrl = banner, isAnime = true,
            episodeCount = episodes.takeIf { count -> count > 0 })
    }
}
data class AnimeAccount(val id: Int, val name: String, val avatar: String?, val banner: String? = null)
data class AnimeConnection(val account: AnimeAccount? = null, val entries: List<AnimeEntry> = emptyList(),
    val busy: Boolean = false, val error: String? = null, val updatedAt: Long = 0, val authorizationRequired: Boolean = false)
data class AnimeDraft(val service: AnimeService, val entry: AnimeEntry)
data class AnimeTrackingState(val connections: Map<AnimeService, AnimeConnection> = AnimeService.entries.associateWith { AnimeConnection() }, val draft: AnimeDraft? = null)

internal data class OAuthPending(val service: AnimeService, val state: String, val verifier: String, val createdAt: Long) {
    fun encode() = JSONObject().put("service", service.name).put("state", state).put("verifier", verifier).put("created", createdAt).toString()
    companion object {
        fun decode(raw: String): OAuthPending = JSONObject(raw).let {
            OAuthPending(AnimeService.valueOf(it.getString("service")), it.getString("state"), it.getString("verifier"), it.getLong("created"))
        }
    }
}
internal data class OAuthSession(val access: String, val refresh: String = "", val expiresAt: Long) {
    fun encode() = JSONObject().put("access", access).put("refresh", refresh).put("expires", expiresAt).toString()
    companion object {
        fun decode(raw: String): OAuthSession = JSONObject(raw).let {
            OAuthSession(it.getString("access"), it.optString("refresh"), it.getLong("expires"))
        }
    }
}

/** Pure OAuth protocol validation: exact redirect, one-use state, expiry and duplicate rejection. */
internal object AnimeOAuth {
    fun redirect(service: AnimeService) = "aetherio://oauth/${service.route}"
    fun pending(service: AnimeService, now: Long): OAuthPending {
        fun random() = ByteArray(48).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        return OAuthPending(service, random(), random(), now)
    }
    fun authorize(clientId: String, pending: OAuthPending): String {
        require(clientId.matches(if (pending.service == AnimeService.ANILIST) Regex("[0-9]{1,12}") else Regex("[a-fA-F0-9]{32}")))
        val params = linkedMapOf("client_id" to clientId, "redirect_uri" to redirect(pending.service), "state" to pending.state,
            "response_type" to if (pending.service == AnimeService.ANILIST) "token" else "code")
        if (pending.service == AnimeService.MAL) {
            params["code_challenge"] = pending.verifier
            params["code_challenge_method"] = "plain" // MAL currently supports only plain PKCE.
        }
        val base = if (pending.service == AnimeService.ANILIST) "https://anilist.co/api/v2/oauth/authorize" else "https://myanimelist.net/v1/oauth2/authorize"
        return base + "?" + params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
    }
    fun service(raw: String): AnimeService? = runCatching {
        require(raw.length <= 32_768)
        val uri = URI(raw)
        AnimeService.entries.firstOrNull { uri.scheme == "aetherio" && uri.rawAuthority == "oauth" && uri.rawPath == "/${it.route}" }
    }.getOrNull()
    fun callback(raw: String, pending: OAuthPending, now: Long): Map<String, String> {
        require(service(raw) == pending.service && now - pending.createdAt in 0..600_000)
        val uri = URI(raw)
        val payload = if (pending.service == AnimeService.ANILIST) {
            require(uri.rawQuery == null); uri.rawFragment
        } else { require(uri.rawFragment == null); uri.rawQuery }
        require(!payload.isNullOrEmpty())
        val pairs = payload.split('&').map { part ->
            val pair = part.split('=', limit = 2)
            require(pair.size == 2)
            URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
        }
        require(pairs.map { it.first }.distinct().size == pairs.size)
        val params = pairs.toMap()
        require(MessageDigest.isEqual(params["state"].orEmpty().toByteArray(), pending.state.toByteArray()))
        require("error" !in params) { "AUTH_DENIED" }
        val credential = params[if (pending.service == AnimeService.ANILIST) "access_token" else "code"].orEmpty()
        require(credential.length in 1..16_384 && credential.all { it in '!'..'~' })
        if (pending.service == AnimeService.ANILIST) require(params["token_type"].equals("bearer", true))
        return params
    }
}

internal object AnimeSyncRules {
    /** Exact ID+offset conversion. Never guess a season/cour from a title or the most recently opened entry. */
    fun episode(target: WatchTarget, index: SeriesIndex): Pair<Int, Int>? {
        val item = target.item
        if (!item.isAnime && item.source != CatalogSource.ANILIST) return null
        if (item.source == CatalogSource.ANILIST) {
            val id = item.id.toIntOrNull()?.takeIf { it > 0 } ?: return null
            return if (item.type == MediaType.MOVIE) id to 1 else
                if (target.season == 1 && target.episode != null) id to target.episode else null
        }
        if (item.source != CatalogSource.TMDB || target.season == null || target.episode == null) return null
        val parts = index.parts(item)
        val candidates = if (item.seriesNumbering) parts.filter { it.season == target.season } else parts.filter { it.tmdbSeason == target.season }
        val external = target.episode + if (item.seriesNumbering) index.baseOffset(parts, target.season) else 0
        val part = candidates.filter { external > it.offset }.maxByOrNull { it.offset } ?: return null
        return part.animeId.toInt() to (external - part.offset)
    }
    fun contiguous(entry: AnimeEntry, history: List<WatchProgress>, index: SeriesIndex): Int {
        val seen = history.filter { it.watched }.mapNotNull { episode(it.target, index) }
            .filter { it.first == entry.aniListId }.map { it.second }.toSet()
        var count = 0
        while (count + 1 in seen && count < 100_000) count++
        return if (entry.episodes > 0) count.coerceAtMost(entry.episodes) else count
    }
}

package com.panoli707.aetherio.nativeapp.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import org.json.JSONArray
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class AnimeSeasonRepository(client: OkHttpClient = OkHttpClient()) {
    private val client = client.newBuilder().cookieJar(CookieJar.NO_COOKIES).followRedirects(false).followSslRedirects(false)
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()
    private val mutex = Mutex()
    private var cachedSeason: AnimeSeason? = null
    private var cachedAt = 0L
    private var cachedEntries = emptyList<SeasonalAnime>()
    private val details = mutableMapOf<String, Pair<Long, MediaDetail>>()

    suspend fun season(season: AnimeSeason, nowMs: Long = System.currentTimeMillis()): List<SeasonalAnime> = mutex.withLock {
        if (cachedSeason == season && nowMs - cachedAt in 0 until 60 * 60 * 1000L) return@withLock cachedEntries
        val entries = mutableListOf<SeasonalAnime>()
        var page = 1
        do {
            val data = request("""query SeasonalAnime(${'$'}page:Int!,${'$'}season:MediaSeason!,${'$'}year:Int!){Page(page:${'$'}page,perPage:50){pageInfo{hasNextPage}media(type:ANIME,season:${'$'}season,seasonYear:${'$'}year,status:RELEASING,isAdult:false,sort:POPULARITY_DESC){$FIELDS nextAiringEpisode{airingAt episode}}}}""",
                JSONObject().put("page", page).put("season", season.code).put("year", season.year)).getJSONObject("Page")
            val media = data.getJSONArray("media")
            if (media.length() > 50) throw failure()
            for (index in 0 until media.length()) AnimeSeasonProtocol.seasonal(media.getJSONObject(index), season, nowMs / 1000)?.let(entries::add)
            val more = data.getJSONObject("pageInfo").getBoolean("hasNextPage")
            if (!more) break
            if (++page > 10) throw failure() // Never present a silently truncated season as complete.
        } while (true)
        cachedEntries = entries.distinctBy { it.item.key }
        cachedSeason = season
        cachedAt = nowMs
        cachedEntries
    }

    suspend fun detail(item: MediaItem, nowMs: Long = System.currentTimeMillis()): MediaDetail = mutex.withLock {
        require(item.source == CatalogSource.ANILIST && item.id.matches(Regex("[0-9]{1,10}")))
        details[item.id]?.takeIf { nowMs - it.first in 0 until 15 * 60 * 1000L }?.let { return@withLock it.second }
        val nodes = JSONArray()
        var page = 1
        var media: JSONObject
        do {
            media = request("""query AnimeDetail(${'$'}id:Int!,${'$'}page:Int!){Media(id:${'$'}id,type:ANIME){$FIELDS duration countryOfOrigin trailer{id site} studios{nodes{name}} characters(perPage:12){edges{node{name{full}} voiceActors(language:JAPANESE){name{full} image{large}}}} relations{edges{node{id format title{romaji} coverImage{large} bannerImage season seasonYear status episodes}}} airingSchedule(perPage:50,page:${'$'}page){nodes{episode airingAt}pageInfo{hasNextPage}}}}""",
                JSONObject().put("id", item.id.toInt()).put("page", page)).getJSONObject("Media")
            if (media.getInt("id").toString() != item.id) throw failure()
            val schedule = media.getJSONObject("airingSchedule")
            val batch = schedule.getJSONArray("nodes")
            if (batch.length() > 50) throw failure()
            for (index in 0 until batch.length()) nodes.put(batch.getJSONObject(index))
            if (!schedule.getJSONObject("pageInfo").getBoolean("hasNextPage")) break
            if (++page > 10) throw failure()
        } while (true)
        media.put("airingSchedule", JSONObject().put("nodes", nodes).put("pageInfo", JSONObject().put("hasNextPage", false)))
        AnimeSeasonProtocol.detail(media, nowMs / 1000).also {
            if (details.size >= 100) details.clear()
            details[item.id] = nowMs to it
        }
    }

    private suspend fun request(query: String, variables: JSONObject): JSONObject = suspendCancellableCoroutine { continuation ->
        val body = JSONObject().put("query", query).put("variables", variables).toString().toRequestBody("application/json".toMediaType())
        // This client never receives the TMDB client, credentials or playback headers.
        val call = client.newCall(Request.Builder().url("https://graphql.anilist.co").post(body).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(failure()) }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching { response.use {
                    if (!it.isSuccessful) throw failure()
                    val body = it.body ?: throw failure()
                    if (body.contentLength() > 2 * 1024 * 1024) throw failure()
                    val source = body.source()
                    source.request(2 * 1024 * 1024L + 1)
                    if (source.buffer.size > 2 * 1024 * 1024) throw failure()
                    val json = JSONObject(source.readUtf8())
                    if (json.has("errors")) throw failure()
                    json.getJSONObject("data")
                } }
                if (continuation.isActive) result.fold({ continuation.resume(it) }, { continuation.resumeWithException(failure()) })
            }
        })
    }

    private fun failure() = IOException("No se pudo consultar el calendario de anime")
    companion object {
        private const val FIELDS = "id title{romaji english} description(asHtml:false) coverImage{large} bannerImage episodes season seasonYear status isAdult averageScore genres"
    }
}

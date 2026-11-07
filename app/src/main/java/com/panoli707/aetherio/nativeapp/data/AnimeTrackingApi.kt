package com.panoli707.aetherio.nativeapp.data

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import org.json.JSONArray
import org.json.JSONObject

internal class AnimeApiException(val status: Int, val retrySeconds: Long = 0) : IOException("ANIME_API_$status")

/** Account HTTP never uses metadata proxies, redirects, cookies or the provider's HTTP client. */
internal class AnimeTrackingApi(
    private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .connectTimeout(12, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS).build(),
    private val graphUrl: String = "https://graphql.anilist.co",
    private val malUrl: String = "https://api.myanimelist.net/v2",
    private val malTokenUrl: String = "https://myanimelist.net/v1/oauth2/token",
    private val spacingMs: Long = 2200,
    private val brokerUrl: String = AnimeOAuth.BROKER,
) {
    private val throttle = Mutex()
    private var nextAt = 0L
    private suspend fun request(builder: Request.Builder, token: String? = null): JSONObject = throttle.withLock {
        delay((nextAt - System.currentTimeMillis()).coerceAtLeast(0))
        nextAt = System.currentTimeMillis() + spacingMs
        builder.header("Accept", "application/json")
        if (token != null) builder.header("Authorization", "Bearer $token")
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(builder.build())
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(IOException("No se pudo contactar el servicio de anime"))
                }
                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching { response.use {
                        val retry = if (it.code == 429) (it.header("Retry-After")?.toLongOrNull() ?: 60).coerceIn(1, 3600) else 0
                        if (retry > 0) nextAt = System.currentTimeMillis() + retry * 1000
                        if (!it.isSuccessful) throw AnimeApiException(it.code, retry)
                        val source = requireNotNull(it.body).source()
                        source.request(8 * 1024 * 1024L + 1)
                        check(source.buffer.size <= 8 * 1024 * 1024L)
                        val json = JSONObject(source.readUtf8())
                        if (json.optJSONArray("errors")?.length()?.let { count -> count > 0 } == true) {
                            val code = json.getJSONArray("errors").optJSONObject(0)?.optInt("status", 400) ?: 400
                            throw AnimeApiException(code)
                        }
                        json
                    } }
                    if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
                }
            })
        }
    }
    private suspend fun graph(query: String, variables: JSONObject = JSONObject(), token: String? = null): JSONObject =
        request(Request.Builder().url(graphUrl).post(JSONObject().put("query", query).put("variables", variables).toString()
            .toRequestBody("application/json".toMediaType())), token).getJSONObject("data")

    suspend fun aniListAuthorization(clientId: String, pending: OAuthPending): String {
        require(pending.service == AnimeService.ANILIST)
        val body = JSONObject().put("state", pending.state).put("challenge", AnimeOAuth.challenge(pending))
        val json = request(Request.Builder().url("$brokerUrl/start").post(body.toString().toRequestBody("application/json".toMediaType())))
        // The server may never choose arbitrary browser destinations or change client/state/return.
        val expected = AnimeOAuth.authorize(clientId, pending)
        val actual = json.getString("authorizationUrl").toHttpUrl()
        val wanted = expected.toHttpUrl()
        require(actual.scheme == wanted.scheme && actual.host == wanted.host && actual.port == 443 &&
            actual.encodedPath == wanted.encodedPath && actual.username.isEmpty() && actual.password.isEmpty() && actual.fragment == null &&
            actual.queryParameterNames == wanted.queryParameterNames && actual.queryParameterNames.all { key ->
                actual.queryParameterValues(key) == wanted.queryParameterValues(key)
            })
        return actual.toString()
    }

    suspend fun malToken(clientId: String, pending: OAuthPending?, codeOrRefresh: String): OAuthSession {
        val form = FormBody.Builder().add("client_id", clientId)
        if (pending == null) form.add("grant_type", "refresh_token").add("refresh_token", codeOrRefresh)
        else form.add("grant_type", "authorization_code").add("code", codeOrRefresh)
            .add("code_verifier", pending.verifier).add("redirect_uri", AnimeOAuth.redirect(AnimeService.MAL))
        val json = request(Request.Builder().url(malTokenUrl).post(form.build()))
        require(json.optString("token_type").equals("bearer", true))
        val token = json.getString("access_token")
        val refresh = json.getString("refresh_token")
        require(token.length in 1..16_384 && refresh.length in 1..16_384)
        val seconds = json.getLong("expires_in").also { require(it in 1..31_536_000) }
        return OAuthSession(token, refresh, System.currentTimeMillis() + seconds * 1000)
    }

    suspend fun account(service: AnimeService, token: String): AnimeAccount = if (service == AnimeService.ANILIST) {
        val user = graph("query { Viewer { id name avatar { large } bannerImage } }", token = token).getJSONObject("Viewer")
        AnimeAccount(user.getInt("id"), user.getString("name").take(100), image(user.optJSONObject("avatar")?.optString("large")), image(user.optString("bannerImage")))
    } else {
        val user = request(Request.Builder().url("$malUrl/users/@me?fields=id,name,picture"), token)
        AnimeAccount(user.getInt("id"), user.getString("name").take(100), image(user.optString("picture")))
    }

    suspend fun list(service: AnimeService, account: AnimeAccount, token: String): List<AnimeEntry> =
        if (service == AnimeService.ANILIST) aniList(account, token) else malList(token)

    private suspend fun aniList(account: AnimeAccount, token: String): List<AnimeEntry> {
        val query = """query(${'$'}id:Int!,${'$'}chunk:Int!) { MediaListCollection(userId:${'$'}id,type:ANIME,chunk:${'$'}chunk,perChunk:500) { hasNextChunk lists { entries { status progress score(format:POINT_100) media { $MEDIA } } } } }"""
        val entries = linkedMapOf<Int, AnimeEntry>()
        var chunk = 1
        do {
            val collection = graph(query, JSONObject().put("id", account.id).put("chunk", chunk), token).getJSONObject("MediaListCollection")
            val lists = collection.getJSONArray("lists")
            for (i in 0 until lists.length()) {
                val rows = lists.getJSONObject(i).getJSONArray("entries")
                for (j in 0 until rows.length()) {
                    val row = rows.getJSONObject(j)
                    val media = row.getJSONObject("media")
                    val entry = aniEntry(media).copy(status = AnimeStatus.entries.firstOrNull { it.aniList == row.optString("status") },
                        progress = row.optInt("progress").coerceIn(0, 100_000), score = row.optDouble("score", 0.0).coerceIn(0.0, 100.0))
                    entries[entry.id] = entry
                }
            }
            if (!collection.optBoolean("hasNextChunk")) break
            check(chunk++ < 60) { "LIST_TOO_LARGE" }
        } while (true)
        var page = 1
        do {
            val favorites = graph("""query(${'$'}id:Int!,${'$'}page:Int!) { User(id:${'$'}id) { favourites { anime(page:${'$'}page,perPage:50) { pageInfo { hasNextPage } nodes { $MEDIA } } } } }""",
                JSONObject().put("id", account.id).put("page", page), token).getJSONObject("User").getJSONObject("favourites").getJSONObject("anime")
            val nodes = favorites.getJSONArray("nodes")
            for (i in 0 until nodes.length()) {
                val entry = aniEntry(nodes.getJSONObject(i))
                entries[entry.id] = (entries[entry.id] ?: entry).copy(favorite = true)
            }
            val more = favorites.getJSONObject("pageInfo").optBoolean("hasNextPage")
            if (!more) break
            check(page++ < 200) { "LIST_TOO_LARGE" }
        } while (true)
        return entries.values.toList()
    }
    private suspend fun malList(token: String): List<AnimeEntry> {
        val entries = mutableListOf<AnimeEntry>()
        var offset = 0
        do {
            val json = request(Request.Builder().url("$malUrl/users/@me/animelist?fields=list_status,num_episodes,media_type,main_picture&limit=1000&offset=$offset&nsfw=true"), token)
            val data = json.getJSONArray("data")
            for (i in 0 until data.length()) entries.add(malEntry(data.getJSONObject(i)))
            if (json.optJSONObject("paging")?.optString("next").isNullOrEmpty()) break
            check(data.length() > 0 && offset < 19_000) { "LIST_TOO_LARGE" }
            offset += data.length() // Never follow an API-supplied URL with the account token.
        } while (true)
        // Public catalogue identifiers only, not a transfer of another service's account/list data.
        val mapping = mutableMapOf<Int, Int>()
        for (ids in entries.mapNotNull { it.malId }.distinct().chunked(50)) {
            try {
                val media = graph("""query(${'$'}ids:[Int]) { Page(perPage:50) { media(idMal_in:${'$'}ids,type:ANIME) { id idMal } } }""",
                    JSONObject().put("ids", JSONArray(ids))).getJSONObject("Page").getJSONArray("media")
                for (i in 0 until media.length()) media.getJSONObject(i).let { mapping[it.getInt("idMal")] = it.getInt("id") }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { break /* Optional catalogue mapping must not hold the MAL list behind repeated rate limits. */ }
        }
        return entries.distinctBy { it.id }.map { it.copy(aniListId = mapping[it.malId]) }
    }
    suspend fun catalog(service: AnimeService, aniListId: Int): AnimeEntry {
        require(aniListId > 0)
        val entry = aniEntry(graph("""query(${'$'}id:Int!) { Media(id:${'$'}id,type:ANIME) { $MEDIA } }""",
            JSONObject().put("id", aniListId)).getJSONObject("Media"))
        return if (service == AnimeService.ANILIST) entry else entry.copy(id = requireNotNull(entry.malId) { "NO_MAL_ID" }, favorite = false)
    }
    suspend fun current(service: AnimeService, token: String, entry: AnimeEntry): AnimeEntry {
        return if (service == AnimeService.ANILIST) {
            val media = graph("""query(${'$'}id:Int!) { Media(id:${'$'}id,type:ANIME) { isFavourite mediaListEntry { status progress score(format:POINT_100) } } }""",
                JSONObject().put("id", entry.id), token).getJSONObject("Media")
            applyAniStatus(entry.copy(favorite = media.optBoolean("isFavourite", entry.favorite)), media.optJSONObject("mediaListEntry"))
        } else {
            val row = request(Request.Builder().url("$malUrl/anime/${entry.id}?fields=my_list_status"), token).optJSONObject("my_list_status")
            applyMalStatus(entry, row)
        }
    }
    suspend fun update(service: AnimeService, token: String, entry: AnimeEntry, status: AnimeStatus, progress: Int, score: Double): AnimeEntry {
        require(progress in 0..100_000 && (entry.episodes <= 0 || progress <= entry.episodes))
        require(score.isFinite() && score in 0.0..100.0)
        if (service == AnimeService.ANILIST) {
            val row = graph("""mutation(${'$'}id:Int!,${'$'}status:MediaListStatus!,${'$'}progress:Int!,${'$'}score:Int!) { SaveMediaListEntry(mediaId:${'$'}id,status:${'$'}status,progress:${'$'}progress,scoreRaw:${'$'}score) { status progress score(format:POINT_100) } }""",
                JSONObject().put("id", entry.id).put("status", status.aniList).put("progress", progress).put("score", score.toInt()), token).getJSONObject("SaveMediaListEntry")
            return applyAniStatus(entry, row)
        } else {
            val body = FormBody.Builder().add("status", status.mal).add("is_rewatching", (status == AnimeStatus.REWATCHING).toString())
                .add("num_watched_episodes", progress.toString()).add("score", kotlin.math.round(score / 10).toInt().toString()).build()
            val row = request(Request.Builder().url("$malUrl/anime/${entry.id}/my_list_status").patch(body), token)
            return applyMalStatus(entry, row)
        }
    }
    companion object {
        private const val MEDIA = "id idMal title { userPreferred } coverImage { large } bannerImage format episodes isFavourite"
        private fun image(raw: String?) = CatalogJson.image(raw)
        private fun applyAniStatus(entry: AnimeEntry, row: JSONObject?): AnimeEntry = entry.copy(
            status = row?.optString("status")?.let { value -> AnimeStatus.entries.firstOrNull { it.aniList == value } },
            progress = row?.optInt("progress")?.coerceIn(0, 100_000) ?: 0, score = row?.optDouble("score", 0.0)?.coerceIn(0.0, 100.0) ?: 0.0)
        private fun applyMalStatus(entry: AnimeEntry, row: JSONObject?): AnimeEntry = entry.copy(
            status = if (row?.optBoolean("is_rewatching") == true) AnimeStatus.REWATCHING else row?.optString("status")?.let { value -> AnimeStatus.entries.firstOrNull { it.mal == value } },
            progress = row?.optInt("num_episodes_watched")?.coerceIn(0, 100_000) ?: 0, score = (row?.optInt("score")?.coerceIn(0, 10) ?: 0) * 10.0)
        internal fun aniEntry(media: JSONObject): AnimeEntry = AnimeEntry(media.getInt("id"), media.getInt("id"),
            media.optInt("idMal").takeIf { it > 0 }, media.getJSONObject("title").getString("userPreferred").take(500),
            image(media.optJSONObject("coverImage")?.optString("large")), image(media.optString("bannerImage")),
            media.optString("format") == "MOVIE", media.optInt("episodes").coerceIn(0, 100_000), favorite = media.optBoolean("isFavourite"))
        internal fun malEntry(raw: JSONObject): AnimeEntry {
            val node = raw.getJSONObject("node")
            val status = raw.optJSONObject("list_status") ?: JSONObject()
            return AnimeEntry(node.getInt("id"), null, node.getInt("id"), node.getString("title").take(500),
                image(node.optJSONObject("main_picture")?.optString("large")) ?: image(node.optJSONObject("main_picture")?.optString("medium")),
                movie = node.optString("media_type") == "movie", episodes = node.optInt("num_episodes").coerceIn(0, 100_000),
                progress = status.optInt("num_episodes_watched").coerceIn(0, 100_000), score = status.optInt("score").coerceIn(0, 10) * 10.0,
                status = if (status.optBoolean("is_rewatching")) AnimeStatus.REWATCHING else AnimeStatus.entries.firstOrNull { it.mal == status.optString("status") })
        }
    }
}

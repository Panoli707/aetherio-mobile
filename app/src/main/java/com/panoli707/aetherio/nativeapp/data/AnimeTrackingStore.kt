package com.panoli707.aetherio.nativeapp.data

import android.content.Context
import com.panoli707.aetherio.nativeapp.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal class AnimeTrackingStore(context: Context, private val api: AnimeTrackingApi = AnimeTrackingApi(), storageName: String = "aetherio_anime_accounts") {
    private val secrets = PrivateStore(context, storageName)
    private val locks = AnimeService.entries.associateWith { Mutex() }
    private val mutableState = MutableStateFlow(AnimeTrackingState())
    val state = mutableState.asStateFlow()
    private fun change(service: AnimeService, block: (AnimeConnection) -> AnimeConnection) {
        mutableState.update { current -> current.copy(connections = current.connections + (service to block(current.connections.getValue(service)))) }
    }
    private fun clientId(service: AnimeService) = if (service == AnimeService.ANILIST) BuildConfig.ANILIST_CLIENT_ID else BuildConfig.MAL_CLIENT_ID
    suspend fun restore() = withContext(Dispatchers.IO) {
        AnimeService.entries.forEach { service -> locks.getValue(service).withLock {
            try {
                val raw = secrets.read("list_${service.name}")
                val session = secrets.read("token_${service.name}")?.let(OAuthSession::decode)
                if (raw != null && session != null) change(service) { AnimeAccountCodec.decode(raw).copy(
                    authorizationRequired = service == AnimeService.ANILIST && System.currentTimeMillis() >= session.expiresAt) }
            } catch (_: Exception) { change(service) { it.copy(error = "No se pudo abrir la conexión guardada. Puedes desconectarla y volver a vincularla.") } }
        } }
    }
    suspend fun begin(service: AnimeService): String = withContext(Dispatchers.IO) { locks.getValue(service).withLock {
        val pending = AnimeOAuth.pending(service, System.currentTimeMillis())
        secrets.write("pending_${service.name}", pending.encode())
        change(service) { it.copy(error = null) }
        AnimeOAuth.authorize(clientId(service), pending)
    } }
    suspend fun finish(raw: String) {
        val service = AnimeOAuth.service(raw) ?: return
        operation(service) {
            val pending = secrets.read("pending_${service.name}")?.let(OAuthPending::decode) ?: error("AUTH_STATE")
            val params = AnimeOAuth.callback(raw, pending, System.currentTimeMillis())
            secrets.write("pending_${service.name}", null) // Consume before exchanging credentials; a callback cannot be replayed.
            val session = if (service == AnimeService.ANILIST) {
                val seconds = params["expires_in"]?.toLongOrNull()?.also { require(it in 1..31_536_000) } ?: 31_536_000
                OAuthSession(params.getValue("access_token"), expiresAt = System.currentTimeMillis() + seconds * 1000)
            } else api.malToken(clientId(service), pending, params.getValue("code"))
            val account = api.account(service, session.access)
            secrets.write("token_${service.name}", session.encode())
            val connected = AnimeConnection(account, busy = true)
            secrets.write("list_${service.name}", AnimeAccountCodec.encode(connected))
            change(service) { connected }
            load(service, session.access)
        }
    }
    suspend fun disconnect(service: AnimeService) = withContext(Dispatchers.IO) { locks.getValue(service).withLock {
        try {
            listOf("token", "pending", "list").forEach { secrets.write("${it}_${service.name}", null) }
            change(service) { AnimeConnection() }
            mutableState.update { if (it.draft?.service == service) it.copy(draft = null) else it }
        } catch (_: Exception) { change(service) { it.copy(error = "No se pudo borrar la conexión. Vuelve a intentarlo.") } }
    } }
    private suspend fun access(service: AnimeService, forceRefresh: Boolean = false): String {
        val session = secrets.read("token_${service.name}")?.let(OAuthSession::decode) ?: error("AUTH_REQUIRED")
        if (!forceRefresh && System.currentTimeMillis() + 60_000 < session.expiresAt) return session.access
        if (service == AnimeService.ANILIST || session.refresh.isEmpty()) error("AUTH_REQUIRED")
        val refreshed = try { api.malToken(clientId(service), null, session.refresh) }
            catch (e: AnimeApiException) { if (e.status in listOf(400, 401)) error("AUTH_REQUIRED") else throw e }
        secrets.write("token_${service.name}", refreshed.encode()) // Persist rotation before using the new access token.
        return refreshed.access
    }
    private suspend fun <T> authenticated(service: AnimeService, block: suspend (String) -> T): T {
        return try { block(access(service)) }
        catch (e: AnimeApiException) {
            if (e.status != 401 || service != AnimeService.MAL) throw e
            block(access(service, forceRefresh = true)) // At most one refresh; never loop on authorization failure.
        }
    }
    private suspend fun load(service: AnimeService, token: String) {
        val account = api.account(service, token)
        val entries = api.list(service, account, token)
        val connected = AnimeConnection(account, entries, updatedAt = System.currentTimeMillis())
        secrets.write("list_${service.name}", AnimeAccountCodec.encode(connected))
        change(service) { connected }
    }
    suspend fun refresh(service: AnimeService) = operation(service) { authenticated(service) { load(service, it) } }
    suspend fun edit(service: AnimeService, aniListId: Int) = operation(service) {
        require(state.value.connections.getValue(service).account != null)
        val entry = api.catalog(service, aniListId)
        val actual = authenticated(service) { api.current(service, it, entry) }
        mutableState.update { it.copy(draft = AnimeDraft(service, actual)) }
    }
    fun dismissDraft() { mutableState.update { it.copy(draft = null) } }
    suspend fun update(service: AnimeService, entry: AnimeEntry, status: AnimeStatus, progress: Int, score: Double) = operation(service) {
        val current = state.value.connections.getValue(service)
        require(current.account != null && entry.id > 0)
        val actual = authenticated(service) { api.current(service, it, entry) }
        if (actual.status != entry.status || actual.progress != entry.progress || actual.score != entry.score) {
            val corrected = current.copy(entries = current.entries.map { if (it.id == entry.id) actual else it })
            secrets.write("list_${service.name}", AnimeAccountCodec.encode(corrected))
            change(service) { corrected }
            error("REMOTE_CHANGED")
        }
        // Read-before-write catches external edits; the services do not expose a conditional-write API.
        val saved = authenticated(service) { api.update(service, it, actual, status, progress, score) }
        val changed = current.copy(entries = listOf(saved) + current.entries.filterNot { it.id == entry.id }, updatedAt = System.currentTimeMillis())
        secrets.write("list_${service.name}", AnimeAccountCodec.encode(changed))
        change(service) { changed }
    }
    private suspend fun operation(service: AnimeService, block: suspend () -> Unit) = withContext(Dispatchers.IO) {
        locks.getValue(service).withLock {
            change(service) { it.copy(busy = true, error = null) }
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val message = when {
                    e is AnimeApiException && e.status == 429 -> "El servicio ha limitado las consultas. Reintenta dentro de ${e.retrySeconds.coerceAtLeast(60)} segundos."
                    e is AnimeApiException && e.status in listOf(401, 403) || e.message == "AUTH_REQUIRED" -> "La autorización ha caducado o no es válida. Vuelve a conectar tu cuenta."
                    e.message == "AUTH_DENIED" -> "Has cancelado la autorización. No se ha vinculado ninguna cuenta nueva."
                    e.message == "REMOTE_CHANGED" -> "La lista remota cambió desde la última lectura. No se ha sobrescrito: revisa los nuevos datos y confirma otra vez."
                    e.message == "NO_MAL_ID" -> "Esta entrega no tiene una equivalencia MyAnimeList confirmada. No se enviará a otro título."
                    e is IllegalArgumentException || e.message == "AUTH_STATE" -> "No se pudo validar el retorno de autorización. Inicia la conexión otra vez y comprueba el URI registrado."
                    else -> "No se pudo completar la operación. Los datos anteriores se conservan; comprueba la red y vuelve a intentarlo."
                }
                change(service) { it.copy(error = message, authorizationRequired = it.authorizationRequired || e is AnimeApiException && e.status == 401 || e.message == "AUTH_REQUIRED") }
            } finally { change(service) { it.copy(busy = false) } }
        }
    }
}

internal object AnimeAccountCodec {
    fun encode(connection: AnimeConnection): String {
        val account = requireNotNull(connection.account)
        return JSONObject().put("schema", 1).put("id", account.id).put("name", account.name).put("avatar", account.avatar)
            .put("banner", account.banner).put("updated", connection.updatedAt).put("entries", JSONArray().apply {
                connection.entries.forEach { entry -> put(JSONObject().put("id", entry.id).put("al", entry.aniListId).put("mal", entry.malId)
                    .put("title", entry.title).put("poster", entry.poster).put("banner", entry.banner).put("movie", entry.movie)
                    .put("episodes", entry.episodes).put("progress", entry.progress).put("score", entry.score)
                    .put("status", entry.status?.name).put("favorite", entry.favorite)) }
            }).toString()
    }
    fun decode(raw: String): AnimeConnection {
        val json = JSONObject(raw)
        require(json.getInt("schema") == 1)
        val array = json.getJSONArray("entries")
        require(array.length() <= 30_000)
        return AnimeConnection(AnimeAccount(json.getInt("id"), json.getString("name"), CatalogJson.image(json.optString("avatar")), CatalogJson.image(json.optString("banner"))),
            (0 until array.length()).map { i -> array.getJSONObject(i).let {
                AnimeEntry(it.getInt("id"), it.optInt("al").takeIf { id -> id > 0 }, it.optInt("mal").takeIf { id -> id > 0 },
                    it.getString("title"), CatalogJson.image(it.optString("poster")), CatalogJson.image(it.optString("banner")), it.optBoolean("movie"),
                    it.getInt("episodes"), it.getInt("progress"), it.getDouble("score"),
                    it.optString("status").takeIf(String::isNotEmpty)?.let(AnimeStatus::valueOf), it.optBoolean("favorite"))
            } }, updatedAt = json.getLong("updated"))
    }
}

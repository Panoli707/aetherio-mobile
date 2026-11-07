package com.panoli707.aetherio.nativeapp.downloads

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.StreamKey
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.work.*
import com.panoli707.aetherio.nativeapp.data.*
import com.panoli707.aetherio.nativeapp.player.*
import com.panoli707.aetherio.nativeapp.player.torrent.*
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class DownloadStage(val label: String) {
    QUEUED("En cola · esperando condiciones"), PREPARING("Preparando"), DOWNLOADING("Descargando"),
    PAUSED("En pausa"), NEEDS_FILE("Selecciona un archivo"), COMPLETE("Disponible sin conexión"), FAILED("No se pudo descargar")
}
data class DownloadFileChoice(val index: Int, val name: String, val size: Long)
data class MediaDownload(val id: String, val target: WatchTarget, val provider: String,
    val stage: DownloadStage = DownloadStage.QUEUED, val bytes: Long = 0, val total: Long = -1,
    val error: String? = null, val files: List<DownloadFileChoice> = emptyList()) {
    val label: String get() = target.item.title + (target.episode?.let { " · T${target.season} E$it" } ?: "")
    val fraction: Float? get() = if (total > 0) (bytes.toDouble() / total).coerceIn(0.0, 1.0).toFloat() else null
}

/** Media3 expects its canonical HLS MIME, not every equivalent value used by HTTP servers. */
internal fun adaptiveDownloadMime(contentType: String?): String? = when (contentType?.substringBefore(';')?.trim()?.lowercase(java.util.Locale.ROOT)) {
    "application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl" -> VideoFormat.HLS.mimeType
    "application/dash+xml" -> VideoFormat.DASH.mimeType
    else -> null
}

internal data class DownloadSource(val url: String = "", val format: VideoFormat = VideoFormat.AUTO,
    val headers: Map<String, String> = emptyMap(), val desktopHttp: Boolean = false,
    val providerHeaders: Boolean = false, val torrent: TorrentRequest? = null) {
    fun encode(): String = JSONObject().put("url", url).put("format", format.name).put("headers", JSONObject(headers))
        .put("desktop", desktopHttp).put("providerHeaders", providerHeaders).put("torrent", torrent?.encode()).toString()
    companion object {
        fun from(source: StreamSource): DownloadSource = if (source.isTorrent) DownloadSource(torrent = TorrentRequestParser.fromSource(source))
        else DownloadSource(SourcePlayback.httpUrl(source), source.format, PlaybackHeaders.validate(source.headers, source.allowProviderHeaders),
            source.allowDesktopHttp, source.allowProviderHeaders)
        fun decode(raw: String): DownloadSource {
            require(raw.length <= 128 * 1024)
            val json = JSONObject(raw)
            if (json.has("torrent")) return DownloadSource(torrent = TorrentRequest.decode(json.getString("torrent")))
            val headers = json.getJSONObject("headers").let { obj -> obj.keys().asSequence().associateWith { obj.getString(it) } }
            val desktop = json.optBoolean("desktop")
            return DownloadSource(MediaUrlPolicy.validate(json.getString("url"), desktop), VideoFormat.valueOf(json.getString("format")),
                PlaybackHeaders.validate(headers, json.optBoolean("providerHeaders")), desktop, json.optBoolean("providerHeaders"))
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
internal class MediaDownloads(private val context: Context) {
    private val prefs = context.getSharedPreferences("aetherio_media_downloads", Context.MODE_PRIVATE)
    private val privateStore = PrivateStore(context, "aetherio_download_sources")
    private val database by lazy { StandaloneDatabaseProvider(context) }
    private val storedIndex = prefs.getString("index", "[]").orEmpty()
    private val mutableEntries = MutableStateFlow(DownloadCodec.decode(storedIndex))
    val storageFailure = mutableEntries.value.isEmpty() && !runCatching { JSONArray(storedIndex).length() == 0 }.getOrDefault(false)
    val entries = mutableEntries.asStateFlow()
    private val active = mutableMapOf<String, Job>()
    private val leases = mutableSetOf<String>()
    val queueLock = kotlinx.coroutines.sync.Mutex()
    private var lastProgressAt = 0L
    private var lastProgressPersistedAt = 0L
    @Synchronized private fun save(records: List<MediaDownload>) {
        check(!storageFailure) { "DOWNLOAD_INDEX_UNREADABLE" }
        check(prefs.edit().putString("index", DownloadCodec.encode(records)).commit())
        mutableEntries.value = records
    }
    @Synchronized fun update(id: String, block: (MediaDownload) -> MediaDownload) {
        if (entries.value.none { it.id == id }) return
        save(entries.value.map { if (it.id == id) block(it) else it })
    }
    @Synchronized fun progress(id: String, bytes: Long, total: Long) {
        if (entries.value.firstOrNull { it.id == id }?.stage == DownloadStage.PAUSED) return
        check(directory(id).usableSpace >= RESERVE) { "DISK_SPACE" }
        val now = System.currentTimeMillis()
        if (now - lastProgressAt < 800) return
        lastProgressAt = now
        val previous = entries.value.firstOrNull { it.id == id } ?: return
        val updated = entries.value.map { if (it.id == id) it.copy(stage = DownloadStage.DOWNLOADING, bytes = bytes.coerceAtLeast(0), total = total) else it }
        if (previous.stage != DownloadStage.DOWNLOADING || now - lastProgressPersistedAt >= 15_000) {
            save(updated); lastProgressPersistedAt = now
        } else mutableEntries.value = updated // UI stays responsive without rewriting a whole queue every second.
    }
    @Synchronized fun enqueue(target: WatchTarget, source: StreamSource): Boolean {
        check(!storageFailure) { "DOWNLOAD_INDEX_UNREADABLE" }
        require(WatchProgressRules.valid(target))
        if (entries.value.any { it.target.key == target.key }) return false
        require(entries.value.size < 2000) { "DOWNLOAD_LIMIT" }
        val id = UUID.randomUUID().toString()
        privateStore.write("source_$id", DownloadSource.from(source).encode())
        val compact = target.copy(item = target.item.copy(logoAlternatives = emptyList(), portraitAlternatives = emptyList(),
            backdropAlternatives = emptyList(), artworkTitles = emptyList()))
        save(entries.value + MediaDownload(id, compact, source.providerName.take(100)))
        schedule()
        return true
    }
    private fun schedule() {
        val work = OneTimeWorkRequestBuilder<MediaDownloadWorker>().setConstraints(Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED).setRequiresStorageNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS).build()
        // One queue worker drains all records. Cancelling one item never cancels dependent downloads.
        WorkManager.getInstance(context).enqueueUniqueWork(QUEUE, ExistingWorkPolicy.APPEND_OR_REPLACE, work)
    }
    @Synchronized fun resume(id: String, fileIndex: Int? = null) {
        val record = entries.value.firstOrNull { it.id == id } ?: return
        if (record.stage !in listOf(DownloadStage.PAUSED, DownloadStage.FAILED, DownloadStage.NEEDS_FILE)) return
        if (fileIndex != null) {
            require(record.files.any { it.index == fileIndex })
            val spec = source(id)
            privateStore.write("source_$id", spec.copy(torrent = requireNotNull(spec.torrent).copy(fileIndex = fileIndex)).encode())
        } else if (record.stage == DownloadStage.NEEDS_FILE) return
        update(id) { it.copy(stage = DownloadStage.QUEUED, error = null, files = emptyList()) }
        schedule()
    }
    @Synchronized fun pause(id: String) {
        if (entries.value.firstOrNull { it.id == id }?.stage !in listOf(DownloadStage.QUEUED, DownloadStage.PREPARING, DownloadStage.DOWNLOADING)) return
        update(id) { it.copy(stage = DownloadStage.PAUSED) }
        active[id]?.cancel()
    }
    @Synchronized fun attach(id: String, job: Job) { active[id] = job }
    @Synchronized fun detach(id: String) { active.remove(id) }
    @Synchronized fun delete(id: String) {
        check(id !in leases) { "DOWNLOAD_PLAYING" }
        check(id !in active) { "DOWNLOAD_ACTIVE" }
        val record = entries.value.firstOrNull { it.id == id } ?: return
        // Remove only resources in this task's exact UUID directory, never cache or user-owned paths.
        val directory = directory(id)
        check(!SimpleCache.isCacheFolderLocked(directory)) { "DOWNLOAD_ACTIVE" }
        SimpleCache.delete(directory, database)
        privateStore.write("source_$id", null)
        privateStore.write("offline_$id", null)
        save(entries.value.filterNot { it.id == record.id })
    }
    fun directory(id: String): File {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        return File(context.filesDir, "media-downloads/$id").apply { check(mkdirs() || isDirectory) }
    }
    fun cache(id: String) = SimpleCache(directory(id), NoOpCacheEvictor(), database)
    fun source(id: String): DownloadSource = DownloadSource.decode(requireNotNull(privateStore.read("source_$id")))
    fun recipe(id: String): String? = privateStore.read("offline_$id")
    fun saveRecipe(id: String, json: String) = privateStore.write("offline_$id", json)
    fun completed(id: String, bytes: Long) {
        update(id) { it.copy(stage = DownloadStage.COMPLETE, bytes = bytes, total = bytes, error = null) }
        privateStore.write("source_$id", null)
    }
    @Synchronized fun open(id: String): OfflineDownload {
        val record = entries.value.firstOrNull { it.id == id && it.stage == DownloadStage.COMPLETE } ?: error("DOWNLOAD_MISSING")
        check(leases.add(id)) { "DOWNLOAD_PLAYING" }
        try {
            val recipe = JSONObject(requireNotNull(recipe(id)))
            val keys = recipe.optJSONArray("keys") ?: JSONArray()
            val media = MediaItem.Builder().setUri(recipe.getString("url")).setMimeType(recipe.optString("mime").takeIf(String::isNotEmpty))
                .setCustomCacheKey(recipe.optString("cacheKey").takeIf(String::isNotEmpty))
                .setStreamKeys((0 until keys.length()).map { i -> keys.getJSONArray(i).let { StreamKey(it.getInt(0), it.getInt(1), it.getInt(2)) } }).build()
            val cache = cache(id)
            return OfflineDownload(record, media, CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(null)
                .setCacheWriteDataSinkFactory(null)) { cache.release(); synchronized(this) { leases.remove(id) } }
        } catch (error: Exception) { leases.remove(id); throw error }
    }
    companion object {
        const val RESERVE = 256L * 1024 * 1024
        const val QUEUE = "aetherio-persistent-media-downloads"
    }
}

internal class OfflineDownload(val record: MediaDownload, val media: MediaItem, val factory: DataSource.Factory,
    private val release: () -> Unit) : java.io.Closeable {
    private var closed = false
    override fun close() { if (!closed) { closed = true; release() } }
}

internal object DownloadCodec {
    fun encode(entries: List<MediaDownload>): String = JSONArray().apply { entries.forEach { record ->
        put(JSONObject().put("id", record.id).put("target", WatchProgressCodec.encodeTarget(record.target)).put("provider", record.provider)
            .put("stage", record.stage.name).put("bytes", record.bytes).put("total", record.total).put("error", record.error)
            .put("files", JSONArray().apply { record.files.forEach { put(JSONObject().put("index", it.index).put("name", it.name).put("size", it.size)) } }))
    } }.toString()
    fun decode(raw: String): List<MediaDownload> = runCatching {
        require(raw.length <= 16 * 1024 * 1024)
        val array = JSONArray(raw)
        require(array.length() <= 2000)
        (0 until array.length()).map { i -> array.getJSONObject(i).let { json ->
            val id = json.getString("id").also { require(it.matches(Regex("[a-f0-9-]{36}"))) }
            val files = json.optJSONArray("files") ?: JSONArray()
            MediaDownload(id, requireNotNull(WatchProgressCodec.decodeTarget(json.getString("target"))), json.getString("provider"),
                DownloadStage.valueOf(json.getString("stage")), json.getLong("bytes").coerceAtLeast(0), json.getLong("total"),
                json.optString("error").takeIf(String::isNotEmpty), (0 until minOf(files.length(), 1000)).map { j -> files.getJSONObject(j).let {
                    DownloadFileChoice(it.getInt("index"), it.getString("name").take(512), it.getLong("size"))
                } })
        } }.distinctBy { it.id }
    }.getOrDefault(emptyList())
}

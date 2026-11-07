package com.panoli707.aetherio.nativeapp.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.DownloadHelper
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.panoli707.aetherio.nativeapp.AetherioApplication
import com.panoli707.aetherio.nativeapp.MainActivity
import com.panoli707.aetherio.nativeapp.player.MediaUrlPolicy
import com.panoli707.aetherio.nativeapp.player.PlaybackHeaders
import com.panoli707.aetherio.nativeapp.player.torrent.*
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

private class TorrentDownloadFailure(val userMessage: String) : Exception("TORRENT_PREPARE")

@androidx.annotation.OptIn(UnstableApi::class)
class MediaDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val app get() = applicationContext as AetherioApplication
    private val store get() = app.mediaDownloads
    override suspend fun doWork(): Result = store.queueLock.withLock {
        androidx.media3.common.util.Log.setLogLevel(androidx.media3.common.util.Log.LOG_LEVEL_OFF)
        if (store.entries.value.none { it.stage in listOf(DownloadStage.QUEUED, DownloadStage.PREPARING, DownloadStage.DOWNLOADING) }) return@withLock Result.success()
        try { setForeground(foreground()) } catch (_: Exception) { return@withLock Result.retry() }
        while (currentCoroutineContext().isActive) {
            val next = store.entries.value.firstOrNull { it.stage in listOf(DownloadStage.QUEUED, DownloadStage.PREPARING, DownloadStage.DOWNLOADING) }
                ?: return@withLock Result.success()
            var retry = false
            supervisorScope {
                val task = async(Dispatchers.IO, start = CoroutineStart.LAZY) { download(next) }
                store.attach(next.id, task)
                try { task.await() }
                catch (_: TimeoutCancellationException) { store.update(next.id) { it.copy(stage = DownloadStage.FAILED, error = "La fuente tardó demasiado. Reintenta o elige otra fuente.") } }
                catch (e: CancellationException) { currentCoroutineContext().ensureActive() }
                catch (e: Exception) {
                    val http = generateSequence(e as Throwable) { it.cause }.filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode
                    // Diagnostic types only: exceptions can contain signed URLs, authorization headers or account data.
                    val causes = generateSequence(e as Throwable) { it.cause }.take(10).toList()
                    val origin = causes.last().stackTrace.take(4).joinToString("/") {
                        "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
                    }
                    android.util.Log.w("AetherioDownloads", "failure=" + causes.joinToString("/") { it.javaClass.name } +
                        ", origin=" + origin + ", http=" + (http ?: 0) + ", attempt=" + runAttemptCount)
                    retry = e is IOException && e !is DownloadHelper.LiveContentUnsupportedException && http !in listOf(400, 401, 403, 404, 410) && runAttemptCount < 3
                    store.update(next.id) { it.copy(stage = if (retry) DownloadStage.QUEUED else DownloadStage.FAILED, error = when {
                        e is TorrentDownloadFailure -> e.userMessage
                        e.message == "DISK_SPACE" -> "No queda espacio suficiente. Libera almacenamiento y reintenta."
                        e.message == "DRM" -> "Esta fuente está protegida con DRM y no se puede descargar."
                        http in listOf(401, 403, 404, 410) -> "El enlace no está disponible o ha caducado. Elimina esta tarea y elige otra fuente."
                        e is DownloadHelper.LiveContentUnsupportedException -> "Las emisiones en directo no se pueden guardar como episodios."
                        else -> if (retry) "Conexión interrumpida. Se reintentará sin perder los bloques guardados." else "No se pudo completar la descarga. Reintenta o elige otra fuente."
                    }) }
                } finally { store.detach(next.id) }
            }
            if (retry) return@withLock Result.retry()
        }
        Result.retry()
    }

    private suspend fun download(record: MediaDownload) {
        if (store.entries.value.firstOrNull { it.id == record.id }?.stage == DownloadStage.PAUSED) return
        check(store.directory(record.id).usableSpace > MediaDownloads.RESERVE) { "DISK_SPACE" }
        store.update(record.id) { it.copy(stage = DownloadStage.PREPARING, error = null) }
        val spec = store.source(record.id)
        var session: TorrentSessionManager.Session? = null
        var route: TorrentRoute? = null
        try {
            if (spec.torrent != null) {
                session = app.downloadTorrentSessions.open(spec.torrent)
                when (val state = session.state.first { it is TorrentState.Ready || it is TorrentState.SelectFile || it is TorrentState.Failed }) {
                    is TorrentState.SelectFile -> {
                        store.update(record.id) { it.copy(stage = DownloadStage.NEEDS_FILE,
                            files = state.files.map { file -> DownloadFileChoice(file.index, file.path.take(512), file.size) }) }
                        return
                    }
                    is TorrentState.Ready -> route = state.route
                    is TorrentState.Failed -> throw TorrentDownloadFailure(state.message)
                    else -> error("TORRENT_PREPARE")
                }
            }
            val probe = if (route == null) runInterruptible(Dispatchers.IO) { probe(spec) } else null
            val factory = route?.let { TorrentDataSourceFactory(it, httpFactory(spec)) } ?: httpFactory(spec, probe)
            val media = MediaItem.Builder().setUri(route?.url ?: spec.url).setMimeType(if (route != null) null else spec.format.mimeType ?: probe?.mime)
                .apply { if (route != null) setCustomCacheKey(record.id) }.build()
            val request = if (route != null) DownloadRequest.Builder(record.id, Uri.parse(route.url)).setCustomCacheKey(record.id).build()
                else prepare(record.id, media, factory)
            // Only a stable, encrypted playback recipe survives. Native route tokens never do.
            val offline = JSONObject().put("url", if (route != null) "https://offline.aetherio.invalid/${record.id}.video" else request.uri.toString())
                .put("mime", request.mimeType).put("cacheKey", request.customCacheKey)
                .put("entity", probe?.validator)
                .put("keys", JSONArray().apply { request.streamKeys.forEach { put(JSONArray().put(it.periodIndex).put(it.groupIndex).put(it.streamIndex)) } })
            val previous = store.recipe(record.id)
            val cache = store.cache(record.id)
            val executor = Executors.newFixedThreadPool(2)
            var completedBytes = 0L
            try {
                // Never splice an old response into a changed file. Without a validator, HTTP retries restart safely.
                if (previous != null && (previous != offline.toString() || route == null && probe?.validator.isNullOrEmpty())) {
                    cache.keys.toList().forEach(cache::removeResource)
                    store.update(record.id) { it.copy(bytes = 0, total = -1) }
                }
                store.saveRecipe(record.id, offline.toString())
                val downloader = DefaultDownloaderFactory(CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(factory), executor).createDownloader(request)
                coroutineScope {
                    val cancellation = launch(Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
                        try { awaitCancellation() } finally { downloader.cancel() }
                    }
                    try {
                        runInterruptible(Dispatchers.IO) {
                            downloader.download { total, bytes, _ -> store.progress(record.id, bytes, total) }
                        }
                    } finally { cancellation.cancelAndJoin() }
                }
                currentCoroutineContext().ensureActive()
                check(cache.cacheSpace > 0) { "EMPTY_DOWNLOAD" }
                completedBytes = cache.cacheSpace
            } finally { executor.shutdownNow(); cache.release() }
            store.completed(record.id, completedBytes)
        } finally { session?.close() }
    }

    private data class Probe(val url: String, val mime: String?, val etag: String?, val modified: String?) {
        val validator: String? get() = etag ?: modified
    }
    private fun probe(spec: DownloadSource): Probe {
        val data = httpFactory(spec).createDataSource()
        try {
            data.open(DataSpec.Builder().setUri(spec.url).setLength(1).build())
            fun header(name: String) = data.responseHeaders.entries.firstOrNull { it.key.equals(name, true) }?.value?.firstOrNull()
            val mime = adaptiveDownloadMime(header("Content-Type"))
            val etag = header("ETag")?.takeIf { !it.startsWith("W/", true) && it.length <= 4096 }
            val modified = header("Last-Modified")?.takeIf { it.length <= 100 }
            return Probe(data.uri?.toString() ?: spec.url, mime, etag, modified)
        } finally { data.close() }
    }
    private fun httpFactory(spec: DownloadSource, probe: Probe? = null): DataSource.Factory {
        val client = (app.downloadHttpClientOverride?.newBuilder() ?: OkHttpClient.Builder()).connectTimeout(12, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
            .addNetworkInterceptor { chain ->
                try { MediaUrlPolicy.validate(chain.request().url.toString(), spec.desktopHttp) }
                catch (_: IllegalArgumentException) { throw IOException("Destino no permitido") }
                val headers = chain.request().headers.names().associateWith { chain.request().header(it).orEmpty() }
                val allowed = PlaybackHeaders.forDestination(headers, spec.url, chain.request().url)
                val builder = chain.request().newBuilder()
                (headers.keys - allowed.keys).forEach(builder::removeHeader)
                if (chain.request().url.toString() == probe?.url) {
                    if (probe.etag != null) builder.header("If-Match", probe.etag)
                    else if (probe.modified != null) builder.header("If-Unmodified-Since", probe.modified)
                }
                val response = chain.proceed(builder.build())
                // Let Media3 classify HTTP errors first: a 403 HTML error page is not a retryable video failure.
                if (response.isSuccessful && response.header("Content-Type")?.substringBefore(';')?.lowercase() in listOf("text/html", "application/json")) {
                    response.close(); throw IOException("La fuente no devolvió vídeo")
                }
                response
            }.build()
        return ResolvingDataSource.Factory(OkHttpDataSource.Factory(client).setDefaultRequestProperties(spec.headers)) { data ->
            val url = try { MediaUrlPolicy.validate(data.uri.toString(), spec.desktopHttp) }
            catch (_: IllegalArgumentException) { throw IOException("Destino no permitido") }
            data.withUri(Uri.parse(url))
        }
    }
    private suspend fun prepare(id: String, media: MediaItem, factory: DataSource.Factory): DownloadRequest = withContext(Dispatchers.Main) {
        val helper = DownloadHelper.forMediaItem(applicationContext, media, DefaultRenderersFactory(applicationContext), factory)
        try {
            withTimeout(60_000) { suspendCancellableCoroutine { continuation ->
                helper.prepare(object : DownloadHelper.Callback {
                    override fun onPrepared(helper: DownloadHelper) {
                        if (!continuation.isActive) return
                        try {
                            for (period in 0 until helper.periodCount) {
                                val groups = helper.getTrackGroups(period)
                                for (group in 0 until groups.length) for (track in 0 until groups[group].length)
                                    check(groups[group].getFormat(track).drmInitData == null) { "DRM" }
                            }
                            val request = helper.getDownloadRequest(id, null)
                            if (androidx.media3.common.util.Util.inferContentTypeForUriAndMimeType(request.uri, request.mimeType) != C.CONTENT_TYPE_OTHER)
                                check(request.streamKeys.isNotEmpty()) { "NO_SUPPORTED_TRACKS" }
                            continuation.resume(request)
                        } catch (error: Exception) { continuation.resumeWithException(error) }
                    }
                    override fun onPrepareError(helper: DownloadHelper, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                })
            } }
        } finally { helper.release() }
    }
    private fun foreground(): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "Descargas de vídeo", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(applicationContext, 21, Intent(applicationContext, MainActivity::class.java)
            .putExtra("openDownloads", true), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Descargas de Aetherio").setContentText("Guardando vídeos para ver sin conexión. Adminístralos desde tu perfil.")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(210, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(210, notification)
    }
    companion object { private const val CHANNEL = "aetherio_media_downloads" }
}

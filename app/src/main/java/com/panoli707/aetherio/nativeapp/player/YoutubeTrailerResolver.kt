package com.panoli707.aetherio.nativeapp.player

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.URI
import java.util.UUID
import java.io.File
import java.security.MessageDigest
import com.panoli707.aetherio.nativeapp.R

internal data class YoutubeTrailerStream(val url: String, val mime: String?, val headers: Map<String, String>)

internal fun interface YoutubeTrailerSource {
    suspend fun resolve(context: Context, id: String, forceRefresh: Boolean): YoutubeTrailerStream
}

/** Desktop pipeline adapted to Android: resolve the selected public video and stream in Media3.
 * No login/cookies, downloads, server, automatic executable updates or persisted signed URLs.
 */
internal object YoutubeTrailerResolver : YoutubeTrailerSource {
    private val lock = Mutex()
    private val cache = linkedMapOf<String, Pair<Long, YoutubeTrailerStream>>()
    private const val BUNDLED_SHA256 = "e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3"
    private fun installBundledExtractor(context: Context) {
        val target = File(context.noBackupFilesDir, "${YoutubeDL.baseName}/${YoutubeDL.ytdlpDirName}/${YoutubeDL.ytdlpBin}")
        fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        if (target.isFile && digest(target.readBytes()) == BUNDLED_SHA256) return
        val bytes = context.resources.openRawResource(R.raw.aetherio_ytdlp).use { it.readBytes() }
        check(digest(bytes) == BUNDLED_SHA256)
        val pending = File(target.parentFile, "aetherio-bundled.tmp")
        pending.writeBytes(bytes)
        check(pending.renameTo(target))
    }
    fun validateUrl(raw: String): String {
        val safe = MediaUrlPolicy.validate(raw)
        val uri = URI(safe)
        require(uri.rawFragment == null && uri.port in listOf(-1, 443) &&
            (uri.host == "googlevideo.com" || uri.host.endsWith(".googlevideo.com")))
        return safe
    }
    fun parse(raw: String): YoutubeTrailerStream {
        require(raw.length <= 8 * 1024 * 1024)
        val json = JSONObject(raw)
        require(json.optString("vcodec", "none") != "none" && json.optString("acodec", "none") != "none")
        val url = validateUrl(json.getString("url"))
        val headers = json.optJSONObject("http_headers")
        val safeHeaders = listOf("User-Agent", "Referer", "Origin").mapNotNull { key ->
            headers?.optString(key)?.takeIf(String::isNotBlank)?.let { key to it }
        }.toMap()
        val mime = when {
            json.optString("protocol").startsWith("m3u8") -> "application/x-mpegURL"
            json.optString("ext") == "webm" -> "video/webm"
            json.optString("ext") == "mp4" -> "video/mp4"
            else -> null
        }
        return YoutubeTrailerStream(url, mime, PlaybackHeaders.validate(safeHeaders))
    }
    suspend fun resolve(context: Context, id: String): YoutubeTrailerStream = resolve(context, id, false)
    override suspend fun resolve(context: Context, id: String, forceRefresh: Boolean): YoutubeTrailerStream {
        require(TrailerEmbed.validId(id) != null)
        return com.panoli707.aetherio.nativeapp.data.LoadTiming.measure(com.panoli707.aetherio.nativeapp.data.LoadTiming.Stage.YOUTUBE_RESOLVE) {
        withTimeout(60_000) {
            lock.withLock {
                val now = System.nanoTime()
                cache.entries.removeAll { now - it.value.first >= 5L * 60 * 1_000_000_000 }
                if (forceRefresh) cache.remove(id)
                if (!forceRefresh) cache[id]?.let { return@withLock it.second }
                val stream = runInterruptible(Dispatchers.IO) {
                    val processId = "aetherio-trailer-${UUID.randomUUID()}"
                    try {
                        YoutubeDL.init(context.applicationContext)
                        installBundledExtractor(context.applicationContext)
                        val request = YoutubeDLRequest("https://www.youtube.com/watch?v=$id")
                        request.addOption("--ignore-config")
                        request.addOption("--no-playlist")
                        request.addOption("--skip-download")
                        request.addOption("--dump-single-json")
                        request.addOption("--no-warnings")
                        request.addOption("--socket-timeout", "12")
                        request.addOption("--retries", "1")
                        request.addOption("-f", "best[height<=720][ext=mp4]/best[height<=720]/best")
                        parse(YoutubeDL.execute(request, processId).out)
                    } finally { YoutubeDL.destroyProcessById(processId) }
                }
                if (cache.size >= 8) cache.remove(cache.keys.first())
                cache[id] = System.nanoTime() to stream
                stream
            }
        }
        }
    }
}

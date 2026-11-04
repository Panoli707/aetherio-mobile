package com.panoli707.aetherio.nativeapp.data

import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

internal interface HomeItemsCache {
    fun read(key: String): List<MediaItem>?
    fun write(key: String, items: List<MediaItem>)
}

/** Disposable public metadata only; never stores credentials, playback URLs or user history. */
internal class HomeCatalogCache(private val directory: File, private val clock: () -> Long = System::currentTimeMillis) : HomeItemsCache {
    private fun file(key: String) = File(directory, MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) } + ".json")
    @Synchronized override fun read(key: String): List<MediaItem>? = runCatching {
        val file = file(key)
        if (!file.isFile || file.length() !in 1..MAX_FILE_BYTES) return null
        val json = JSONObject(file.readText(Charsets.UTF_8))
        if (json.optString("version") != DesktopHomeCatalog.REFERENCE || clock() - json.optLong("at") !in 0 until TTL_MS) return null
        CatalogJson.decode(json.getString("items")).take(200).takeIf { it.isNotEmpty() }
    }.getOrNull()
    @Synchronized override fun write(key: String, items: List<MediaItem>) {
        if (items.isEmpty()) return
        val text = JSONObject().put("version", DesktopHomeCatalog.REFERENCE).put("at", clock())
            .put("items", CatalogJson.encode(items.take(200))).toString()
        if (text.toByteArray(Charsets.UTF_8).size > MAX_FILE_BYTES) return
        if (!directory.isDirectory && !directory.mkdirs()) return
        file(key).writeText(text, Charsets.UTF_8)
        val files = directory.listFiles { f -> f.isFile && f.name.matches(Regex("[a-f0-9]{64}\\.json")) }.orEmpty().sortedBy { it.lastModified() }
        var bytes = files.sumOf { it.length() }
        var count = files.size
        for (old in files) {
            if (bytes <= MAX_TOTAL_BYTES && count <= 160) break
            val length = old.length()
            if (old.delete()) { bytes -= length; count-- }
        }
    }
    companion object {
        const val TTL_MS = 6 * 60 * 60 * 1000L
        const val MAX_FILE_BYTES = 2 * 1024 * 1024L
        const val MAX_TOTAL_BYTES = 16 * 1024 * 1024L
    }
}

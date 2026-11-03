package com.panoli707.aetherio.nativeapp.player.torrent

import java.util.Locale

data class TorrentFile(val index: Int, val path: String, val size: Long, val pathTruncated: Boolean = false) {
    val label: String get() = path.replace('\\', '/').filterNot(Char::isISOControl).take(512)
}

object TorrentFileSelection {
    private val extensions = setOf("mkv", "mp4", "m4v", "webm", "avi", "mov", "ts", "m2ts")
    private fun normalized(path: String) = path.replace('\\', '/').lowercase(Locale.ROOT)
    fun candidates(files: List<TorrentFile>): List<TorrentFile> = files.filter {
        it.size > 0 && normalized(it.path).substringAfterLast('.', "") in extensions
    }
    /** null means explicit user selection, never an implicit largest-episode guess. */
    fun choose(files: List<TorrentFile>, request: TorrentRequest): TorrentFile? {
        val videos = candidates(files)
        require(videos.isNotEmpty()) { "NO_VIDEO" }
        request.fileIndex?.let { index -> return requireNotNull(videos.singleOrNull { it.index == index }) { "INVALID_FILE" } }
        request.filenameHint?.let { hint ->
            val full = videos.filter { !it.pathTruncated && normalized(it.path) == normalized(hint) }
            if (full.size == 1) return full.single()
            val base = videos.filter { !it.pathTruncated && normalized(it.path).substringAfterLast('/') == normalized(hint).substringAfterLast('/') }
            if (base.size == 1) return base.single()
        }
        val main = videos.filterNot { normalized(it.path).substringAfterLast('/').matches(Regex("(?:sample|trailer)(?:[._ -].*)?\\.[^.]+")) }
        return main.singleOrNull()?.takeUnless { it.pathTruncated }
    }
}

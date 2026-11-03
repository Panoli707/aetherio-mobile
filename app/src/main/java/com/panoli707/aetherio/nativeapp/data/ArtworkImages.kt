package com.panoli707.aetherio.nativeapp.data

import java.net.URI

/** Upgrade only known TMDB image variants; never rewrite provider or signed URLs. */
internal object ArtworkImages {
    data class Header(val url: String, val aspectRatio: Float, val containsTitle: Boolean = false)

    /** Never fill a portrait header by zooming into a landscape banner. */
    fun headers(item: MediaItem, portrait: Boolean): List<Header> {
        val clean = item.portraitArtworkUrl?.takeIf(String::isNotBlank)?.let { Header(it, 2f / 3f) }
        val poster = item.posterUrl?.takeIf(String::isNotBlank)?.let { Header(it, 2f / 3f, containsTitle = true) }
        val backdrop = item.backdropUrl?.takeIf(String::isNotBlank)?.let { Header(it, 16f / 9f) }
        return (if (portrait) listOf(clean, poster, backdrop) else listOf(backdrop, clean, poster))
            .filterNotNull().distinctBy { it.url }
    }
    fun highResolution(url: String?): String? {
        if (url == null) return null
        return runCatching {
            val uri = URI(url)
            if (uri.scheme != "https" || uri.host != "image.tmdb.org" || uri.rawUserInfo != null ||
                uri.port != -1 || uri.rawQuery != null || uri.rawFragment != null) return url
            if (!Regex("/t/p/w[0-9]+/[^/]+").matches(uri.rawPath)) return url
            "https://image.tmdb.org" + uri.rawPath.replaceFirst(Regex("/t/p/w[0-9]+/"), "/t/p/original/")
        }.getOrDefault(url)
    }

    fun hero(item: MediaItem, portrait: Boolean): String? = if (portrait)
        item.posterUrl ?: item.backdropUrl else item.backdropUrl ?: item.posterUrl

}

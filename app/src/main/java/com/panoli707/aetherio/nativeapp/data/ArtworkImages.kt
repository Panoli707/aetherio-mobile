package com.panoli707.aetherio.nativeapp.data

import java.net.URI

/** Upgrade only known TMDB image variants; never rewrite provider or signed URLs. */
internal object ArtworkImages {
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

    /** Detail screens overlay a separate title logo, so prefer clean landscape artwork over a text-heavy poster. */
    fun detailHero(item: MediaItem): String? = item.backdropUrl ?: item.posterUrl
}

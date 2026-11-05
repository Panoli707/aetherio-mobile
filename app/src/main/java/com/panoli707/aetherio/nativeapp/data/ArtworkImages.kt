package com.panoli707.aetherio.nativeapp.data

import java.net.URI

/** Upgrade only known TMDB image variants; never rewrite provider or signed URLs. */
internal object ArtworkImages {
    enum class Kind { PORTRAIT, POSTER, BACKDROP }
    data class Header(val url: String, val aspectRatio: Float, val kind: Kind,
        val titlePresence: ArtworkTitlePresence = ArtworkTitlePresence.UNKNOWN)

    /** Never fill a portrait header by zooming into a landscape banner. */
    fun headers(item: MediaItem, portrait: Boolean): List<Header> {
        val clean = item.portraitArtworkUrl?.takeIf(String::isNotBlank)?.let { Header(it, 2f / 3f, Kind.PORTRAIT, item.portraitTitlePresence) }
        val poster = item.posterUrl?.takeIf(String::isNotBlank)?.let { Header(it, 2f / 3f, Kind.POSTER, item.posterTitlePresence) }
        val backdrop = item.backdropUrl?.takeIf(String::isNotBlank)?.let { Header(it, 16f / 9f, Kind.BACKDROP, item.backdropTitlePresence) }
        val alternatives = item.portraitAlternatives.map { Header(it, 2f / 3f, Kind.PORTRAIT) }
        return (if (portrait) listOf(clean, poster) + alternatives + backdrop else listOf(backdrop, clean, poster) + alternatives)
            .filterNotNull().distinctBy { it.url }
    }
    fun separateTitle(header: Header?, detail: Boolean): Boolean = true

    fun overlayTitle(header: Header?): Boolean = header?.titlePresence in
        setOf(ArtworkTitlePresence.ABSENT, ArtworkTitlePresence.TOP)

    fun logos(item: MediaItem): List<String> = (listOfNotNull(AnimeLogoOverrides.logo(item), AnimeLogoCatalog.logo(item), item.logoUrl) + item.logoAlternatives)
        .filter(String::isNotBlank).flatMap { listOfNotNull(highResolution(it), it) }.distinct()
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

    /** One display-sized variant per illustration; never rewrite signed/foreign URLs. */
    fun canonicalResolution(url: String): String = highResolution(url)?.let { original ->
        if (original.startsWith("https://image.tmdb.org/t/p/original/") &&
            runCatching { URI(original).let { it.rawQuery == null && it.rawFragment == null } }.getOrDefault(false))
            original.replace("/t/p/original/", "/t/p/w1280/") else original
    } ?: url

    fun hero(item: MediaItem, portrait: Boolean): String? = if (portrait)
        item.posterUrl ?: item.backdropUrl else item.backdropUrl ?: item.posterUrl

}

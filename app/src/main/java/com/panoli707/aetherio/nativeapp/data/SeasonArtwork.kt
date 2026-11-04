package com.panoli707.aetherio.nativeapp.data

/** Always derive a season from the series artwork, never from the previously selected season. */
internal object SeasonArtwork {
    fun apply(base: MediaItem, season: Int, poster: String? = null, backdrop: String? = null,
        logo: String? = null, portrait: String? = null): MediaItem {
        if (listOf(poster, backdrop, portrait).all { it.isNullOrBlank() }) return base.copy(seasonHint = season)
        return base.copy(posterUrl = poster, backdropUrl = backdrop, portraitArtworkUrl = portrait,
            logoUrl = logo ?: base.logoUrl, seasonHint = season)
    }
}

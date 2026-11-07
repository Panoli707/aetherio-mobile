package com.panoli707.aetherio.nativeapp.data

/** IMDb episodes and TMDB artwork are different contracts. Never identify a series by its title. */
internal object AnimeEpisodeCatalog {
    fun preferImdb(detail: MediaDetail, imdb: MediaDetail, parts: List<SeriesPart>): MediaDetail {
        if (parts.isEmpty() || detail.item.imdbId != imdb.item.id || imdb.item.source != CatalogSource.CINEMETA ||
            imdb.item.type != MediaType.SERIES || imdb.seasons.isEmpty() ||
            parts.any { part -> imdb.seasons.none { it.number == part.season } }) return detail
        val preferred = detail.item.seasonHint ?: detail.selectedSeason
        return detail.copy(item = detail.item.copy(imdbNumbering = true, seriesNumbering = true, isAnime = true),
            seasons = imdb.seasons, selectedSeason = preferred?.takeIf { number -> imdb.seasons.any { it.number == number } }
                ?: imdb.seasons.first().number, episodes = emptyList())
    }

    /** A unique dated episode can establish a conversion; missing/duplicate dates cannot. */
    fun matchingEpisode(episode: EpisodeItem, candidates: List<EpisodeItem>): EpisodeItem? =
        episode.airedOn.takeIf { it.matches(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) }?.let { date ->
            candidates.filter { it.airedOn == date }.singleOrNull()
        }
}

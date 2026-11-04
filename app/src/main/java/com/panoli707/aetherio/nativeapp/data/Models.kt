package com.panoli707.aetherio.nativeapp.data

enum class MediaType(val path: String, val label: String) {
    MOVIE("movie", "Película"),
    SERIES("tv", "Serie")
}

enum class CatalogSource { TMDB, CINEMETA, ANILIST }
enum class SectionStyle { POSTERS, RANKED, FEATURED }

data class MediaItem(
    val id: String,
    val type: MediaType,
    val title: String,
    val source: CatalogSource = CatalogSource.TMDB,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val logoUrl: String? = null,
    val overview: String = "",
    val year: String = "",
    val score: Double = 0.0,
    val genres: List<String> = emptyList(),
    val isAnime: Boolean = false,
    val imdbId: String? = null,
    val episodeCount: Int? = null,
    val animeSeasonLabel: String = "",
    val seasonHint: Int? = null,
    val animeEntryId: String? = null,
    val seriesNumbering: Boolean = false,
    val voteCount: Int? = null,
    val certification: String? = null,
    val portraitArtworkUrl: String? = null
) {
    val key: String get() = "${source.name}:${type.path}:$id"
}

data class CatalogSection(
    val id: String,
    val title: String,
    val items: List<MediaItem>,
    val style: SectionStyle = SectionStyle.POSTERS
)

data class CastMember(val name: String, val role: String, val imageUrl: String? = null)
data class SeasonInfo(val number: Int, val name: String, val episodeCount: Int, val posterUrl: String? = null)
data class EpisodeItem(
    val number: Int,
    val season: Int,
    val title: String,
    val overview: String = "",
    val imageUrl: String? = null,
    val released: Boolean = true,
    val airedOn: String = "",
    val runtimeMinutes: Int? = null
)

data class MediaDetail(
    val item: MediaItem,
    val runtimeMinutes: Int? = null,
    val cast: List<CastMember> = emptyList(),
    val seasons: List<SeasonInfo> = emptyList(),
    val episodes: List<EpisodeItem> = emptyList(),
    val selectedSeason: Int? = null,
    val trailers: List<MediaTrailer> = emptyList(),
    val facts: List<DetailFact> = emptyList(),
    val availability: List<WatchRegion> = emptyList(),
    val reviews: List<MediaReview> = emptyList(),
    val related: List<MediaItem> = emptyList(),
    val voteCount: Int? = null,
    val certification: String? = null,
    val artworkBase: MediaItem? = null
)

data class MediaTrailer(val id: String, val title: String, val url: String, val thumbnailUrl: String)
data class DetailFact(val label: String, val value: String)
data class WatchPlatform(val id: Int, val name: String, val logoUrl: String?, val offer: String)
data class WatchRegion(val country: String, val link: String?, val platforms: List<WatchPlatform>)
data class MediaReview(val id: String, val author: String, val content: String, val publishedOn: String, val url: String?)

data class HomeCatalog(val sections: List<CatalogSection>, val heroes: List<MediaItem>, val partial: Boolean = false)

data class AppState(
    val sections: List<CatalogSection> = emptyList(),
    val heroes: List<MediaItem> = emptyList(),
    val homeLoading: Boolean = true,
    val homeError: String? = null,
    val selectedItem: MediaItem? = null,
    val detail: MediaDetail? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val episodesLoading: Boolean = false,
    val pendingSeason: Int? = null,
    val searchQuery: String = "",
    val searchResults: List<MediaItem> = emptyList(),
    val searchLoading: Boolean = false,
    val searchError: String? = null,
    val savedItems: List<MediaItem> = emptyList(),
    val watchProgress: List<WatchProgress> = emptyList(),
    val watchingAnime: List<MediaItem> = emptyList(),
    val animeSeason: AnimeSeasonState = AnimeSeasonState(),
    val profileName: String = "Usuario",
    val addons: List<AddonConfig> = listOf(ProviderHub.config),
    val enabledDesktopSources: Set<String> = DesktopSources.defaultIds,
    val desktopSourceErrors: Set<String> = emptySet(),
    val addonsLoading: Boolean = true,
    val addonSaving: Boolean = false,
    val addonOperation: Long = 0,
    val addonError: String? = null,
    val sourceQuery: SourceQuery? = null,
    val sourcesLoading: Boolean = false,
    val sources: List<StreamSource> = emptyList(),
    val sourceError: String? = null
)

package com.panoli707.aetherio.nativeapp.data

import org.junit.Assert.*
import org.junit.Test

class LibraryFilteringTest {
    private val movie = MediaItem("900000001", MediaType.MOVIE, "El bosque", genres = listOf("Drama", "Terror"))
    private val series = MediaItem("900000002", MediaType.SERIES, "La aventura", genres = listOf("Comedia"))

    @Test fun mergesListsWithoutMutatingHistoryAndSavedTakesPrecedence() {
        val progress = WatchProgress(WatchTarget(movie), watched = true, updatedAt = 100)
        val state = AppState(savedItems = listOf(movie.copy(genres = emptyList())), watchProgress = listOf(progress), savedAddedAt = mapOf(movie.key to 123))
        val entries = LibraryFiltering.entries(state)
        assertEquals(1, entries.size)
        assertEquals("saved", entries.single().list)
        assertEquals(123L, entries.single().addedAt)
        assertEquals(movie.genres, entries.single().item.genres)
        assertEquals(listOf(progress), state.watchProgress)
    }

    @Test fun filtersAndFacetsUseTheSelectedListAndCrossCountGenresAndTypes() {
        val entries = listOf(LibraryEntry(movie, 2, "saved", "Mi lista"), LibraryEntry(series, 1, "saved", "Mi lista"))
        val result = LibraryFiltering.filter(entries, LibraryFilters(type = "movie"))
        assertEquals(listOf(movie), result.entries.map { it.item })
        assertEquals(0, result.genres.first { it.key == "comedia" }.count)
        val none = LibraryFiltering.filter(entries, LibraryFilters(type = "tv", genre = "terror"))
        assertTrue(none.entries.isEmpty())
        assertEquals(0, none.types.first { it.key == "tv" }.count)
        assertEquals("all", LibraryFiltering.filter(entries, LibraryFilters(list = "deleted", genre = "unknown")).filters.genre)
    }

    @Test fun sortsLikeDesktopIgnoringLeadingArticlesAndUsesRealSavedDates() {
        val entries = listOf(LibraryEntry(movie, 200, "saved", "Mi lista"), LibraryEntry(series, 100, "activity", "Actividad"))
        fun ids(sort: String) = LibraryFiltering.filter(entries, LibraryFilters(sort = sort)).entries.map { it.item.id }
        assertEquals(listOf(series.id, movie.id), ids("title_asc"))
        assertEquals(listOf(movie.id, series.id), ids("title_desc"))
        assertEquals(listOf(series.id, movie.id), ids("added_asc"))
        assertEquals(listOf(movie.id, series.id), ids("added_desc"))
    }

    @Test fun importedAniListEntriesAreDeduplicatedByCanonicalIdWithoutImportingProgress() {
        val anime = MediaItem("195516", MediaType.SERIES, "Anime", CatalogSource.ANILIST, isAnime = true)
        val canonical = SeriesIdentity.item(anime)
        val connection = AnimeConnection(entries = listOf(AnimeEntry(195516, 195516, null, "Anime", status = AnimeStatus.PLANNED)), updatedAt = 10)
        val state = AppState(savedItems = listOf(canonical), animeTracking = AnimeTrackingState(connections = mapOf(AnimeService.ANILIST to connection)))
        assertEquals(1, LibraryFiltering.entries(state).size)
        assertEquals("saved", LibraryFiltering.entries(state).single().list)
        assertTrue(state.watchProgress.isEmpty())
    }

    @Test fun emptyLibraryAndLegacySavedItemsHaveStableOrdering() {
        assertTrue(LibraryFiltering.filter(emptyList(), LibraryFilters()).entries.isEmpty())
        val entries = LibraryFiltering.entries(AppState(savedItems = listOf(movie, series)))
        assertEquals(listOf(movie.id, series.id), LibraryFiltering.filter(entries, LibraryFilters()).entries.map { it.item.id })
    }
    @Test fun pendingSeasonHasItsOwnCardButOpensTheGeneralSeriesAtThatSeason() {
        val root = SeriesIdentity.item(MediaItem("21355", MediaType.SERIES, "Re:Zero", CatalogSource.ANILIST, isAnime = true))
        val second = root.copy(title = "Re:Zero Season 2", seasonHint = 2, posterUrl = "https://example.com/season2.jpg")
        val state = AppState(savedItems = listOf(root), librarySeasonCards = listOf(second), mediaStates = mapOf(
            mediaStateKey(root, 1) to MediaListState(AnimeStatus.COMPLETED),
            mediaStateKey(root, 2) to MediaListState(AnimeStatus.PLANNED)))
        val entries = LibraryFiltering.entries(state)
        val pending = LibraryFiltering.filter(entries, LibraryFilters(status = AnimeStatus.PLANNED.name)).entries.single()
        assertEquals(second, pending.displayItem)
        assertEquals(root.key, pending.item.key)
        assertEquals(2, pending.item.seasonHint)
        assertEquals(CatalogSource.TMDB, pending.item.source)
        assertEquals(root, LibraryFiltering.filter(entries, LibraryFilters()).entries.single().item)
        assertNull(pending.item.animeEntryId)
    }
    @Test fun severalPendingSeasonsRemainSeparateAndSurviveCardSerialization() {
        val root = series.copy(isAnime = true)
        val cards = listOf(2, 3).map { root.copy(title = "Season $it", seasonHint = it) }
        val restored = CatalogJson.decode(CatalogJson.encode(cards), distinguishSeasons = true)
        assertEquals(cards, restored)
        val entries = LibraryFiltering.entries(AppState(savedItems = listOf(root), librarySeasonCards = restored,
            mediaStates = listOf(2, 3).associate { mediaStateKey(root, it) to MediaListState() }))
        val pending = LibraryFiltering.filter(entries, LibraryFilters(status = AnimeStatus.PLANNED.name)).entries
        assertEquals(listOf(2, 3), pending.map { it.item.seasonHint })
        assertEquals(cards, pending.map { it.displayItem })
    }
    @Test fun completedSeasonDoesNotReappearAsAnAggregatePendingSeries() {
        val anime = series.copy(isAnime = true)
        val entries = LibraryFiltering.entries(AppState(savedItems = listOf(anime), mediaStates = mapOf(
            mediaStateKey(anime, 1) to MediaListState(AnimeStatus.COMPLETED))))
        assertTrue(LibraryFiltering.filter(entries, LibraryFilters(status = AnimeStatus.PLANNED.name)).entries.isEmpty())
        assertEquals(1, LibraryFiltering.filter(entries, LibraryFilters(status = AnimeStatus.COMPLETED.name)).entries.size)
    }
}

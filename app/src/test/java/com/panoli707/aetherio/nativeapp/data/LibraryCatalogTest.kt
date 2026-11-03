package com.panoli707.aetherio.nativeapp.data

import org.junit.Assert.*
import org.junit.Test

class LibraryCatalogTest {
    private val current = MediaItem("900000001", MediaType.SERIES, "Actual", CatalogSource.ANILIST, isAnime = true, episodeCount = 12)
    private val old = current.copy(id = "900000002", title = "Otra temporada")
    private val movie = MediaItem("900000003", MediaType.MOVIE, "Película vista")
    private val progress = WatchProgress(WatchTarget(current, 1, 2), 10000, 1440000, updatedAt = 2)
    private val state = AppState(savedItems = listOf(old), watchingAnime = listOf(current, old), watchProgress = listOf(progress,
        WatchProgress(WatchTarget(movie), watched = true), WatchProgress(WatchTarget(old, 1, 1), 1000, 1440000)),
        animeSeason = AnimeSeasonState(entries = listOf(SeasonalAnime(current, AnimeWeekday.MONDAY, 3, 1793973600)), loaded = true))

    @Test fun libraryIncludesWatchedMoviesOngoingSeriesAndOldFavoritesWithoutDuplicates() {
        assertEquals(setOf(current.key, old.key, movie.key), LibraryCatalog.all(state).map { it.key }.toSet())
        assertEquals(3, LibraryCatalog.all(state).size)
    }
    @Test fun animePageContainsOnlyTrackedCurrentSeasonAndItsPendingPlayback() {
        val currentItems = LibraryCatalog.currentAnime(state)
        assertEquals(listOf(current), currentItems)
        assertEquals(listOf(progress), LibraryCatalog.currentProgress(state, currentItems))
        assertTrue(LibraryCatalog.currentAnime(state.copy(watchingAnime = emptyList())).isEmpty())
    }
    @Test fun finishingASeasonMovesItOutOfAnimeButKeepsItInTheLibrary() {
        val completed = (1..12).map { WatchProgress(WatchTarget(current, 1, it), watched = true) }
        val updated = state.copy(watchProgress = completed)
        assertTrue(LibraryCatalog.currentAnime(updated).isEmpty())
        assertTrue(LibraryCatalog.all(updated).any { it.key == current.key })
    }
    @Test fun watchingAnOldPartDoesNotAutomaticallyFollowTheCurrentPartOfTheSameSeries() {
        val oldRoot = SeriesIdentity.item(MediaItem("220542", MediaType.SERIES, "La boticaria", isAnime = true))
        val third = MediaItem("195516", MediaType.SERIES, "Tercera entrega", CatalogSource.ANILIST, isAnime = true)
        val oldHistory = WatchProgress(WatchTarget(oldRoot, 1, 1), watched = true)
        val mixed = AppState(watchProgress = listOf(oldHistory), watchingAnime = listOf(oldRoot),
            animeSeason = AnimeSeasonState(entries = listOf(SeasonalAnime(third, AnimeWeekday.MONDAY, 6, 1793973600)), loaded = true))
        assertTrue(LibraryCatalog.currentAnime(mixed).isEmpty())
        assertEquals(1, LibraryCatalog.all(mixed).size)
        assertEquals(1, LibraryCatalog.currentAnime(mixed.copy(watchingAnime = listOf(SeriesIdentity.item(third)))).size)
    }
    @Test fun aCurrentSplitCourDoesNotCountThePreviousCoursEpisodesAsCompleted() {
        val part = SeriesIdentity.item(MediaItem("104578", MediaType.SERIES, "Parte 2", CatalogSource.ANILIST,
            isAnime = true, episodeCount = 10))
        assertEquals(3, part.seasonHint)
        assertFalse(LibraryCatalog.matchesCurrent(part, WatchTarget(part, 3, 12)))
        assertTrue(LibraryCatalog.matchesCurrent(part, WatchTarget(part, 3, 13)))
        assertTrue(LibraryCatalog.matchesCurrent(part, WatchTarget(part, 3, 22)))
        assertFalse(LibraryCatalog.matchesCurrent(part, WatchTarget(part, 3, 23)))
    }
}

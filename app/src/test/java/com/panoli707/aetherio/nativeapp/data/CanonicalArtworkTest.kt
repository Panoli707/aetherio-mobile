package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CanonicalArtworkTest {
    private val base = MediaItem("220542", MediaType.SERIES, "The Apothecary Diaries", posterUrl = "https://fixture/printed.jpg",
        logoUrl = "https://fixture/season.png", logoAlternatives = listOf("https://fixture/title.png"))

    @Test fun printedCatalogPosterLoadsWithoutMetadataOcrOrLogoRequestsAndStaysPinnedAcrossSeasons() = runTest {
        val disk = mutableMapOf<String, String>()
        fun resolver() = CanonicalArtworkResolver({ error("must not fetch metadata for a root seed") },
            { _, _, _ -> error("OCR must not gate cover loading") }, disk::get, { key, value -> disk[key] = value })
        val first = resolver()
        val choice = first.resolve(base)
        assertEquals(base.posterUrl, choice.cover)
        assertEquals(ArtworkTitlePresence.UNKNOWN, choice.titlePresence)
        assertNull(choice.logo) // Do not put a second title over a printed poster.
        assertEquals(choice, first.resolve(base.copy(seasonHint = 3, posterUrl = "https://fixture/season3.jpg")))
        assertEquals(choice, resolver().resolve(base.copy(seasonHint = 2)))
        assertEquals(1, disk.size)
    }

    @Test fun savedSeasonArtIsNeverUsedAsTheRootAndCleanMetadataPortraitWins() = runTest {
        var calls = 0
        val resolver = CanonicalArtworkResolver({ calls++; base.copy(portraitArtworkUrl = "https://fixture/clean.jpg",
            portraitTitlePresence = ArtworkTitlePresence.ABSENT) }, { _, _, _ -> error("no OCR in cover path") })
        val choice = resolver.resolve(base.copy(seasonHint = 2, posterUrl = "https://fixture/wrong-season.jpg"))
        assertEquals("https://fixture/clean.jpg", choice.cover)
        assertEquals(ArtworkTitlePresence.ABSENT, choice.titlePresence)
        assertEquals(1, calls)
    }

    @Test fun failedMetadataDoesNotPinAbsenceAndRetriesWhenItRecovers() = runTest {
        var online = false
        val item = base.copy(posterUrl = null)
        val resolver = CanonicalArtworkResolver({ if (!online) error("offline") else base }, { _, _, _ -> null })
        assertNull(resolver.resolve(item).cover)
        online = true
        assertEquals(base.posterUrl, resolver.resolve(item).cover)
    }

    @Test fun emptyMetadataAndTimeoutRemainRetryable() = runTest {
        var calls = 0
        val resolver = CanonicalArtworkResolver({ calls++; if (calls == 1) delay(9_000); base.copy(posterUrl = null) }, { _, _, _ -> null })
        assertNull(resolver.resolve(base.copy(posterUrl = null)).cover)
        assertNull(resolver.resolve(base.copy(posterUrl = null)).cover)
        assertEquals(2, calls)
    }

    @Test fun sameTitleRequestsAreCoalescedAndCancellationDoesNotPersist() = runTest {
        var calls = 0
        var writes = 0
        val gate = CompletableDeferred<Unit>()
        val resolver = CanonicalArtworkResolver({ calls++; gate.await(); base }, { _, _, _ -> null }, write = { _, _ -> writes++ })
        val item = base.copy(posterUrl = null)
        val a = async { resolver.resolve(item) }
        val b = async { resolver.resolve(item.copy(seasonHint = 2)) }
        gate.complete(Unit)
        assertEquals(a.await(), b.await()); assertEquals(1, calls); assertEquals(1, writes)
        val cancelled = CanonicalArtworkResolver({ awaitCancellation() }, { _, _, _ -> null }, write = { _, _ -> error("must not write") })
        val job = launch(start = CoroutineStart.UNDISPATCHED) { cancelled.resolve(item) }
        job.cancelAndJoin()
    }

    @Test fun logoValidationRunsSeparatelyAndRejectsSeasonCandidates() = runTest {
        val inspected = mutableListOf<String>()
        val resolver = CanonicalArtworkResolver({ base }, { url, logo, _ ->
            assertTrue(logo); inspected += url
            ArtworkInspection(3f, genericLogo = url.endsWith("title.png"))
        })
        assertNotNull(resolver.resolve(base).cover)
        assertTrue(inspected.isEmpty())
        assertEquals("https://fixture/title.png", resolver.logo(base))
    }

    @Test fun mappedAnimePartsShareOneSeriesKeyButMoviesStaySeparate() {
        for (id in listOf("161645", "176301", "195516")) assertEquals(base.key,
            CanonicalArtworkIdentity.key(MediaItem(id, MediaType.SERIES, "Any title", CatalogSource.ANILIST)))
        assertNotEquals(CanonicalArtworkIdentity.key(base), CanonicalArtworkIdentity.key(base.copy(type = MediaType.MOVIE)))
    }

    @Test fun logoGuardsKeepRejectingSeasonWordsAndUnrelatedText() {
        for (text in listOf("Series Season 2", "Series Temporada II", "Series III", "Series 2", "Series Cour", "Series 第2期", "Series Shibuya Incident"))
            assertFalse(text, CanonicalArtworkPolicy.genericLogo(text, "Series", true, true))
        assertTrue(CanonicalArtworkPolicy.genericLogo("Series", "Series", true, true))
        assertTrue(CanonicalArtworkPolicy.genericLogo("86", "86", true, true))
        assertFalse(CanonicalArtworkPolicy.genericLogo("Series", "Series", false, true))
        assertFalse(CanonicalArtworkPolicy.genericLogo("Series", "Series", true, false))
    }

    @Test fun oldValidatedRecordsRemainCompatibleAndNewUnknownPresenceRoundTrips() {
        assertNull(CanonicalArtwork.decode("{}"))
        assertNull(CanonicalArtwork.decode("""{"title":"Series","cover":"file:///private","ratio":0.7}"""))
        val legacy = CanonicalArtwork.decode("""{"title":"Series","cover":"https://fixture/clean.jpg","ratio":0.7}""")!!
        assertEquals(ArtworkTitlePresence.ABSENT, legacy.titlePresence)
        val choice = legacy.copy(titlePresence = ArtworkTitlePresence.UNKNOWN)
        assertEquals(choice, CanonicalArtwork.decode(choice.encode()))
        assertEquals(7, CanonicalArtworkCatalog.entries.size)
    }
}

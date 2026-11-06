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

    @Test fun rootLanguageAliasesAreAllowedButSeasonDisplayNamesCannotAuthorizeSeasonBranding() = runTest {
        val root = MediaItem("900001", MediaType.MOVIE, "English title")
        val names = mutableListOf<String>()
        val resolver = CanonicalArtworkResolver({ root.copy(title = "Título español", logoUrl = "https://fixture/title.png") },
            { _, _, title -> names += title; ArtworkInspection(3f, genericLogo = true) })
        assertNotNull(resolver.logo(root))
        assertTrue(names.last().contains("English title"))
        assertNotNull(resolver.logo(root.copy(title = "English title II", seasonHint = 2)))
        assertFalse(names.last().contains("II"))
    }

    @Test fun horizontalSelectionUsesBackdropAndNeverReusesAPersistedVerticalPoster() = runTest {
        val disk = mutableMapOf(base.key to CanonicalArtwork(base.title, base.posterUrl).encode())
        val item = base.copy(backdropUrl = "https://fixture/wide.jpg")
        val resolver = CanonicalArtworkResolver({ error("seed has both formats") }, { _, _, _ -> error("no card OCR") },
            disk::get, { key, value -> disk[key] = value })
        assertEquals(base.posterUrl, resolver.resolve(item).cover)
        assertEquals(item.backdropUrl, resolver.resolve(item, false).cover)
        assertEquals(16f / 9f, resolver.resolve(item, false).ratio)
        assertEquals(2, disk.size)
        assertEquals(item.backdropUrl, resolver.resolve(item.copy(seasonHint = 3), false).cover)
    }

    @Test fun horizontalSelectionFetchesRootBackdropWhenCatalogOnlyHasAPoster() = runTest {
        var calls = 0
        val resolver = CanonicalArtworkResolver({ calls++; base.copy(backdropUrl = "https://fixture/wide.jpg") }, { _, _, _ -> null })
        assertEquals("https://fixture/wide.jpg", resolver.resolve(base, false).cover)
        assertEquals(1, calls)
    }

    @Test fun missingLandscapeNeverFallsBackToAVerticalPosterOrPinsTheAbsence() = runTest {
        val writes = mutableListOf<String>()
        var online = false
        val resolver = CanonicalArtworkResolver({ if (online) base.copy(backdropUrl = "https://fixture/wide.jpg") else error("offline") },
            { _, _, _ -> null }, write = { key, _ -> writes += key })
        assertNull(resolver.resolve(base, false).cover)
        assertTrue(writes.isEmpty())
        online = true
        assertEquals("https://fixture/wide.jpg", resolver.resolve(base, false).cover)
    }

    @Test fun bundledLogoCanBeValidatedWithoutNetworkMetadata() = runTest {
        val resolver = CanonicalArtworkResolver({ error("offline") }, { url, logo, _ ->
            ArtworkInspection(3f, genericLogo = logo && url.startsWith("file:///android_asset/"))
        })
        assertTrue(resolver.logo(base)!!.startsWith("file:///android_asset/"))
    }

    @Test fun cleanUpgradeInspectsAlternativesOnlyAfterCoverAndPersistsItsRealRatio() = runTest {
        val disk = mutableMapOf<String, String>()
        var calls = 0
        val resolver = CanonicalArtworkResolver({ base.copy(portraitAlternatives = listOf("https://fixture/dirty.jpg", "https://fixture/clean.jpg")) },
            { url, logo, _ -> calls++; assertFalse(logo); ArtworkInspection(.7f, clean = url.endsWith("clean.jpg")) },
            disk::get, { key, value -> disk[key] = value })
        val first = resolver.resolve(base)
        assertEquals(0, calls)
        val clean = resolver.refine(base, first, true)
        assertEquals("https://fixture/clean.jpg", clean.cover)
        assertEquals(.7f, clean.ratio)
        assertEquals(ArtworkTitlePresence.ABSENT, clean.titlePresence)
        assertEquals(clean, resolver.resolve(base.copy(seasonHint = 2)))
        assertEquals(clean, CanonicalArtwork.decode(disk[base.key]))
    }

    @Test fun failedOrWrongOrientationUpgradeKeepsExistingCoverAndDoesNotPinFailure() = runTest {
        val resolver = CanonicalArtworkResolver({ base.copy(backdropAlternatives = listOf("https://fixture/vertical.jpg")) },
            { _, _, _ -> ArtworkInspection(.7f, clean = true) })
        val first = resolver.resolve(base)
        assertEquals(first, resolver.refine(base, first, false))
    }

    @Test fun logoGuardsKeepRejectingSeasonWordsAndUnrelatedText() {
        for (text in listOf("Series Season 2", "Series Temporada II", "Series III", "Series 2", "Series Cour", "Series 第2期", "Series Shibuya Incident"))
            assertFalse(text, CanonicalArtworkPolicy.genericLogo(text, "Series", true, true))
        assertTrue(CanonicalArtworkPolicy.genericLogo("Series", "Series", true, true))
        assertTrue(CanonicalArtworkPolicy.genericLogo("86", "86", true, true))
        assertTrue(CanonicalArtworkPolicy.genericLogo("Horrest Gump", "Forrest Gump", true, true))
        assertTrue(CanonicalArtworkPolicy.genericLogo("Korrest Gump", "Forrest Gump", true, true))
        assertFalse(CanonicalArtworkPolicy.genericLogo("Horrast Gump", "Forrest Gump", true, true))
        assertFalse(CanonicalArtworkPolicy.genericLogo("Horrest Gump Tom Hanks", "Forrest Gump", true, true))
        assertTrue(CanonicalArtworkPolicy.genericLogo("Attack on Titan 進撃の巨人", "Attack on Titan\n進撃の巨人", true, true))
        assertFalse(CanonicalArtworkPolicy.genericLogo("Attack on Titan FINAL SEASON", "Attack on Titan\n進撃の巨人", true, true))
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
        assertEquals(13, CanonicalArtworkCatalog.entries.size)
        assertEquals(8, CanonicalArtworkCatalog.landscapes.size)
        assertEquals("TMDB:tv:46260", CanonicalArtworkIdentity.key(MediaItem("20", MediaType.SERIES, "Naruto", CatalogSource.ANILIST)))
    }
}

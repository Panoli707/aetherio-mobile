package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class CanonicalArtworkTest {
    private val base = MediaItem("220542", MediaType.SERIES, "The Apothecary Diaries", posterUrl = "https://fixture/printed.jpg",
        portraitAlternatives = listOf("https://fixture/clean.jpg"), logoUrl = "https://fixture/season.png",
        logoAlternatives = listOf("https://fixture/title.png"))

    @Test fun rejectsPrintedCoversAndSeasonLogosThenPersistsExactlyOnePairAcrossSeasonsAndRestarts() = runTest {
        val disk = mutableMapOf<String, String>()
        val inspected = mutableListOf<String>()
        var metadataCalls = 0
        fun resolver() = CanonicalArtworkResolver({ metadataCalls++; base }, { url, logo, _ ->
            inspected += url
            ArtworkInspection(2f / 3f, clean = !logo && url.endsWith("clean.jpg"), genericLogo = logo && url.endsWith("title.png"))
        }, disk::get, { key, value -> disk[key] = value })
        val first = resolver()
        val chosen = first.resolve(base)
        assertEquals("https://fixture/clean.jpg", chosen.cover)
        assertEquals("https://fixture/title.png", chosen.logo)
        assertEquals(chosen, first.resolve(base.copy(seasonHint = 3, animeEntryId = "195516", posterUrl = "wrong")))
        assertEquals(chosen, resolver().resolve(base.copy(seasonHint = 2)))
        assertEquals(1, metadataCalls)
        assertEquals(1, disk.size)
        assertFalse(inspected.any { it.contains("wrong") })
    }

    @Test fun incompleteOcrAndNetworkFailureNeverBecomeCleanArtworkOrAPermanentNegativeChoice() = runTest {
        var online = false
        var writes = 0
        val resolver = CanonicalArtworkResolver({ if (!online) error("offline") else base }, { url, _, _ ->
            if (url.endsWith("clean.jpg")) ArtworkInspection(.7f, clean = true) else null
        }, write = { _, _ -> writes++ })
        assertNull(resolver.resolve(base).cover)
        online = true
        assertNotNull(resolver.resolve(base).cover)
        assertEquals(1, writes) // A verified cover stays pinned even if the logo falls back to its written name.
        assertFalse(CanonicalArtworkPolicy.clean("", false))
        assertFalse(CanonicalArtworkPolicy.clean("SERIES TITLE", true))
        assertFalse(CanonicalArtworkPolicy.clean("第2期", true))
        assertTrue(CanonicalArtworkPolicy.clean("", true))
    }

    @Test fun sameTitleRequestsAreCoalescedAndCancelledSelectionsDoNotPersist() = runTest {
        var calls = 0
        var writes = 0
        val gate = CompletableDeferred<Unit>()
        val resolver = CanonicalArtworkResolver({ calls++; gate.await(); base.copy(logoUrl = null, logoAlternatives = emptyList()) },
            { _, _, _ -> ArtworkInspection(.7f, clean = true) }, write = { _, _ -> writes++ })
        val a = async { resolver.resolve(base) }
        val b = async { resolver.resolve(base.copy(seasonHint = 2)) }
        gate.complete(Unit)
        assertEquals(a.await(), b.await()); assertEquals(1, calls); assertEquals(1, writes)
        val cancelled = CanonicalArtworkResolver({ awaitCancellation() }, { _, _, _ -> null }, write = { _, _ -> error("must not write") })
        val job = launch(start = CoroutineStart.UNDISPATCHED) { cancelled.resolve(base) }
        job.cancelAndJoin()
    }

    @Test fun mappedAnimePartsShareOneSeriesKeyButMoviesAndUnverifiedLookalikesStaySeparate() {
        val keys = listOf("161645", "176301", "195516").map {
            CanonicalArtworkIdentity.key(MediaItem(it, MediaType.SERIES, "Any title", CatalogSource.ANILIST))
        }
        assertEquals(listOf(base.key, base.key, base.key), keys)
        assertNotEquals(CanonicalArtworkIdentity.key(base), CanonicalArtworkIdentity.key(base.copy(type = MediaType.MOVIE)))
        assertNotEquals(CanonicalArtworkIdentity.key(base), CanonicalArtworkIdentity.key(base.copy(id = "999999999")))
    }

    @Test fun logoGuardsRejectSeasonWordsNumbersRomanNumeralsAndOpaqueOrUnreadableImages() {
        for (text in listOf("Series Season 2", "Series Temporada II", "Series III", "Series 2", "Series Cour", "Series 第2期"))
            assertFalse(text, CanonicalArtworkPolicy.genericLogo(text, "Series", true, true))
        assertTrue(CanonicalArtworkPolicy.genericLogo("Series", "Series", true, true))
        assertTrue(CanonicalArtworkPolicy.genericLogo("86", "86", true, true))
        assertTrue(CanonicalArtworkPolicy.genericLogo("Blade Runner 2049", "Blade Runner 2049", true, true))
        assertFalse(CanonicalArtworkPolicy.genericLogo("Series", "Series", false, true))
        assertFalse(CanonicalArtworkPolicy.genericLogo("Series", "Series", true, false))
        assertFalse(CanonicalArtworkPolicy.genericLogo("Series Shibuya Incident", "Series", true, true))
    }

    @Test fun invalidOrLegacyDiskRecordsCannotReintroduceUnvalidatedArtwork() {
        assertNull(CanonicalArtwork.decode("{}"))
        assertNull(CanonicalArtwork.decode("""{"title":"Series","cover":"file:///private","ratio":0.7}"""))
        assertNull(CanonicalArtwork.decode("""{"title":"Series","cover":"https://cdn/image","ratio":0}"""))
        val value = CanonicalArtwork("Series", "https://cdn/image", .7f, "https://cdn/logo")
        assertEquals(value, CanonicalArtwork.decode(value.encode()))
        assertEquals("file:///android_asset/anime-logos/anilist_1.png",
            CanonicalArtwork.decode(value.copy(logo = "file:///android_asset/anime-logos/anilist_1.png").encode())!!.logo)
    }

    @Test fun reviewedCatalogHasExactlyOneSafePairPerRootWithoutSeasonKeys() {
        assertEquals(7, CanonicalArtworkCatalog.entries.size)
        CanonicalArtworkCatalog.entries.forEach { (key, value) ->
            assertTrue(Regex("TMDB:(movie|tv):[0-9]+").matches(key))
            assertNotNull(value.cover); assertNotNull(value.logo)
            assertEquals(value, CanonicalArtwork.decode(value.encode()))
        }
        assertEquals(CanonicalArtworkCatalog.entries["TMDB:tv:220542"],
            CanonicalArtworkCatalog.entries[CanonicalArtworkIdentity.key(MediaItem("176301", MediaType.SERIES, "Season 2", CatalogSource.ANILIST))])
    }
}

package com.panoli707.aetherio.nativeapp.data

import org.junit.Assert.*
import org.junit.Test

class SourcePreferencesTest {
    private fun source(id: String, language: String? = null, height: Int? = null, provider: String = "fixture") =
        StreamSource(id, provider, provider, id, "", "https://cdn.example/$id.mp4?secret=token",
            headers = mapOf("Authorization" to "secret"),
            display = SourceDisplay(languages = listOfNotNull(language), resolutionHeight = height))

    @Test fun seederLevelsMatchPcBoundariesAndTreatUnknownAsUnknown() {
        assertEquals(SeederLevel.UNKNOWN, SeederLevel.of(null))
        assertEquals(SeederLevel.UNKNOWN, SeederLevel.of(-1))
        assertEquals(SeederLevel.LOW, SeederLevel.of(0))
        assertEquals(SeederLevel.MEDIUM, SeederLevel.of(1))
        assertEquals(SeederLevel.MEDIUM, SeederLevel.of(9))
        assertEquals(SeederLevel.HIGH, SeederLevel.of(10))
        assertEquals(SeederLevel.HIGH, SeederLevel.of(Int.MAX_VALUE))
    }

    @Test fun noPreferencePreservesOriginalOrderAndAllTargets() {
        val sources = listOf(source("a"), source("b", "Español", 2160))
        assertSame(sources, SourcePreferenceRules.order(sources, SourcePreferences()))
    }

    @Test fun priorityIsFavoriteThenLanguageThenExactQualityAndStableTies() {
        val first = source("one", "Inglés", 2160)
        val second = source("two", "Latino", 1080)
        val third = source("three", "Castellano", 2160)
        val fourth = source("four", "Castellano", 2160)
        val favorite = source("fav")
        val input = listOf(first, second, third, fourth, favorite)
        val preferences = SourcePreferences(SourceLanguage.SPANISH, SourceQuality.UHD,
            favoriteSources = setOf(SourcePreferenceRules.sourceKey(favorite)))
        assertEquals(listOf(favorite, third, fourth, second, first), SourcePreferenceRules.order(input, preferences))
        assertEquals(input, listOf(first, second, third, fourth, favorite))
        assertSame(favorite, SourcePreferenceRules.order(input, preferences).first())
    }

    @Test fun exactLowerQualityCanBePreferredOver4kAndUnknownDoesNotDisappear() {
        val sources = listOf(source("4k", height = 2160), source("unknown"), source("720", height = 720))
        assertEquals(listOf(sources[2], sources[0], sources[1]),
            SourcePreferenceRules.order(sources, SourcePreferences(quality = SourceQuality.HD)))
    }

    @Test fun spanishVariantsAreDistinctWhenRequestedButGroupedForSpanish() {
        val input = listOf(source("generic", "Español"), source("es", "Castellano"), source("la", "Latino"))
        assertEquals(input, SourcePreferenceRules.order(input, SourcePreferences(language = SourceLanguage.SPANISH)))
        assertEquals(input[2], SourcePreferenceRules.order(input, SourcePreferences(language = SourceLanguage.LATIN)).first())
        assertEquals(input[1], SourcePreferenceRules.order(input, SourcePreferences(language = SourceLanguage.CASTILIAN)).first())
    }

    @Test fun subtitleLanguageAloneDoesNotMakeAudioMatch() {
        val subbed = source("subbed", "Japonés").copy(subtitles = listOf(StreamSubtitle("es", "https://cdn.example/es.vtt", "es")))
        val dubbed = source("dubbed", "Español")
        assertEquals(listOf(dubbed, subbed), SourcePreferenceRules.order(listOf(subbed, dubbed), SourcePreferences(language = SourceLanguage.SPANISH)))
    }

    @Test fun providerFavoriteAppliesToNewResultsButNotAnUnrelatedProvider() {
        val original = source("old", provider = "alpha")
        val current = source("new", provider = "alpha")
        val unrelated = source("other", provider = "beta")
        val preferences = SourcePreferenceRules.toggleProvider(original, SourcePreferences())
        assertEquals(listOf(current, unrelated), SourcePreferenceRules.order(listOf(unrelated, current), preferences))
        assertEquals(SourcePreferences(), SourcePreferenceRules.toggleProvider(original, preferences))
        assertEquals(listOf(unrelated, current), SourcePreferenceRules.order(listOf(unrelated, current), preferences.copy(favoritesFirst = false)))
    }

    @Test fun torrentFavoriteIgnoresChangingSeedersButNotFileSelection() {
        val torrent = source("one").copy(url = "", infoHash = "0123456789abcdef0123456789abcdef01234567", fileIdx = 0)
        val updated = torrent.copy(id = "new-id", display = SourceDisplay(seeders = 100), description = "new")
        assertEquals(SourcePreferenceRules.sourceKey(torrent), SourcePreferenceRules.sourceKey(updated))
        assertNotEquals(SourcePreferenceRules.sourceKey(torrent), SourcePreferenceRules.sourceKey(torrent.copy(fileIdx = 1)))
        val prefs = SourcePreferenceRules.toggleSource(torrent, SourcePreferences())
        assertTrue(SourcePreferenceRules.favorite(updated, prefs))
        assertEquals(SourcePreferences(), SourcePreferenceRules.toggleSource(updated, prefs))
    }

    @Test fun fingerprintsContainNoCredentialsAndProvidersAreSeparated() {
        val first = source("one")
        assertTrue(SourcePreferenceRules.sourceKey(first).matches(Regex("[a-f0-9]{64}")))
        assertTrue(SourcePreferenceRules.providerKey(first).matches(Regex("[a-f0-9]{64}")))
        assertNotEquals(SourcePreferenceRules.sourceKey(first), SourcePreferenceRules.sourceKey(first.copy(addonId = "other")))
        assertEquals(SourcePreferenceRules.sourceKey(first), SourcePreferenceRules.sourceKey(first.copy(id = "new", description = "updated")))
        assertNotEquals(SourcePreferenceRules.sourceKey(first), SourcePreferenceRules.sourceKey(first.copy(headers = emptyMap())))
    }

    @Test fun badgesRemainCompatibleWhenResolutionWasNotProvided() {
        val old = source("old").copy(display = SourceDisplay(badges = listOf("FHD")))
        assertEquals(1080, SourcePreferenceRules.quality(old))
        assertNull(SourcePreferenceRules.quality(source("hls")))
    }

    @Test fun rankingLargeListsKeepsEveryObjectAndStableTies() {
        val input = (0 until 2000).map { source("$it", if (it % 2 == 0) "Español" else "Japonés") }
        val output = SourcePreferenceRules.order(input, SourcePreferences(language = SourceLanguage.SPANISH))
        assertEquals(input.filterIndexed { index, _ -> index % 2 == 0 } + input.filterIndexed { index, _ -> index % 2 == 1 }, output)
    }
}

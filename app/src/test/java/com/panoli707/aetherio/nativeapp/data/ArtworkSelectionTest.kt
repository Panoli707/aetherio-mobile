package com.panoli707.aetherio.nativeapp.data

import org.junit.Assert.*
import org.junit.Test

class ArtworkSelectionTest {
    @Test fun selectedImageAndExplicitTextSurviveSerialization() {
        for (selection in listOf(ArtworkSelection("https://image.tmdb.org/t/p/w1280/cover.jpg", .66f),
            ArtworkSelection("https://image.tmdb.org/t/p/original/logo.png", 3f), ArtworkSelection(""))) {
            assertEquals(selection, ArtworkSelection.decode(selection.encode()))
        }
        assertNull(ArtworkSelection.decode(null))
        assertNull(ArtworkSelection.decode("broken"))
        assertNull(ArtworkSelection.decode("""{"url":"javascript:alert(1)","ratio":0.66}"""))
        assertNull(ArtworkSelection.decode("""{"url":"https://fixture/cover.jpg","ratio":0}"""))
    }

    @Test fun seriesSeasonsAndVerifiedAnimeAliasesShareSelectionButFilmsDoNot() {
        val series = MediaItem("220542", MediaType.SERIES, "The Apothecary Diaries")
        val key = ArtworkSelectionIdentity.key(series, ArtworkSelectionKind.LOGO)
        assertEquals(key, ArtworkSelectionIdentity.key(series.copy(seasonHint = 4), ArtworkSelectionKind.LOGO))
        assertEquals(key, ArtworkSelectionIdentity.key(MediaItem("161645", MediaType.SERIES, "Otro nombre", CatalogSource.ANILIST), ArtworkSelectionKind.LOGO))
        assertNotEquals(key, ArtworkSelectionIdentity.key(series.copy(type = MediaType.MOVIE), ArtworkSelectionKind.LOGO))
        val film = series.copy(type = MediaType.MOVIE)
        assertNotEquals(ArtworkSelectionIdentity.key(film, ArtworkSelectionKind.LOGO),
            ArtworkSelectionIdentity.key(film.copy(id = "220543"), ArtworkSelectionKind.LOGO))
        assertNotEquals(key, ArtworkSelectionIdentity.key(series, ArtworkSelectionKind.PORTRAIT))
        assertNotEquals(ArtworkSelectionIdentity.key(series, ArtworkSelectionKind.PORTRAIT),
            ArtworkSelectionIdentity.key(series, ArtworkSelectionKind.BACKDROP))
    }

    @Test fun manualLogoMustStillRespectTheGenericNameAndTransparencyPolicy() {
        assertTrue(CanonicalArtworkPolicy.genericLogo("Vinland Saga", "Vinland Saga", true, true))
        assertFalse(CanonicalArtworkPolicy.genericLogo("Vinland Saga Season 2", "Vinland Saga", true, true))
        assertFalse(CanonicalArtworkPolicy.genericLogo("Mob Psycho 100 II", "Mob Psycho 100", true, true))
        assertFalse(CanonicalArtworkPolicy.genericLogo("Vinland Saga", "Vinland Saga", true, false))
        assertFalse(CanonicalArtworkPolicy.clean("TITLE", true))
        assertFalse(CanonicalArtworkPolicy.clean("", false))
    }
}

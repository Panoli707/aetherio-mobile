package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.panoli707.aetherio.nativeapp.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileAndDownloadsUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun localProfileHasBothRealConnectionActionsAndNoUpdatesOrFakeEngineToggles() {
        var connected: AnimeService? = null
        compose.setContent { AetherioTheme {
            ProfileSpace(AppState(profileName = "Local"), {}, PersonalActions(connect = { connected = it }), {}, {})
        } }
        compose.onNodeWithTag("connect:ANILIST").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AnimeService.ANILIST, connected) }
        compose.onNodeWithTag("connect:MAL").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(AnimeService.MAL, connected) }
        compose.onNodeWithText("APP UPDATES").assertDoesNotExist()
        compose.onNodeWithText("Choose Font").assertDoesNotExist()
    }
    @Test fun syncPreferencesCanEnableEachServiceIndependentlyFromLocalOnlyMode() {
        var changed: Pair<AnimeService, Boolean>? = null
        compose.setContent { AetherioTheme {
            val connections = AnimeService.entries.associateWith { service ->
                AnimeConnection(AnimeAccount(if (service == AnimeService.ANILIST) 1 else 2, service.label, null))
            }
            ProfileSpace(AppState(syncServices = emptySet(), animeTracking = AnimeTrackingState(connections = connections)), {},
                PersonalActions(setSyncService = { service, enabled -> changed = service to enabled }), {}, {})
        } }
        compose.onNodeWithTag("syncPreference:ANILIST").assertDoesNotExist()
        compose.onNodeWithText("Preferencias de sincronización").performScrollTo().performClick()
        compose.onNodeWithTag("syncPreferences").assertIsDisplayed()
        compose.onNodeWithText("Los editores de AniList y MAL", substring = true).assertDoesNotExist()
        compose.onNodeWithTag("syncPreference:ANILIST").performScrollTo().assertIsOff().performClick()
        compose.runOnIdle { assertEquals(AnimeService.ANILIST to true, changed) }
        compose.onNodeWithTag("syncPreference:MAL").performScrollTo().assertIsOff().performClick()
        compose.runOnIdle { assertEquals(AnimeService.MAL to true, changed) }
    }
    @Test fun syncPreferenceIsDisabledWhenItsAccountIsNotLinked() {
        compose.setContent { AetherioTheme {
            ProfileSpace(AppState(), {}, PersonalActions(), {}, {})
        } }
        compose.onNodeWithText("Preferencias de sincronización").performScrollTo().performClick()
        compose.onNodeWithTag("syncPreference:ANILIST").performScrollTo().assertIsOff().assertIsNotEnabled()
        compose.onNodeWithTag("syncPreference:MAL").performScrollTo().assertIsOff().assertIsNotEnabled()
    }
    @Test fun linkedStateDisplaysRealAccountCountsAndFavoritesInsteadOfConnectButton() {
        val entries = listOf(AnimeEntry(1, 1, 1, "Favorite anime", progress = 3, status = AnimeStatus.WATCHING, favorite = true),
            AnimeEntry(2, 2, 2, "Completed anime", progress = 12, status = AnimeStatus.COMPLETED))
        val state = AppState(animeTracking = AnimeTrackingState(connections = mapOf(
            AnimeService.ANILIST to AnimeConnection(AnimeAccount(1, "Account A", null), entries, updatedAt = 100),
            AnimeService.MAL to AnimeConnection())))
        compose.setContent { AetherioTheme { ProfileSpace(state, {}, PersonalActions(), {}, {}) } }
        compose.onNodeWithText("Account A").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Conectado").assertExists()
        compose.onNodeWithTag("connect:ANILIST").assertDoesNotExist()
        compose.onNodeWithText("Favoritos (1)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Favorite anime").assertExists()
        compose.onNodeWithText("Completados (1)").performScrollTo().performClick()
        compose.onNodeWithText("Completed anime").assertExists()
    }
    @Test fun batchOnlySelectsReleasedEpisodesAndCanDeselectAll() {
        val item = MediaItem("1", MediaType.SERIES, "Series")
        val episodes = listOf(EpisodeItem(1, 2, "One"), EpisodeItem(2, 2, "Two"), EpisodeItem(3, 2, "Future", released = false))
        var chosen = emptyList<EpisodeItem>()
        compose.setContent { AetherioTheme { BatchDownloadDialog(item, 2, episodes, emptyList(), {}, { chosen = it }) } }
        compose.onNodeWithText("Seleccionar todo").performClick()
        compose.onNodeWithText("Continuar (2)").assertIsEnabled()
        compose.onNodeWithText("Deseleccionar todo").performClick()
        compose.onNodeWithText("Continuar (0)").assertIsNotEnabled()
        compose.onNodeWithText("Seleccionar todo").performClick()
        compose.onNodeWithText("Continuar (2)").performClick()
        compose.runOnIdle { assertEquals(listOf(1, 2), chosen.map { it.number }) }
    }
    @Test fun batchAccessSitsBesideSeasonAndOpensForTheSelectedSeason() {
        val item = MediaItem("1", MediaType.SERIES, "Series")
        val detail = MediaDetail(item, seasons = listOf(SeasonInfo(2, "Temporada 2", 1)), selectedSeason = 2, episodes = listOf(EpisodeItem(1, 2, "Episode")))
        compose.setContent { AetherioTheme {
            DetailScreen(AppState(detail = detail), item, rememberLazyListState(), {}, {}, {}, {}, {}, { _, _, _ -> })
        } }
        compose.onNodeWithTag("detailScroll").performScrollToNode(hasTestTag("downloadSeason"))
        compose.onNodeWithTag("downloadSeason").performClick()
        compose.onNodeWithTag("batchDownloads").assertIsDisplayed()
        compose.onNodeWithText("Series · Temporada 2").assertExists()
    }
}

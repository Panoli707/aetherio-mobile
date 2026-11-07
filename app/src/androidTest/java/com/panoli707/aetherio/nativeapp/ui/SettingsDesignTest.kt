package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.panoli707.aetherio.nativeapp.data.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsDesignTest {
    @get:Rule val compose = createComposeRule()

    @Test fun overviewHasOnlyRealAetherioSectionsAndDoesNotExposeAllFormsAtOnce() {
        compose.setContent { AetherioTheme {
            SettingsScreen(AppState(addonsLoading = false), {}, {}, {}, {}, { _, _ -> }, UpdateState(), {}, {})
        } }
        compose.onNodeWithText("Configuración").assertIsDisplayed()
        listOf("PROFILE", "SOURCES", "PLAYBACK", "UPDATES", "ABOUT").forEach { compose.onNodeWithTag("setting:$it").assertExists() }
        listOf("Cuenta", "Cambiar perfil", "Ver en grupo", "Notificaciones", "Integraciones", "Appearance", "Layout").forEach {
            compose.onNodeWithText(it).assertDoesNotExist()
        }
        compose.onNodeWithText("Nombre del perfil").assertDoesNotExist()
        compose.onNodeWithText("URL HTTPS del manifest.json").assertDoesNotExist()
        compose.onNodeWithContentDescription("Actualizaciones automáticas").assertDoesNotExist()
    }

    @Test fun searchIgnoresAccentsAndProfileCanBeSavedAndReturnedToTheSameSearch() {
        val state = mutableStateOf(AppState(profileName = "Original"))
        compose.setContent { AetherioTheme {
            SettingsScreen(state.value, { state.value = state.value.copy(profileName = it) }, {}, {}, {}, { _, _ -> }, UpdateState(), {}, {})
        } }
        compose.onNodeWithTag("settingsSearch").performTextInput("perfíl")
        compose.onNodeWithTag("setting:SOURCES").assertDoesNotExist()
        compose.onNodeWithTag("setting:PROFILE").performClick()
        compose.onNodeWithTag("editProfile").performScrollTo().performClick()
        compose.onNodeWithText("Nombre local").performTextReplacement("Mi espacio")
        compose.onNodeWithText("Guardar nombre").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("Mi espacio", state.value.profileName) }
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithContentDescription("Volver").performScrollTo().performClick()
        compose.onNodeWithTag("settingsSearch").assertTextContains("perfíl")
        compose.onNodeWithTag("setting:SOURCES").assertDoesNotExist()
        compose.onNodeWithContentDescription("Borrar búsqueda de ajustes").performClick()
        compose.onNodeWithTag("setting:SOURCES").assertExists()
    }

    @Test fun sourcesAreOnlyChangedInsideTheirPageAndSystemBackReturnsToOverview() {
        val state = mutableStateOf(AppState(addonsLoading = false))
        var changed: Pair<String, Boolean>? = null
        compose.setContent { AetherioTheme {
            SettingsScreen(state.value, {}, {}, {}, {}, { id, enabled ->
                changed = id to enabled
                state.value = state.value.copy(enabledDesktopSources = if (enabled) state.value.enabledDesktopSources + id else state.value.enabledDesktopSources - id)
            }, UpdateState(), {}, {})
        } }
        compose.runOnIdle { assertNull(changed) }
        compose.onNodeWithTag("setting:SOURCES").performScrollTo().performClick()
        compose.onNodeWithText("Aetherio Provider Hub").assertExists()
        compose.onNodeWithContentDescription("AnimeAV1").performScrollTo().assertIsOff().performClick()
        compose.runOnIdle { assertEquals("animeav1" to true, changed) }
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("setting:SOURCES").assertExists()
        compose.onNodeWithTag("setting:SOURCES").performScrollTo().performClick()
        compose.onNodeWithContentDescription("AnimeAV1").performScrollTo().assertIsOn()
    }

    @Test fun playbackPageDispatchesExistingLinkActionWithoutInventedPreferences() {
        var linked = false
        compose.setContent { AetherioTheme {
            SettingsScreen(AppState(), {}, { linked = true }, {}, {}, { _, _ -> }, UpdateState(), {}, {})
        } }
        compose.onNodeWithTag("setting:PLAYBACK").performScrollTo().performClick()
        compose.onNodeWithText("Reproductor integrado").assertIsDisplayed()
        compose.onAllNodes(isToggleable()).assertCountEquals(0)
        compose.onNodeWithText("Reproducir por enlace").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(linked) }
    }

    @Test fun updatesStillUseExistingActionsAndAboutStillOpensRealLicenses() {
        var action: UpdateAction? = null
        var automatic: Boolean? = null
        compose.setContent { AetherioTheme {
            SettingsScreen(AppState(), {}, {}, {}, {}, { _, _ -> }, UpdateState(), { action = it }, { automatic = it })
        } }
        compose.onNodeWithTag("setting:UPDATES").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Actualizaciones automáticas").assertIsOff().performClick()
        compose.onNodeWithText("Buscar actualizaciones").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, automatic); assertEquals(UpdateAction.CHECK, action) }
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithTag("setting:ABOUT").performScrollTo().performClick()
        compose.onNodeWithText("Licencias y avisos de terceros").performScrollTo().performClick()
        compose.onNodeWithText("Licencias de software").assertIsDisplayed()
    }

    @Test fun unknownSearchShowsAUsefulEmptyStateWithoutNewSettings() {
        compose.setContent { AetherioTheme {
            SettingsScreen(AppState(), {}, {}, {}, {}, { _, _ -> }, UpdateState(), {}, {})
        } }
        compose.onNodeWithTag("settingsSearch").performTextInput("watch party")
        compose.onNodeWithText("Sin ajustes coincidentes").assertExists()
        compose.onNodeWithTag("setting:PROFILE").assertDoesNotExist()
        compose.onNodeWithContentDescription("Borrar búsqueda de ajustes").performClick()
        compose.onNodeWithTag("setting:PROFILE").assertExists()
    }
}

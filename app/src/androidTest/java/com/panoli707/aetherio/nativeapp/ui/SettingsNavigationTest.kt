package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.geometry.Offset
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.panoli707.aetherio.nativeapp.data.AppState
import com.panoli707.aetherio.nativeapp.data.UpdateState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsNavigationTest {
    @get:Rule val compose = createComposeRule()

    private fun showApp(restoration: StateRestorationTester? = null) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            AetherioApp(AppState(homeLoading = false, addonsLoading = false), UpdateState(),
                onSelectMedia = {}, onToggleSaved = {}, onSearch = {}, onRetryHome = {}, onRetryDetail = {},
                onSelectSeason = {}, onRenameProfile = {}, onOpenVideo = { _, _ -> }, onAddAddon = {},
                onRemoveAddon = {}, onDesktopSourceToggle = { _, _ -> }, onRequestSources = { _, _, _ -> },
                onDismissSources = {}, onPlaySource = { _, _ -> }, onUpdateAction = {}, onAutomaticUpdatesChange = {})
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
        tab("Ajustes")
    }

    private fun tab(title: String) {
        compose.onNode(hasText(title) and hasClickAction()).performTouchInput { click() }
    }

    private fun open(section: String) {
        // The floating bottom bar can overlap the centre of the last overview row.
        compose.onNodeWithTag("setting:$section").performScrollTo().performTouchInput { click(topCenter + Offset(0f, 24f)) }
        compose.onNodeWithText("Configuración").assertDoesNotExist()
        compose.onNodeWithContentDescription("Volver").assertExists()
    }

    @Test fun everySectionOpensOnItsFirstTouchWithoutLeavingSettings() {
        showApp()
        val pages = listOf("PROFILE" to "Nombre del perfil", "DESIGN" to "Contenido principal", "SOURCES" to "Aetherio Provider Hub",
            "PLAYBACK" to "Reproductor integrado", "UPDATES" to "Buscar actualizaciones", "ABOUT" to "Licencias y avisos de terceros")
        pages.forEach { (section, content) ->
            open(section)
            compose.onNodeWithText(content).performScrollTo().assertIsDisplayed()
            compose.onNodeWithContentDescription("Volver").performScrollTo().performTouchInput { click() }
            compose.onNodeWithText("Configuración").assertExists()
        }
    }

    @Test fun sectionAndUnsavedFormSurviveChangingTabsAndRestoringState() {
        val restoration = StateRestorationTester(compose)
        showApp(restoration)
        open("PROFILE")
        compose.onNodeWithText("Nombre del perfil").performTextReplacement("Borrador local")
        compose.onNodeWithText("Nombre del perfil").performImeAction()
        Espresso.closeSoftKeyboard()
        tab("Biblioteca")
        tab("Ajustes")
        compose.onNodeWithText("Configuración").assertDoesNotExist()
        compose.onNodeWithText("Nombre del perfil").assertTextContains("Borrador local")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Nombre del perfil").assertTextContains("Borrador local")
        Espresso.pressBack()
        compose.onNodeWithText("Configuración").assertExists()
    }
}

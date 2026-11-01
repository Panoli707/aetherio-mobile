package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.panoli707.aetherio.nativeapp.data.AppState
import com.panoli707.aetherio.nativeapp.data.UpdateState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun initialLoadingShowsSplashAndLaterLoadingDoesNotRestoreIt() {
        val state = mutableStateOf(AppState(
            homeLoading = true,
            sections = emptyList(),
            homeError = null,
            addonsLoading = false
        ))
        render(state)

        compose.onNodeWithContentDescription("Logo de Aetherio").assertIsDisplayed()
        compose.onNodeWithText("Aetherio").assertIsDisplayed()
        compose.onNodeWithText("Cargando\u2026").assertIsDisplayed()
        compose.onNodeWithText("Ajustes").assertDoesNotExist()

        compose.runOnIdle { state.value = state.value.copy(homeLoading = false) }

        compose.onNodeWithContentDescription("Logo de Aetherio").assertDoesNotExist()
        compose.onNodeWithText("Cargando\u2026").assertDoesNotExist()
        compose.onNodeWithText("Ajustes").assertIsDisplayed()

        compose.runOnIdle { state.value = state.value.copy(homeLoading = true) }

        compose.onNodeWithContentDescription("Logo de Aetherio").assertDoesNotExist()
        compose.onNodeWithText("Cargando\u2026").assertDoesNotExist()
        compose.onNodeWithText("Ajustes").assertIsDisplayed()
    }

    @Test fun initialLoadingErrorDismissesSplashAndShowsRetry() {
        val state = mutableStateOf(AppState(
            homeLoading = true,
            sections = emptyList(),
            homeError = null,
            addonsLoading = false
        ))
        render(state)

        compose.onNodeWithContentDescription("Logo de Aetherio").assertIsDisplayed()
        compose.onNodeWithText("Ajustes").assertDoesNotExist()

        compose.runOnIdle {
            state.value = state.value.copy(
                homeLoading = false,
                homeError = "No se pudo cargar el contenido."
            )
        }

        compose.onNodeWithContentDescription("Logo de Aetherio").assertDoesNotExist()
        compose.onNodeWithText("Cargando\u2026").assertDoesNotExist()
        compose.onNodeWithText("Reintentar").assertIsDisplayed()
    }

    @Test fun initiallyReadyStateShowsNavigationAfterIdleWithoutForcedDelay() {
        val state = mutableStateOf(AppState(homeLoading = false, addonsLoading = false))
        render(state)
        compose.waitForIdle()

        compose.onNodeWithContentDescription("Logo de Aetherio").assertDoesNotExist()
        compose.onNodeWithText("Cargando\u2026").assertDoesNotExist()
        compose.onNodeWithText("Ajustes").assertIsDisplayed()
    }

    private fun render(state: State<AppState>) {
        compose.setContent {
            AetherioTheme {
                AetherioApp(
                    state = state.value,
                    updates = UpdateState(),
                    onUpdateAction = {},
                    onAutomaticUpdatesChange = {},
                    onSelectMedia = {},
                    onToggleSaved = {},
                    onSearch = {},
                    onRetryHome = {},
                    onRetryDetail = {},
                    onSelectSeason = {},
                    onRenameProfile = {},
                    onOpenVideo = { _, _ -> },
                    onAddAddon = {},
                    onRemoveAddon = {},
                    onRequestSources = { _, _, _ -> },
                    onDismissSources = {},
                    onPlaySource = { _, _ -> }
                )
            }
        }
    }
}

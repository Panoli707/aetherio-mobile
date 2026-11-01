package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.panoli707.aetherio.nativeapp.data.AppState
import com.panoli707.aetherio.nativeapp.data.AppUpdate
import com.panoli707.aetherio.nativeapp.data.MediaItem
import com.panoli707.aetherio.nativeapp.data.MediaType
import com.panoli707.aetherio.nativeapp.data.SourceQuery
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateDialogTest {
    @get:Rule val compose = createComposeRule()

    private val update = AppUpdate(
        versionCode = 200,
        versionName = "2.0.0-preview.1",
        releaseUrl = "https://github.com/Panoli707/aetherio-mobile/releases/tag/v2.0.0-preview.1",
        prerelease = true
    )

    @Test fun installedApplicationNameIsAetherio() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("Aetherio", context.packageManager.getApplicationLabel(context.applicationInfo).toString())
    }

    @Test fun prereleaseShowsVersionAndPreliminaryIndicator() {
        compose.setContent {
            AetherioTheme {
                UpdateDialog(update, onDismiss = {}, onOpen = {})
            }
        }

        compose.onNodeWithText("Actualizaci\u00f3n disponible").assertIsDisplayed()
        compose.onNodeWithText("Aetherio 2.0.0-preview.1").assertIsDisplayed()
        compose.onNodeWithText("Versi\u00f3n preliminar").assertIsDisplayed()
    }

    @Test fun stableShowsVersionWithoutPreliminaryIndicator() {
        val stable = update.copy(versionName = "2.0.0", prerelease = false)
        compose.setContent {
            AetherioTheme {
                UpdateDialog(stable, onDismiss = {}, onOpen = {})
            }
        }

        compose.onNodeWithText("Aetherio 2.0.0").assertIsDisplayed()
        compose.onNodeWithText("Versi\u00f3n preliminar").assertDoesNotExist()
        compose.onNodeWithText("Aetherio 2.0.0-preview.1").assertDoesNotExist()
    }

    @Test fun ahoraNoDismissesAndUnrelatedStateChangeDoesNotReopenUpdate() {
        val state = mutableStateOf(AppState(
            homeLoading = false, addonsLoading = false, availableUpdate = update
        ))
        var dismissCalls = 0
        var openCalls = 0
        setAppContent(state, onDismissUpdate = {
            dismissCalls++
            state.value = state.value.copy(availableUpdate = null)
        }, onOpenUpdate = { openCalls++ })

        compose.onNodeWithText("Actualizaci\u00f3n disponible").assertIsDisplayed()
        compose.onNodeWithText("Ahora no").performClick()
        compose.onNodeWithText("Actualizaci\u00f3n disponible").assertDoesNotExist()
        compose.onNodeWithText("Ahora no").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, dismissCalls)
            assertEquals(0, openCalls)
            assertNull(state.value.availableUpdate)
            state.value = state.value.copy(profileName = "Updated offline profile")
        }

        compose.onNodeWithText("Actualizaci\u00f3n disponible").assertDoesNotExist()
        compose.onNodeWithText("Ver actualizaci\u00f3n").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, dismissCalls) }
    }

    @Test fun verActualizacionForwardsSuppliedUpdateAndReleaseUrlWithoutBrowser() {
        val state = mutableStateOf(AppState(
            homeLoading = false, addonsLoading = false, availableUpdate = update
        ))
        var opened: AppUpdate? = null
        var openCalls = 0
        var dismissCalls = 0
        setAppContent(state, onDismissUpdate = { dismissCalls++ }, onOpenUpdate = {
            opened = it
            openCalls++
        })

        compose.onNodeWithText("Ver actualizaci\u00f3n").performClick()

        compose.runOnIdle {
            assertEquals(1, openCalls)
            assertEquals(0, dismissCalls)
            assertSame(update, opened)
            assertEquals(
                "https://github.com/Panoli707/aetherio-mobile/releases/tag/v2.0.0-preview.1",
                opened?.releaseUrl
            )
        }
    }

    @Test fun updateWaitsForSourceSheetToCloseInsteadOfOverlapping() {
        val dummyMovie = MediaItem(id = "offline-movie", type = MediaType.MOVIE, title = "Offline movie")
        val state = mutableStateOf(AppState(
            homeLoading = false,
            addonsLoading = false,
            sourceQuery = SourceQuery(dummyMovie),
            sourcesLoading = false,
            availableUpdate = update
        ))
        setAppContent(state)

        compose.onNodeWithText(dummyMovie.title).assertIsDisplayed()
        compose.onNodeWithText("Fuentes de reproducci\u00f3n").assertIsDisplayed()
        compose.onNodeWithText("Actualizaci\u00f3n disponible").assertDoesNotExist()
        compose.onNodeWithText("Ver actualizaci\u00f3n").assertDoesNotExist()

        compose.runOnIdle { state.value = state.value.copy(sourceQuery = null) }

        compose.onNodeWithText(dummyMovie.title).assertDoesNotExist()
        compose.onNodeWithText("Fuentes de reproducci\u00f3n").assertDoesNotExist()
        compose.onNodeWithText("Actualizaci\u00f3n disponible").assertIsDisplayed()
        compose.onNodeWithText("Aetherio 2.0.0-preview.1").assertIsDisplayed()
        compose.onNodeWithText("Ver actualizaci\u00f3n").assertIsDisplayed()
    }

    private fun setAppContent(
        state: State<AppState>,
        onDismissUpdate: () -> Unit = {},
        onOpenUpdate: (AppUpdate) -> Unit = {}
    ) {
        compose.setContent {
            AetherioApp(
                state = state.value,
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
                onPlaySource = { _, _ -> },
                onDismissUpdate = onDismissUpdate,
                onOpenUpdate = onOpenUpdate
            )
        }
    }
}

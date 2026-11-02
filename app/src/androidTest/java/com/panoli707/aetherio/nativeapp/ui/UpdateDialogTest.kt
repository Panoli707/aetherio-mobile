package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.panoli707.aetherio.nativeapp.BuildConfig
import com.panoli707.aetherio.nativeapp.data.AppState
import com.panoli707.aetherio.nativeapp.data.AppUpdate
import com.panoli707.aetherio.nativeapp.data.MediaItem
import com.panoli707.aetherio.nativeapp.data.MediaType
import com.panoli707.aetherio.nativeapp.data.SourceQuery
import com.panoli707.aetherio.nativeapp.data.UpdateAction
import com.panoli707.aetherio.nativeapp.data.UpdateState
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
        releaseUrl = "https://example.invalid/releases/v2.0.0-preview.1",
        prerelease = true,
        apkUrl = "https://example.invalid/aetherio.apk",
        sha256 = "a".repeat(64),
        size = 1_024L
    )

    @Test fun dialogShowsReleaseChannelAndDownloadsWithoutOpeningBrowser() {
        val updates = mutableStateOf(UpdateState(available = update))
        val actions = mutableListOf<UpdateAction>()
        compose.setContent { AetherioTheme { UpdateDialog(updates.value, actions::add) } }

        compose.onNodeWithText("Actualizaci\u00f3n disponible").assertIsDisplayed()
        compose.onNodeWithText("Aetherio 2.0.0-preview.1").assertIsDisplayed()
        compose.onNodeWithText("Versi\u00f3n preliminar").assertIsDisplayed()
        compose.onNodeWithText("Android te pedir\u00e1 confirmar la instalaci\u00f3n.").assertIsDisplayed()
        compose.onNodeWithText("GitHub", substring = true, ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithText("https://", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Actualizar ahora").performClick()
        compose.runOnIdle {
            assertEquals(listOf(UpdateAction.DOWNLOAD), actions)
            updates.value = updates.value.copy(available = update.copy(versionName = "2.0.0", prerelease = false))
        }
        compose.onNodeWithText("Aetherio 2.0.0").assertIsDisplayed()
        compose.onNodeWithText("Versi\u00f3n preliminar").assertDoesNotExist()

        compose.runOnIdle { updates.value = UpdateState() }
        compose.onNodeWithText("Actualizaci\u00f3n disponible").assertDoesNotExist()
        compose.onNodeWithText("Actualizar ahora").assertDoesNotExist()
    }

    @Test fun readyDialogRequestsInstallAndWaitsForAndroidConfirmation() {
        val updates = mutableStateOf(UpdateState(available = update, ready = true))
        val actions = mutableListOf<UpdateAction>()
        compose.setContent {
            AetherioTheme {
                UpdateDialog(updates.value) { action ->
                    actions.add(action)
                    if (action == UpdateAction.INSTALL) updates.value = updates.value.copy(installRequested = true)
                }
            }
        }

        compose.onNodeWithText("Actualizar ahora").assertDoesNotExist()
        compose.onNodeWithText("Instalar actualizaci\u00f3n").performClick()
        compose.onNodeWithText("Confirma la instalaci\u00f3n en Android").assertIsDisplayed()
        compose.onNodeWithText("Instalar actualizaci\u00f3n").assertDoesNotExist()
        compose.onNodeWithText("Ocultar").assertIsEnabled()
        compose.runOnIdle { assertEquals(listOf(UpdateAction.INSTALL), actions) }
    }

    @Test fun busyDialogShowsPercentWaitingAndVerificationAndCanCancel() {
        val updates = mutableStateOf(UpdateState(available = update, downloading = true, progress = 64))
        val actions = mutableListOf<UpdateAction>()
        compose.setContent { AetherioTheme { UpdateDialog(updates.value, actions::add) } }

        compose.onNodeWithText("Actualizando Aetherio").assertIsDisplayed()
        compose.onNodeWithText("Descargando actualizaci\u00f3n: 64%").assertIsDisplayed()
        compose.onNode(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo(0.64f, 0f..1f)
        )).assertIsDisplayed()
        compose.onNodeWithText("Actualizar ahora").assertDoesNotExist()
        compose.onNodeWithText("Cancelar descarga").performClick()
        compose.runOnIdle {
            assertEquals(listOf(UpdateAction.CANCEL), actions)
            updates.value = updates.value.copy(waiting = true)
        }
        compose.onNodeWithText("Esperando conexi\u00f3n para descargar").assertIsDisplayed()
        compose.onNodeWithText("Descargando actualizaci\u00f3n: 64%").assertDoesNotExist()
        compose.onNode(SemanticsMatcher.expectValue(
            SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate
        )).assertIsDisplayed()

        compose.runOnIdle { updates.value = updates.value.copy(downloading = false, waiting = false, verifying = true) }
        compose.onNodeWithText("Verificando actualizaci\u00f3n").assertIsDisplayed()
        compose.onNodeWithText("Cancelar descarga").assertIsEnabled()
    }

    @Test fun settingsShowsInstalledVersionAndControlledAutomaticSwitchAndManualCheck() {
        val updates = mutableStateOf(UpdateState(message = "No hay actualizaciones disponibles."))
        val actions = mutableListOf<UpdateAction>()
        val automaticChanges = mutableListOf<Boolean>()
        compose.setContent {
            AetherioTheme {
                UpdateSettings(updates.value, actions::add) { enabled ->
                    automaticChanges.add(enabled)
                    updates.value = updates.value.copy(automatic = enabled)
                }
            }
        }

        compose.onNodeWithText("Actualizaciones").assertIsDisplayed()
        compose.onNodeWithText("Versi\u00f3n instalada: ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        compose.onNodeWithText(
            "Incluye versiones preliminares. Descarga al abrir la app en redes no medidas; t\u00fa decides cu\u00e1ndo instalar."
        ).assertIsDisplayed()
        val automaticSwitch = compose.onNodeWithContentDescription("Actualizaciones autom\u00e1ticas")
        automaticSwitch.assertIsOff().performClick()
        automaticSwitch.assertIsOn().performClick()
        automaticSwitch.assertIsOff()
        compose.onAllNodesWithText("No hay actualizaciones disponibles.").assertCountEquals(1)
        compose.onNodeWithText("Buscar actualizaciones").performClick()
        compose.runOnIdle {
            assertEquals(listOf(true, false), automaticChanges)
            assertEquals(listOf(UpdateAction.CHECK), actions)
            updates.value = updates.value.copy(checking = true, message = null)
        }
        compose.onNodeWithText("Buscando actualizaciones").assertIsDisplayed()
        compose.onNodeWithText("Buscar actualizaciones").assertIsNotEnabled()
        compose.onNodeWithText("Cancelar descarga").assertDoesNotExist()
    }

    @Test fun settingsSupportsDownloadCancelAndReadyInstall() {
        val updates = mutableStateOf(UpdateState(available = update))
        val actions = mutableListOf<UpdateAction>()
        compose.setContent { AetherioTheme { UpdateSettings(updates.value, actions::add, {}) } }

        compose.onNodeWithText("Aetherio 2.0.0-preview.1").assertIsDisplayed()
        compose.onNodeWithText("Versi\u00f3n preliminar").assertIsDisplayed()
        compose.onNodeWithText("Actualizar ahora").performClick()
        compose.runOnIdle { updates.value = updates.value.copy(downloading = true, progress = 25) }
        compose.onNodeWithText("Descargando actualizaci\u00f3n: 25%").assertIsDisplayed()
        compose.onNodeWithText("Buscar actualizaciones").assertIsNotEnabled()
        compose.onNodeWithText("Cancelar descarga").performClick()
        compose.runOnIdle { updates.value = updates.value.copy(downloading = false, ready = true) }
        compose.onNodeWithText("Cancelar descarga").assertDoesNotExist()
        compose.onNodeWithText("Instalar actualizaci\u00f3n").performClick()
        compose.runOnIdle {
            assertEquals(listOf(UpdateAction.DOWNLOAD, UpdateAction.CANCEL, UpdateAction.INSTALL), actions)
            updates.value = updates.value.copy(ready = false, error = "Download failed: ${update.apkUrl}")
        }
        compose.onAllNodesWithText("No se pudo completar la actualizaci\u00f3n. Vuelve a intentarlo.").assertCountEquals(1)
        compose.onNodeWithText("https://", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle { assertEquals(UpdateAction.DOWNLOAD, actions.last()) }
    }

    @Test fun dialogShowsOneGenericErrorAndRetryThenOneStatusMessage() {
        val updates = mutableStateOf(UpdateState(
            available = update,
            error = "Download failed: ${update.apkUrl}",
            message = "Mensaje anterior"
        ))
        val actions = mutableListOf<UpdateAction>()
        compose.setContent { AetherioTheme { UpdateDialog(updates.value, actions::add) } }

        compose.onAllNodesWithText("No se pudo completar la actualizaci\u00f3n. Vuelve a intentarlo.").assertCountEquals(1)
        compose.onNodeWithText("https://", substring = true).assertDoesNotExist()
        compose.onNodeWithText("Mensaje anterior").assertDoesNotExist()
        compose.onNodeWithText("Reintentar").performClick()
        compose.runOnIdle {
            assertEquals(listOf(UpdateAction.DOWNLOAD), actions)
            updates.value = updates.value.copy(error = null, message = "Descarga cancelada.")
        }
        compose.onAllNodesWithText("Descarga cancelada.").assertCountEquals(1)
        compose.onNodeWithText("Reintentar").assertDoesNotExist()
    }

    @Test fun hidingBusyNoticeRetainsDownloadAndUnrelatedStateDoesNotReopenIt() {
        val state = mutableStateOf(AppState(homeLoading = false, addonsLoading = false))
        val updates = mutableStateOf(UpdateState(
            available = update, noticeVisible = true, downloading = true, progress = 42
        ))
        val actions = mutableListOf<UpdateAction>()
        setAppContent(state, updates, onUpdateAction = { action ->
            actions.add(action)
            if (action == UpdateAction.DISMISS) updates.value = updates.value.copy(noticeVisible = false)
        })

        compose.onNodeWithText("Actualizando Aetherio").assertIsDisplayed()
        compose.onNodeWithText("Ocultar").performClick()
        compose.onNodeWithText("Actualizando Aetherio").assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(listOf(UpdateAction.DISMISS), actions)
            assertFalse(updates.value.noticeVisible)
            assertSame(update, updates.value.available)
            assertTrue(updates.value.downloading)
            assertEquals(42, updates.value.progress)
            state.value = state.value.copy(profileName = "Updated offline profile")
        }
        compose.onNodeWithText("Ocultar").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(UpdateAction.DISMISS), actions) }
    }

    @Test fun noticeWaitsForSourceSheetAndRequiresNoticeVisible() {
        val dummyMovie = MediaItem(id = "offline-movie", type = MediaType.MOVIE, title = "Offline movie")
        val state = mutableStateOf(AppState(
            homeLoading = false,
            addonsLoading = false,
            sourceQuery = SourceQuery(dummyMovie),
            sourcesLoading = false
        ))
        val updates = mutableStateOf(UpdateState(available = update, noticeVisible = true))
        setAppContent(state, updates)

        compose.onNodeWithText(dummyMovie.title).assertIsDisplayed()
        compose.onNodeWithText("Fuentes de reproducci\u00f3n").assertIsDisplayed()
        compose.onNodeWithText("Actualizaci\u00f3n disponible").assertDoesNotExist()
        compose.runOnIdle { state.value = state.value.copy(sourceQuery = null) }
        compose.onNodeWithText("Fuentes de reproducci\u00f3n").assertDoesNotExist()
        compose.onNodeWithText("Actualizaci\u00f3n disponible").assertIsDisplayed()
        compose.onNodeWithText("Actualizar ahora").assertIsDisplayed()

        compose.runOnIdle { updates.value = updates.value.copy(noticeVisible = false) }
        compose.onNodeWithText("Actualizaci\u00f3n disponible").assertDoesNotExist()
    }

    private fun setAppContent(
        state: State<AppState>,
        updates: State<UpdateState>,
        onUpdateAction: (UpdateAction) -> Unit = {}
    ) {
        compose.setContent {
            AetherioTheme {
                AetherioApp(
                    state = state.value,
                    updates = updates.value,
                    onUpdateAction = onUpdateAction,
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
                    onDesktopSourceToggle = { _, _ -> },
                    onRequestSources = { _, _, _ -> },
                    onDismissSources = {},
                    onPlaySource = { _, _ -> }
                )
            }
        }
    }
}

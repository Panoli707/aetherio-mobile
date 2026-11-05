package com.panoli707.aetherio.nativeapp.player

import androidx.activity.OnBackPressedDispatcher
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class PlayerScreenTest {
    @get:Rule val compose = createComposeRule()

    private val playback = VideoUiState(
        title = "Pelicula sin conexion", subtitle = "Proveedor local / Episodio 2",
        positionMs = 30_000, durationMs = 120_000, seekable = true
    )
    private val actions = mutableListOf<VideoAction>()
    private val seeks = mutableListOf<Long>()
    private val speeds = mutableListOf<Float>()
    private val tracks = mutableListOf<String>()
    private val volumes = mutableListOf<Float>()
    private lateinit var back: OnBackPressedDispatcher

    @Test fun openingUsesExactTimingsOrClearlyLabelledManualAdvanceAndRespectsLock() {
        val state = mutableStateOf(playback.copy(animeEpisode = true, durationMs = 1440000))
        render(state)
        compose.onNodeWithText("Opening · +90 s").assertIsDisplayed().performClick()
        compose.runOnIdle { state.value = state.value.copy(opening = OpeningInterval(20000, 110000)) }
        compose.onNodeWithText("Saltar opening").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Bloquear controles").performClick()
        compose.onNodeWithText("Saltar opening").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(VideoAction.SKIP_OPENING, VideoAction.SKIP_OPENING), actions) }
    }

    @Test fun transportRotationSourceAndCloseOnlyDispatchActions() {
        val state = mutableStateOf(playback)
        render(state)
        compose.onNodeWithText(playback.title).assertIsDisplayed()
        compose.onNodeWithText(playback.subtitle).assertIsDisplayed()
        compose.onNodeWithText("1080p").assertDoesNotExist()
        compose.onNodeWithContentDescription("Reproducir").performClick()
        compose.onNodeWithContentDescription("Retroceder 10 segundos").performClick()
        compose.onNodeWithContentDescription("Avanzar 10 segundos").performClick()
        compose.onNodeWithContentDescription("Girar pantalla").performClick()
        compose.onNodeWithContentDescription("Cambiar fuente").performClick()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.runOnIdle {
            assertEquals(listOf(VideoAction.TOGGLE_PLAY, VideoAction.REWIND, VideoAction.FORWARD,
                VideoAction.ROTATE, VideoAction.FIND_SOURCES, VideoAction.CLOSE), actions)
            state.value = playback.copy(playing = true, videoHeight = 1080)
        }
        compose.onNodeWithContentDescription("Pausar").assertIsDisplayed()
        compose.onNodeWithText("1080p").assertIsDisplayed()
    }

    @Test fun endingControlIsIndependentOfOpeningAndDisappearsWhenLocked() {
        val state = mutableStateOf(playback.copy(animeEpisode = true, positionMs = 1260000, durationMs = 1440000))
        render(state)
        compose.onNodeWithText("Opening · +90 s").assertDoesNotExist()
        compose.onNodeWithText("Ending · +90 s").assertIsDisplayed().performClick()
        compose.runOnIdle { state.value = state.value.copy(ending = OpeningInterval(1250000, 1340000)) }
        compose.onNodeWithText("Saltar ending").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Bloquear controles").performClick()
        compose.onNodeWithText("Saltar ending").assertDoesNotExist()
        compose.runOnIdle { assertEquals(listOf(VideoAction.SKIP_ENDING, VideoAction.SKIP_ENDING), actions) }
    }

    @Test fun transportIsCenteredAndLargerWhileToolsKeepEqualTouchTargets() {
        render()
        val play = compose.onNodeWithContentDescription("Reproducir").fetchSemanticsNode().boundsInRoot
        val rewind = compose.onNodeWithContentDescription("Retroceder 10 segundos").fetchSemanticsNode().boundsInRoot
        val forward = compose.onNodeWithContentDescription("Avanzar 10 segundos").fetchSemanticsNode().boundsInRoot
        val safe = compose.onNodeWithTag("playerSafeArea").fetchSemanticsNode().boundsInRoot
        assertTrue(play.width > rewind.width && play.height > rewind.height)
        assertTrue(kotlin.math.abs(play.center.x - safe.center.x) < 2)
        assertTrue(kotlin.math.abs(play.center.y - safe.center.y) < 2)
        assertTrue(kotlin.math.abs(rewind.center.y - forward.center.y) < 1)
        val toolbar = listOf("Velocidad", "Subtítulos", "Audio", "Ajuste de imagen: Fit", "Bloquear controles", "Girar pantalla")
            .map { compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot }
        assertTrue(toolbar.all { kotlin.math.abs(it.width - toolbar.first().width) < 1 && kotlin.math.abs(it.height - toolbar.first().height) < 1 })
        assertTrue(toolbar.all { kotlin.math.abs(it.center.y - toolbar.first().center.y) < 1 })
        val spacing = toolbar.zipWithNext().map { (a, b) -> b.center.x - a.center.x }
        assertTrue(spacing.all { kotlin.math.abs(it - spacing.first()) < 2 })
        val footer = compose.onNodeWithTag("playerFooter").fetchSemanticsNode().boundsInRoot
        assertTrue(play.bottom <= footer.top)
        val timeline = compose.onNodeWithContentDescription("Posición de reproducción").fetchSemanticsNode().boundsInRoot
        assertTrue("Timeline must stay below tools", timeline.center.y > toolbar.first().bottom)
        assertTrue("Lower controls must not leave the safe area", timeline.bottom <= safe.bottom)
        if (safe.width > safe.height) {
            val info = compose.onNodeWithContentDescription("Información").fetchSemanticsNode().boundsInRoot
            assertTrue("Landscape Info must share the timeline row", kotlin.math.abs(info.center.y - timeline.center.y) < 2)
            compose.onNodeWithTag("playerInfo").assertDoesNotExist()
        }
        assertTrue(toolbar.all { it.left >= safe.left && it.right <= safe.right && it.bottom <= safe.bottom })
        val close = compose.onNodeWithContentDescription("Volver").fetchSemanticsNode().boundsInRoot
        val volume = compose.onNodeWithContentDescription("Volumen").fetchSemanticsNode().boundsInRoot
        assertTrue(close.right < volume.left && close.top < play.top)
        captureIfRequested("player-controls")
    }

    @Test fun volumeAndTranslucentInfoUseRealStateAndBackDismissesTheCard() {
        val state = mutableStateOf(playback.copy(overview = "Una aventura para recordar.", episodeLabel = "T1 · E2"))
        render(state)
        compose.onNodeWithText("T1 · E2").assertIsDisplayed()
        compose.onNodeWithContentDescription("Volumen")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(.4f) }
        compose.runOnIdle { assertEquals(listOf(.4f), volumes) }
        compose.onNodeWithContentDescription("Información").performClick()
        compose.onNodeWithTag("playerInfoCard").assertIsDisplayed()
        compose.onNodeWithText(state.value.overview).assertIsDisplayed()
        captureIfRequested("player-info")
        compose.onNodeWithContentDescription("Reproducir").assertDoesNotExist()
        compose.runOnIdle { back.onBackPressed(); assertTrue(actions.isEmpty()) }
        compose.onNodeWithTag("playerInfoCard").assertDoesNotExist()
        compose.onNodeWithContentDescription("Reproducir").assertIsDisplayed()
    }

    @Test fun sliderSeeksAndDoubleTapsRespectSeekability() {
        val state = mutableStateOf(playback)
        render(state)
        compose.onNodeWithContentDescription("Posici\u00f3n de reproducci\u00f3n")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(.75f) }
        compose.onNodeWithTag("playerSurface").performTouchInput {
            doubleClick(Offset(width * .05f, height * .4f))
        }
        compose.onNodeWithTag("playerSurface").performTouchInput {
            doubleClick(Offset(width * .95f, height * .4f))
        }
        compose.runOnIdle {
            assertEquals(listOf(90_000L), seeks)
            assertEquals(listOf(VideoAction.REWIND, VideoAction.FORWARD), actions)
            state.value = playback.copy(seekable = false)
        }
        compose.onNodeWithContentDescription("Posici\u00f3n de reproducci\u00f3n").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Retroceder 10 segundos").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Avanzar 10 segundos").assertIsNotEnabled()
        compose.onNodeWithTag("playerSurface").performTouchInput {
            doubleClick(Offset(width * .05f, height * .4f))
        }
        compose.runOnIdle { assertEquals(2, actions.size) }
    }

    @Test fun lockHidesControlsBlocksGesturesAndKeepsUnlockAndSystemBack() {
        render()
        compose.onNodeWithContentDescription("Bloquear controles").performClick()
        compose.onNodeWithContentDescription("Reproducir").assertDoesNotExist()
        compose.onNodeWithContentDescription("Girar pantalla").assertDoesNotExist()
        compose.onNodeWithContentDescription("Volver").assertDoesNotExist()
        compose.onNodeWithTag("playerSurface").performTouchInput {
            doubleClick(Offset(width * .05f, height * .4f))
        }
        compose.onNodeWithTag("playerSurface").performTouchInput { click(center) }
        compose.onNodeWithContentDescription("Desbloquear controles").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(emptyList<VideoAction>(), actions)
            back.onBackPressed()
            assertEquals(listOf(VideoAction.CLOSE), actions)
        }
        compose.onNodeWithContentDescription("Desbloquear controles").performClick()
        compose.onNodeWithContentDescription("Reproducir").assertIsDisplayed()
    }

    @Test fun errorShowsTrustedMessageRetryAndOnlyAvailableSourceActions() {
        val state = mutableStateOf(playback.copy(error = "No se pudo reproducir el video."))
        val sourceAvailable = mutableStateOf(false)
        render(state, sourceAvailable)
        compose.onNodeWithText(state.value.error!!).assertIsDisplayed()
        compose.onNodeWithText("Reintentar").performClick()
        compose.onNodeWithText("Cambiar fuente").assertDoesNotExist()
        compose.onNodeWithContentDescription("Cambiar fuente").assertDoesNotExist()
        compose.onNodeWithContentDescription("Reproducir").assertDoesNotExist()
        compose.runOnIdle { sourceAvailable.value = true }
        compose.onNodeWithText("Cambiar fuente").performClick()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.runOnIdle {
            assertEquals(listOf(VideoAction.RETRY, VideoAction.FIND_SOURCES, VideoAction.CLOSE), actions)
        }
    }

    @Test fun menusUseRealTracksDisableTextAndReportSpeedAndMetadata() {
        render(mutableStateOf(playback.copy(
            videoHeight = 720,
            audioTracks = listOf(PlayerTrackOption("audio:es", "Castellano", true)),
            subtitleTracks = listOf(PlayerTrackOption("text:en", "Ingles", false))
        )))
        compose.onNodeWithContentDescription("Audio").performClick()
        compose.onNodeWithText("Castellano").assertIsSelected().performClick()
        compose.onNodeWithContentDescription("Subt\u00edtulos").performClick()
        compose.onNodeWithText("Ingles").performClick()
        compose.onNodeWithContentDescription("Subt\u00edtulos").performClick()
        compose.onNodeWithText("Desactivar subt\u00edtulos").performClick()
        compose.onNodeWithContentDescription("Velocidad").performClick()
        listOf("0.5x", "0.75x", "1.0x", "1.25x", "1.5x", "2.0x").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        compose.onNodeWithText("1.5x").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Informaci\u00f3n").performClick()
        compose.onNodeWithText("Resoluci\u00f3n: 720p").assertIsDisplayed()
        compose.onNodeWithContentDescription("Cerrar").performClick()
        compose.runOnIdle {
            assertEquals(listOf("audio:es", "text:en", "text:none"), tracks)
            assertEquals(listOf(1.5f), speeds)
        }
    }

    @Test fun aspectModeAndLockSurviveSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        val modes = mutableListOf<Int>()
        restoration.setContent {
            MaterialTheme {
                PlayerScreen(playback, false, actions::add, seeks::add, speeds::add, tracks::add) { mode ->
                    SideEffect { modes.add(mode) }
                    Box(Modifier.fillMaxSize().background(Color.Black))
                }
            }
        }
        compose.runOnIdle { assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, modes.last()) }
        compose.onNodeWithContentDescription("Ajuste de imagen: Fit").performClick()
        compose.runOnIdle { assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FILL, modes.last()) }
        compose.onNodeWithContentDescription("Ajuste de imagen: Fill").performClick()
        compose.runOnIdle { assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, modes.last()) }
        compose.onNodeWithContentDescription("Bloquear controles").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.runOnIdle { assertEquals(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, modes.last()) }
        compose.onNodeWithContentDescription("Desbloquear controles").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("Ajuste de imagen: Zoom").performClick()
        compose.runOnIdle { assertEquals(AspectRatioFrameLayout.RESIZE_MODE_FIT, modes.last()) }
    }

    @Test fun autoHideResetsOnInputAndWaitsForMenusPauseBufferingAndErrors() {
        compose.mainClock.autoAdvance = false
        val state = mutableStateOf(playback.copy(playing = true))
        render(state)
        compose.mainClock.advanceTimeBy(3_600)
        compose.onNodeWithContentDescription("Pausar").assertDoesNotExist()
        compose.onNodeWithTag("playerSurface").performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithContentDescription("Pausar").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithContentDescription("Girar pantalla").performClick()
        compose.mainClock.advanceTimeBy(2_000)
        compose.onNodeWithContentDescription("Pausar").assertIsDisplayed()
        compose.onNodeWithContentDescription("Velocidad").performClick()
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithText("1.0x").assertIsDisplayed().performClick()
        compose.runOnIdle { state.value = playback }
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithContentDescription("Reproducir").assertIsDisplayed()
        compose.runOnIdle { state.value = playback.copy(playing = true, buffering = true) }
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithContentDescription("Cargando video").assertIsDisplayed()
        compose.onNodeWithContentDescription("Pausar").assertDoesNotExist()
        compose.runOnIdle { state.value = playback.copy(playing = true, error = "Error de reproduccion") }
        compose.mainClock.advanceTimeBy(4_000)
        compose.onNodeWithText("Reintentar").assertIsDisplayed()
    }

    private fun captureIfRequested(name: String) {
        if (InstrumentationRegistry.getArguments().getString("capturePlayer") != "true") return
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun render(
        state: State<VideoUiState> = mutableStateOf(playback),
        canChangeSource: State<Boolean> = mutableStateOf(true)
    ) {
        compose.setContent {
            back = LocalOnBackPressedDispatcherOwner.current!!.onBackPressedDispatcher
            MaterialTheme {
                PlayerScreen(state.value, canChangeSource.value, actions::add, seeks::add, speeds::add, tracks::add,
                    onVolume = { volumes.add(it) }) {
                    Box(Modifier.fillMaxSize().background(Color.Black))
                }
            }
        }
    }
}

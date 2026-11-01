package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.panoli707.aetherio.nativeapp.data.AppState
import com.panoli707.aetherio.nativeapp.data.SourceProtocol
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddonsSettingsTest {
    @get:Rule val compose = createComposeRule()

    private val privateUrl = "https://example.org/private/manifest.json?token=synthetic-private-secret"
    private val providerName = "Synthetic private provider"

    @Test fun instantReaddWithSameIdsClearsUrlAndRestoresControls() {
        submitInstantly()

        compose.onNodeWithText("Proveedor guardado. Ya puedes buscar fuentes desde las fichas.").assertExists()
        compose.onNode(hasSetTextAction()).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))
        )
        assertReady()
        compose.onNode(hasSetTextAction()).performTextInput(privateUrl)
        compose.onNodeWithText("A\u00f1adir proveedor").assertIsEnabled()
    }

    @Test fun instantFailureKeepsUrlAndRestoresControls() {
        submitInstantly(error = "Synthetic failure")

        compose.onNodeWithText(
            "No se pudo completar la operaci\u00f3n. Revisa la URL, la conexi\u00f3n y que el manifiesto sea compatible."
        ).assertExists()
        compose.onNodeWithText("Proveedor guardado. Ya puedes buscar fuentes desde las fichas.").assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertTextContains(privateUrl)
        assertReady()
        compose.onNodeWithText("A\u00f1adir proveedor").assertIsEnabled()
    }

    private fun submitInstantly(error: String? = null) {
        val addon = SourceProtocol.manifest(
            """{"id":"org.example.synthetic","name":"$providerName","types":["movie"],"resources":["stream"]}""",
            SourceProtocol.manifestUrl(privateUrl)
        )
        val addons = listOf(addon)
        val state = mutableStateOf(AppState(addons = addons, addonsLoading = false, addonOperation = 41L))
        compose.setContent {
            AetherioTheme {
                Column {
                    AddonsSettings(state.value, onAdd = { url ->
                        assertEquals(privateUrl, url)
                        // Complete synchronously: no saving frame and exactly the same providers/IDs.
                        state.value = state.value.copy(
                            addonOperation = state.value.addonOperation + 1L,
                            addonError = error
                        )
                    }, onRemove = {})
                }
            }
        }
        compose.onNode(hasSetTextAction()).performTextInput(privateUrl)
        compose.onNodeWithText("A\u00f1adir proveedor").performClick()
        compose.runOnIdle {
            assertEquals(42L, state.value.addonOperation)
            assertSame(addons, state.value.addons)
            assertFalse(state.value.addonSaving)
        }
    }

    private fun assertReady() {
        compose.onNodeWithText("Guardando proveedor\u2026").assertDoesNotExist()
        compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).assertDoesNotExist()
        compose.onNode(hasSetTextAction()).assertIsEnabled()
        compose.onNodeWithContentDescription("Eliminar proveedor $providerName").assertIsEnabled()
    }
}

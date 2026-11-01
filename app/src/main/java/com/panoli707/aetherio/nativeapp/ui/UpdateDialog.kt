package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.panoli707.aetherio.nativeapp.BuildConfig
import com.panoli707.aetherio.nativeapp.data.UpdateAction
import com.panoli707.aetherio.nativeapp.data.UpdateState

@Composable
internal fun UpdateDialog(state: UpdateState, onAction: (UpdateAction) -> Unit) {
    val update = state.available ?: return
    val cancellable = state.downloading || state.verifying || state.waiting
    val busy = cancellable || state.checking || state.installRequested
    AlertDialog(
        onDismissRequest = { onAction(UpdateAction.DISMISS) },
        title = { Text(if (busy) "Actualizando Aetherio" else "Actualizaci\u00f3n disponible") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Aetherio ${update.versionName}", style = MaterialTheme.typography.titleMedium)
                if (update.prerelease) Text("Versi\u00f3n preliminar", color = Muted)
                Text("Android te pedir\u00e1 confirmar la instalaci\u00f3n.", color = Muted)
                UpdateStatus(state)
            }
        },
        confirmButton = {
            if (cancellable) {
                TextButton(onClick = { onAction(UpdateAction.CANCEL) }) { Text("Cancelar descarga") }
            } else if (!busy) {
                Button(onClick = { onAction(if (state.ready) UpdateAction.INSTALL else UpdateAction.DOWNLOAD) }) {
                    Text(if (state.ready) "Instalar actualizaci\u00f3n" else if (state.error != null) "Reintentar" else "Actualizar ahora")
                }
            }
        },
        dismissButton = { TextButton(onClick = { onAction(UpdateAction.DISMISS) }) { Text("Ocultar") } }
    )
}

@Composable
internal fun UpdateSettings(
    state: UpdateState,
    onAction: (UpdateAction) -> Unit,
    onAutomaticChange: (Boolean) -> Unit
) {
    val cancellable = state.downloading || state.verifying || state.waiting
    val busy = cancellable || state.checking || state.installRequested
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Actualizaciones", style = MaterialTheme.typography.titleLarge)
        Text("Versi\u00f3n instalada: ${BuildConfig.VERSION_NAME}", color = Muted)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Actualizaciones autom\u00e1ticas", modifier = Modifier.weight(1f))
            Switch(
                checked = state.automatic,
                onCheckedChange = onAutomaticChange,
                modifier = Modifier.semantics { contentDescription = "Actualizaciones autom\u00e1ticas" }
            )
        }
        Text(
            "Incluye versiones preliminares. Descarga al abrir la app en redes no medidas; t\u00fa decides cu\u00e1ndo instalar.",
            style = MaterialTheme.typography.bodyMedium,
            color = Muted
        )
        state.available?.let { update ->
            Text("Aetherio ${update.versionName}", style = MaterialTheme.typography.titleMedium)
            if (update.prerelease) Text("Versi\u00f3n preliminar", color = Muted)
        }
        UpdateStatus(state)
        if (state.available != null && !busy) {
            Button(
                onClick = { onAction(if (state.ready) UpdateAction.INSTALL else UpdateAction.DOWNLOAD) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.ready) "Instalar actualizaci\u00f3n" else if (state.error != null) "Reintentar" else "Actualizar ahora")
            }
        }
        OutlinedButton(
            onClick = { onAction(UpdateAction.CHECK) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Buscar actualizaciones") }
        if (cancellable || state.ready) {
            TextButton(onClick = { onAction(UpdateAction.CANCEL) }, enabled = !state.installRequested) {
                Text(if (state.ready) "Descartar descarga" else "Cancelar descarga")
            }
        }
    }
}

@Composable
private fun UpdateStatus(state: UpdateState) {
    val status = when {
        state.installRequested -> "Confirma la instalaci\u00f3n en Android"
        state.verifying -> "Verificando actualizaci\u00f3n"
        state.waiting -> "Esperando conexi\u00f3n para descargar"
        state.downloading -> "Descargando actualizaci\u00f3n: ${state.progress.coerceIn(0, 100)}%"
        state.checking -> "Buscando actualizaciones"
        else -> null
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (status != null) {
            Text(status, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            if (state.downloading && !state.waiting && !state.verifying && !state.installRequested) {
                LinearProgressIndicator(
                    progress = { state.progress.coerceIn(0, 100) / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
        // Technical errors can include download URLs; never expose them in the UI.
        if (state.error != null) {
            Text(
                "No se pudo completar la actualizaci\u00f3n. Vuelve a intentarlo.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
            )
        } else if (!state.message.isNullOrBlank()) {
            Text(state.message, color = Muted, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

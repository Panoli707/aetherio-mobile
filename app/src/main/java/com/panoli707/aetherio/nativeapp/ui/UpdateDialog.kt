package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.panoli707.aetherio.nativeapp.data.AppUpdate

@Composable
internal fun UpdateDialog(update: AppUpdate, onDismiss: () -> Unit, onOpen: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Actualizaci\u00f3n disponible") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Aetherio ${update.versionName}", style = MaterialTheme.typography.titleMedium)
                if (update.prerelease) Text("Versi\u00f3n preliminar", color = Muted)
                Text("Abre GitHub para descargar el nuevo APK. Inst\u00e1lalo sobre la aplicaci\u00f3n actual para conservar tus datos.")
                Text("La actualizaci\u00f3n no se instala autom\u00e1ticamente; Android te pedir\u00e1 confirmaci\u00f3n.")
            }
        },
        confirmButton = { TextButton(onClick = onOpen) { Text("Ver actualizaci\u00f3n") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Ahora no") } }
    )
}

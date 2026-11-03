package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun LicenseSettings() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val licenses by produceState("Cargando licencias…", open) {
        if (open) value = withContext(Dispatchers.IO) {
            context.assets.list("licenses").orEmpty().sorted().joinToString("\n\n") { file ->
                "$file\n\n" + context.assets.open("licenses/$file").bufferedReader().use { it.readText() }
            }
        }
    }
    Text("Software libre", style = MaterialTheme.typography.titleLarge)
    Text("Aetherio · GPL-3.0-or-later. Motor Nuvio Engine 0.1.6. Código fuente: github.com/Panoli707/aetherio-mobile", color = Muted)
    OutlinedButton(onClick = { open = true }) { Text("Licencias y avisos de terceros") }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text("Licencias de software") },
        text = { Text(licenses, Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Cerrar") } })
}

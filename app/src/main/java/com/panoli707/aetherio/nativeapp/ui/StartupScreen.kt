package com.panoli707.aetherio.nativeapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.panoli707.aetherio.nativeapp.R

@Composable
internal fun StartupScreen() {
    Box(
        Modifier.fillMaxSize().background(Ink).windowInsetsPadding(WindowInsets.safeDrawing)
            .semantics { paneTitle = "Inicio de Aetherio" },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(painterResource(R.drawable.aetherio_logo), "Logo de Aetherio", Modifier.size(128.dp))
            Spacer(Modifier.height(20.dp))
            Text("Aetherio", style = MaterialTheme.typography.headlineLarge, color = Cream)
            Spacer(Modifier.height(24.dp))
            CircularProgressIndicator(Modifier.size(24.dp), color = Accent, strokeWidth = 2.dp)
            Spacer(Modifier.height(16.dp))
            Text("Cargando\u2026", style = MaterialTheme.typography.bodyMedium, color = Muted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
        }
    }
}

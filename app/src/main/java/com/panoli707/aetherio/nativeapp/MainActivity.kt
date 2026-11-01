package com.panoli707.aetherio.nativeapp

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.panoli707.aetherio.nativeapp.data.AetherioViewModel
import com.panoli707.aetherio.nativeapp.data.UpdateViewModel
import com.panoli707.aetherio.nativeapp.player.MediaUrlPolicy
import com.panoli707.aetherio.nativeapp.player.VideoActivity
import com.panoli707.aetherio.nativeapp.ui.AetherioApp

class MainActivity : ComponentActivity() {
    private val model: AetherioViewModel by viewModels()
    private val updateModel: UpdateViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            val updates by updateModel.state.collectAsStateWithLifecycle()
            val activityState by lifecycle.currentStateFlow.collectAsStateWithLifecycle()
            val installLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                if (Build.VERSION.SDK_INT < 26 || packageManager.canRequestPackageInstalls()) updateModel.requestInstall()
                else updateModel.installationUnavailable()
            }
            LaunchedEffect(updates.installRequested, activityState, state.sourceQuery) {
                if (updates.installRequested && activityState.isAtLeast(Lifecycle.State.RESUMED) && state.sourceQuery == null) {
                    val installer = updateModel.consumeInstallRequest() ?: return@LaunchedEffect
                    try {
                        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
                            permissionLauncher.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
                        } else {
                            installLauncher.launch(installer)
                            updateModel.installerLaunched()
                        }
                    } catch (_: Exception) { updateModel.installationUnavailable() }
                }
            }
            AetherioApp(
                state = state,
                updates = updates,
                onSelectMedia = model::selectMedia,
                onToggleSaved = model::toggleSaved,
                onSearch = model::search,
                onRetryHome = model::loadHome,
                onRetryDetail = model::retryDetail,
                onSelectSeason = model::selectSeason,
                onRenameProfile = model::renameProfile,
                onAddAddon = model::addAddon,
                onRemoveAddon = model::removeAddon,
                onRequestSources = model::requestSources,
                onDismissSources = model::dismissSources,
                onUpdateAction = updateModel::onAction,
                onAutomaticUpdatesChange = updateModel::setAutomatic,
                onPlaySource = { source, format ->
                    try {
                        val validated = MediaUrlPolicy.validate(source.url)
                        val query = model.state.value.sourceQuery
                        val title = query?.let {
                            if (it.season != null && it.episode != null) "${it.item.title} · T${it.season} E${it.episode}" else it.item.title
                        } ?: "Reproductor"
                        startActivity(Intent(this, VideoActivity::class.java)
                            .putExtra(VideoActivity.EXTRA_URL, validated)
                            .putExtra(VideoActivity.EXTRA_MIME, format.mimeType)
                            .putExtra(VideoActivity.EXTRA_TITLE, title))
                        model.dismissSources()
                    } catch (_: Exception) {
                        Toast.makeText(this, "No se pudo abrir esta fuente. Prueba otra opción.", Toast.LENGTH_LONG).show()
                    }
                },
                onOpenVideo = { url, format ->
                    try {
                        val validated = MediaUrlPolicy.validate(url)
                        startActivity(Intent(this, VideoActivity::class.java)
                            .putExtra(VideoActivity.EXTRA_URL, validated)
                            .putExtra(VideoActivity.EXTRA_MIME, format.mimeType)
                            .putExtra(VideoActivity.EXTRA_TITLE, "Reproducción por enlace"))
                    } catch (_: Exception) {
                        Toast.makeText(this, "No se pudo abrir el vídeo. Usa una URL HTTPS directa válida.", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }

    override fun onStart() {
        super.onStart()
        updateModel.checkForUpdates()
    }
}

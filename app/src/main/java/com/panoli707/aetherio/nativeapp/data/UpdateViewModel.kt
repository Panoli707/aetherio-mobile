package com.panoli707.aetherio.nativeapp.data

import android.app.Application
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.panoli707.aetherio.nativeapp.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdateViewModel internal constructor(
    application: Application,
    private val repository: UpdateRepository,
    private val downloads: UpdateDownloads
) : AndroidViewModel(application) {
    constructor(application: Application) : this(application, UpdateRepository(), UpdateDownloads(application))

    private val mutableState = MutableStateFlow(UpdateState())
    val state = mutableState.asStateFlow()
    private var transferJob: Job? = null
    private var checkJob: Job? = null
    private var lastCheck: Long? = null
    private var dismissedCode = 0
    private var installAfterVerification = false
    private val restoreJob = viewModelScope.launch {
        try {
            val restored = withContext(Dispatchers.IO) { downloads.automatic to downloads.pending() }
            mutableState.update { it.copy(automatic = restored.first) }
            restored.second?.let { (id, update) ->
                transferJob = viewModelScope.launch { monitor(update, id) }
            } ?: run { installAfterVerification = false }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { mutableState.update { it.copy(error = "No se pudo recuperar la descarga.") } }
    }

    fun onAction(action: UpdateAction) {
        when (action) {
            UpdateAction.CHECK -> checkForUpdates(force = true)
            UpdateAction.DOWNLOAD -> download(automatic = false)
            UpdateAction.INSTALL -> requestInstall()
            UpdateAction.CANCEL -> cancelDownload()
            UpdateAction.DISMISS -> {
                state.value.available?.let { dismissedCode = maxOf(dismissedCode, it.versionCode) }
                mutableState.update { it.copy(noticeVisible = false) }
            }
        }
    }

    fun checkForUpdates(force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (checkJob?.isActive == true || (!force && lastCheck?.let { now - it < 600_000L } == true)) return
        lastCheck = now
        checkJob = viewModelScope.launch {
            restoreJob.join()
            if (transferJob?.isActive == true || (state.value.ready && !force)) return@launch
            mutableState.update { it.copy(checking = true, error = null, message = null) }
            try {
                val update = repository.available(BuildConfig.VERSION_CODE)
                if (transferJob?.isActive == true) return@launch
                val prepared = state.value.takeIf { it.ready }?.available
                if (prepared != null) {
                    if (update == null || update.versionCode <= prepared.versionCode) {
                        mutableState.update { it.copy(message = "La actualizacion descargada esta lista para instalar.") }
                        return@launch
                    }
                    mutableState.update { it.copy(ready = false, installRequested = false) }
                    withContext(Dispatchers.IO) { downloads.cancel() }
                }
                mutableState.update { current -> current.copy(
                    available = update,
                    checking = false,
                    noticeVisible = update != null && (force || update.versionCode > dismissedCode),
                    message = if (force && update == null) "Ya tienes la ultima version disponible." else null
                ) }
                if (update != null && state.value.automatic && (force || update.versionCode > dismissedCode)) download(automatic = true)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                if (force) mutableState.update { it.copy(error = "No se pudieron consultar las actualizaciones.") }
            } finally { mutableState.update { it.copy(checking = false) } }
        }
    }

    fun setAutomatic(enabled: Boolean) {
        viewModelScope.launch {
            restoreJob.join()
            try {
                withContext(Dispatchers.IO) { downloads.automatic = enabled }
                mutableState.update { it.copy(automatic = enabled, error = null) }
                if (enabled && checkJob?.isActive != true && transferJob?.isActive != true && !state.value.ready) {
                    if (state.value.available != null) download(automatic = true) else checkForUpdates(force = true)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.update { it.copy(error = "No se pudo guardar el ajuste.") } }
        }
    }

    private fun download(automatic: Boolean) {
        if (state.value.checking) return
        if (state.value.ready) { requestInstall(); return }
        if (transferJob?.isActive == true) return
        val update = state.value.available ?: return
        mutableState.update { it.copy(downloading = true, ready = false, waiting = false,
            progress = 0, noticeVisible = true, error = null, message = null) }
        transferJob = viewModelScope.launch {
            try {
                val id = withContext(Dispatchers.IO) { downloads.enqueue(update, automatic) }
                monitor(update, id)
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { failDownload() }
        }
    }

    private suspend fun monitor(update: AppUpdate, id: Long) {
        try {
            mutableState.update { it.copy(available = update, downloading = true, noticeVisible = true, error = null) }
            while (true) {
                val snapshot = withContext(Dispatchers.IO) { downloads.snapshot(id) }
                when (snapshot.status) {
                    UpdateTransferStatus.COMPLETE -> {
                        mutableState.update { it.copy(downloading = false, verifying = true, waiting = false, progress = 100) }
                        downloads.verify(update, id)
                        mutableState.update { it.copy(verifying = false, ready = true,
                            installRequested = installAfterVerification, error = null,
                            message = "Actualizacion verificada. Pulsa Instalar actualizacion para continuar.") }
                        installAfterVerification = false
                        return
                    }
                    UpdateTransferStatus.FAILED -> { failDownload(); return }
                    else -> mutableState.update { it.copy(downloading = true,
                        waiting = snapshot.status == UpdateTransferStatus.WAITING,
                        progress = (snapshot.bytesDownloaded * 100 / update.size).toInt().coerceIn(0, 100)) }
                }
                delay(1000)
            }
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { failDownload() }
    }

    private suspend fun failDownload() {
        installAfterVerification = false
        withContext(Dispatchers.IO) { runCatching { downloads.cancel() } }
        mutableState.update { it.copy(downloading = false, verifying = false, waiting = false,
            ready = false, installRequested = false, error = "No se pudo descargar o verificar el APK.") }
    }

    private fun cancelDownload() {
        installAfterVerification = false
        val previous = transferJob
        previous?.cancel()
        transferJob = viewModelScope.launch {
            previous?.join()
            try {
                withContext(Dispatchers.IO) { downloads.cancel() }
                mutableState.update { it.copy(downloading = false, verifying = false, waiting = false,
                    ready = false, progress = 0, noticeVisible = false, installRequested = false,
                    error = null, message = "Descarga cancelada.") }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.update { it.copy(error = "No se pudo cancelar la descarga.") } }
        }
    }

    fun requestInstall() {
        if (state.value.ready) mutableState.update { it.copy(installRequested = true, error = null, message = null) }
        else if (restoreJob.isActive || transferJob?.isActive == true) installAfterVerification = true
    }

    fun consumeInstallRequest(): Intent? {
        mutableState.update { it.copy(installRequested = false) }
        val update = state.value.available ?: return null
        return try { downloads.installIntent(update) }
        catch (_: Exception) {
            mutableState.update { it.copy(ready = false, error = "No se pudo preparar la instalacion.") }
            null
        }
    }

    fun installerLaunched() {
        mutableState.update { it.copy(noticeVisible = false, message = "Confirma la instalacion en Android.") }
    }

    fun installationUnavailable() {
        mutableState.update { it.copy(message = "Permite que Aetherio instale aplicaciones para continuar. Puedes reintentar desde Ajustes.") }
    }
}

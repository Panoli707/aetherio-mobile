package com.panoli707.aetherio.nativeapp.player.torrent

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One engine per lease. Cancelling Kotlin commands is insufficient: finally always destroys the native engine. */
class TorrentSessionManager(
    private val factory: () -> TorrentEngine,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val enoughSpace: () -> Boolean = { true },
) {
    private val lifecycle = Mutex()
    private var current: Session? = null
    private var shutdownFailed = false

    @Synchronized fun open(request: TorrentRequest): Session {
        current?.close()
        val session = Session(request.validated())
        current = session
        session.job = scope.launch { lifecycle.withLock { session.run() } }
        return session
    }

    inner class Session internal constructor(private val request: TorrentRequest) {
        private val mutableState = MutableStateFlow<TorrentState>(TorrentState.Preparing("Iniciando motor"))
        val state: StateFlow<TorrentState> = mutableState.asStateFlow()
        internal var job: Job? = null
        @Volatile private var closed = false
        @Volatile private var route: TorrentRoute? = null
        private val selection = CompletableDeferred<Int>()
        fun selectFile(index: Int) {
            val files = (state.value as? TorrentState.SelectFile)?.files ?: return
            if (!closed && files.any { it.index == index }) selection.complete(index)
        }
        @Synchronized fun close() {
            closed = true
            route?.revoke()
            job?.cancel()
            if (mutableState.value !is TorrentState.Failed) mutableState.value = TorrentState.Closed
        }
        @Synchronized private fun publish(value: TorrentState) {
            if (!closed) mutableState.value = value
        }
        internal suspend fun run() {
            var engine: TorrentEngine? = null
            try {
                currentCoroutineContext().ensureActive()
                check(!shutdownFailed) { "SHUTDOWN_FAILED" }
                engine = factory()
                currentCoroutineContext().ensureActive()
                coroutineScope {
                    val active = engine
                    launch { active.failures.collect { throw IllegalStateException("STREAM_STOPPED") } }
                    publish(TorrentState.Preparing("Buscando metadatos"))
                    val id = withTimeout(90_000) { active.addMagnet(request.magnet()) }
                    val files = active.files(id)
                    val file = TorrentFileSelection.choose(files, request) ?: run {
                        val candidates = TorrentFileSelection.candidates(files)
                        publish(TorrentState.SelectFile(candidates))
                        val index = selection.await()
                        candidates.single { it.index == index }
                    }
                    publish(TorrentState.Preparing("Preparando vídeo"))
                    val prepared = withTimeout(30_000) { active.prepare(id, file).also { route = it } }
                    currentCoroutineContext().ensureActive()
                    if (closed) throw CancellationException()
                    publish(TorrentState.Ready(prepared))
                    var samples = 0
                    while (isActive) {
                        delay(1_000)
                        if (++samples % 5 == 0) check(enoughSpace()) { "DISK_SPACE" }
                        publish(TorrentState.Ready(prepared, active.metrics(prepared)))
                    }
                }
            } catch (_: TimeoutCancellationException) {
                publish(TorrentState.Failed("El torrent no respondió a tiempo. Reintenta o elige otra fuente."))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: UnsatisfiedLinkError) {
                publish(TorrentState.Failed("El motor torrent no está disponible en este dispositivo. Elige otra fuente."))
            } catch (error: Exception) {
                publish(TorrentState.Failed(when (error.message) {
                    "INVALID_FILE" -> "El archivo indicado por el proveedor no es un vídeo válido en este torrent."
                    "NO_VIDEO" -> "El torrent no contiene un archivo de vídeo seleccionable."
                    "DISK_SPACE" -> "No hay suficiente espacio libre para reproducir este torrent."
                    "SHUTDOWN_FAILED" -> "El motor anterior no se cerró correctamente. Reinicia Aetherio antes de usar torrents."
                    else -> "No se pudo preparar o mantener el torrent. Reintenta o elige otra fuente."
                }))
            } finally {
                route?.revoke()
                withContext(NonCancellable) {
                    try {
                        route?.let { prepared -> withTimeoutOrNull(5_000) { engine?.stop(prepared) } }
                    } catch (_: Exception) { /* Always destroy even after an unsuccessful stop. */ }
                    finally {
                        try { engine?.shutdown() }
                        catch (_: Exception) {
                            shutdownFailed = true
                            publish(TorrentState.Failed("No se pudo cerrar correctamente el motor torrent. Reinicia Aetherio antes de usar torrents."))
                        }
                    }
                }
            }
        }
    }
}

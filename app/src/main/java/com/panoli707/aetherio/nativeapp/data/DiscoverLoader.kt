package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** One cancellable request for the current selection; failed next pages remain retryable. */
internal class DiscoverLoader(private val scope: CoroutineScope,
    private val fetch: suspend (DiscoverCatalog, Int, String) -> DiscoverPage,
    private val state: () -> DiscoverState, private val update: ((DiscoverState) -> DiscoverState) -> Unit) {
    private var job: Job? = null
    private var generation = 0

    fun load(selection: DiscoverSelection = state().selection, more: Boolean = false, force: Boolean = false) {
        val old = state()
        val normalized = DesktopDiscover.normalize(selection, old.catalogs)
        if (more && (old.loading || old.loadingMore || !old.hasMore || normalized != old.selection)) return
        if (!more && !force && normalized == old.selection && (old.loading || old.page > 0)) return
        val catalog = old.catalogs.firstOrNull { it.key == normalized.catalog } ?: return
        job?.cancel()
        val request = ++generation
        val page = if (more) old.page + 1 else 1
        update { it.copy(selection = normalized, items = if (more) it.items else emptyList(), page = if (more) it.page else 0,
            hasMore = if (more) it.hasMore else false, loading = !more, loadingMore = more, error = null) }
        job = scope.launch {
            try {
                val result = fetch(catalog, page, normalized.genre)
                ensureActive()
                if (generation != request) return@launch
                update { current ->
                    val merged = (if (more) current.items else emptyList()) + result.items
                    val unique = merged.distinctBy { it.key }
                    current.copy(items = unique, page = page, hasMore = result.hasMore && page < 500 &&
                        (!more || unique.size > current.items.size), loading = false, loadingMore = false)
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                if (generation == request) update { it.copy(loading = false, loadingMore = false,
                    error = if (more) "No se pudo cargar la siguiente página. Puedes reintentar." else "No se pudo cargar este catálogo. Comprueba tu conexión y reintenta.") }
            }
        }
    }
}

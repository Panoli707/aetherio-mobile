package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

data class HubProvider(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val config: AddonConfig?,
    val enabled: Boolean,
    val hidden: Boolean,
    val timeoutMs: Long = 55_000,
    val attempts: Int = 1,
    val filterMode: String? = null,
    val filterPrefixes: List<String> = emptyList()
)

internal object ProviderHubDirectory {
    const val URL = "${ProviderHub.BASE}/addons.json"
    // PC resolves directory-relative addon paths against this API, NOT the Hub origin.
    const val API = "https://trkll.aetherio.workers.dev"

    fun parse(raw: String): List<HubProvider> = try { parseDocument(raw) }
        catch (_: Exception) { throw IllegalArgumentException("Directorio público del Hub no válido") }

    private fun parseDocument(raw: String): List<HubProvider> {
        // Public manifests may contain hundreds of unrelated catalog genre options. Keep byte/depth
        // bounds, but don't reject every provider because an ignored catalog exceeds stream limits.
        val json = SourceProtocol.root(raw, SourceProtocol.MANIFEST_LIMIT, collectionLimit = 20_000)
        require(json.opt("version") == 1)
        val entries = requireNotNull(json.optJSONArray("addons"))
        require(entries.length() <= 100)
        val seen = mutableSetOf<String>()
        return (0 until entries.length()).map { index ->
            val entry = requireNotNull(entries.optJSONObject(index))
            val id = requireNotNull(SourceMetadata.text(entry.opt("id")))
            require(seen.add(id))
            val name = requireNotNull(SourceMetadata.text(entry.opt("name"), 80))
            val path = requireNotNull(entry.opt("manifestUrl") as? String)
            val url = SourceProtocol.manifestUrl(if (path.startsWith("/") && !path.startsWith("//")) "$API$path" else path)
            val fallback = requireNotNull(entry.optJSONObject("manifestFallback"))
            require(fallback.optJSONArray("types") != null && fallback.optJSONArray("resources") != null)
            val rawLogo = (entry.opt("logo") as? String) ?: (fallback.opt("logo") as? String)
            val logo = SourceMetadata.safeLogo(if (rawLogo?.startsWith("/") == true && !rawLogo.startsWith("//"))
                "${ProviderHub.BASE}$rawLogo" else rawLogo)
            fun flag(key: String, default: Boolean): Boolean = if (!entry.has(key)) default else entry.get(key) as Boolean
            val hidden = flag("hiddenInUi", false)
            val compatibleType = (0 until fallback.getJSONArray("types").length()).any {
                fallback.getJSONArray("types").optString(it) in setOf("movie", "series")
            }
            val prefixes = if (fallback.has("idPrefixes")) requireNotNull(fallback.optJSONArray("idPrefixes")) else null
            val compatibleId = prefixes == null || prefixes.length() == 0 || (0 until prefixes.length()).any {
                Regex("tt[0-9]{0,12}").matches(prefixes.optString(it))
            }
            val resources = fallback.getJSONArray("resources")
            val hasStreams = (0 until resources.length()).any { resources.opt(it) == "stream" || resources.optJSONObject(it)?.optString("name") == "stream" }
            val streamResources = org.json.JSONArray()
            for (i in 0 until resources.length()) {
                if (resources.opt(i) == "stream") streamResources.put("stream")
                else resources.optJSONObject(i)?.takeIf { it.optString("name") == "stream" }?.let { resource ->
                    val contract = JSONObject().put("name", "stream")
                    listOf("types", "idPrefixes").forEach { key -> if (resource.has(key)) contract.put(key, resource.get(key)) }
                    streamResources.put(contract)
                }
            }
            val streamManifest = JSONObject().put("id", id).put("name", name).put("logo", logo)
                .put("types", fallback.getJSONArray("types")).put("resources", streamResources)
            prefixes?.let { streamManifest.put("idPrefixes", it) }
            val config = if (compatibleType && compatibleId && hasStreams) SourceProtocol.manifest(
                streamManifest.toString(), url
            ).copy(hubManaged = true, payloadAttribution = hidden) else null
            val filter = entry.optJSONObject("streamNameFilter")
            val mode = filter?.getString("mode")?.also { require(it in setOf("include", "exclude")) }
            val filterPrefixes = filter?.let {
                val values = it.getJSONArray("prefixes")
                require(values.length() in 1..20)
                (0 until values.length()).map { i -> requireNotNull(SourceMetadata.text(values.get(i), 80)) }
            }.orEmpty()
            fun bounded(key: String, default: Long, range: LongRange): Long = if (!entry.has(key)) default else {
                val number = entry.get(key) as Number
                require(number.toDouble() == number.toLong().toDouble() && number.toLong() in range)
                number.toLong()
            }
            HubProvider(id, name, logo, config, flag("enabledByDefault", true), hidden,
                bounded("streamTimeoutMs", 55_000, 1_000L..60_000L), bounded("streamAttempts", 1, 1L..2L).toInt(), mode, filterPrefixes)
        }
    }

    fun filter(provider: HubProvider, result: SourceResult): SourceResult = if (provider.filterMode == null) result else {
        result.copy(sources = result.sources.filter { source ->
            val match = provider.filterPrefixes.any { source.name.startsWith(it, ignoreCase = true) }
            if (provider.filterMode == "include") match else !match
        })
    }

    fun branding(raw: String): List<HubBranding> {
        val entries = requireNotNull(SourceProtocol.root(raw, SourceProtocol.MANIFEST_LIMIT, collectionLimit = 20_000)
            .optJSONArray("scrapers"))
        require(entries.length() <= 1000)
        return (0 until entries.length()).mapNotNull { index ->
            val entry = entries.optJSONObject(index) ?: return@mapNotNull null
            val id = SourceMetadata.text(entry.opt("id")) ?: return@mapNotNull null
            val name = SourceMetadata.text(entry.opt("name"), 80) ?: return@mapNotNull null
            HubBranding("hub-runtime:$id", id, name, SourceMetadata.safeLogo(entry.opt("logo")), entry.opt("enabled") != false)
        }
    }
}

/** Only public directory/manifest metadata is cached; never streams or the user's addon store. */
internal class ProviderHubDirectoryRepository(
    private val repository: SourceRepository = SourceRepository(requestTimeoutMs = 12_000),
    private val readCache: () -> String? = { null },
    private val writeCache: (String) -> Unit = {},
    private val readBrandingCache: () -> String? = { null },
    private val writeBrandingCache: (String) -> Unit = {}
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(HubDirectoryResult(
        runCatching { readCache()?.let(ProviderHubDirectory::parse) }.getOrNull().orEmpty(), true,
        runCatching { readBrandingCache()?.let(ProviderHubDirectory::branding) }.getOrNull().orEmpty()))
    val state = mutableState.asStateFlow()

    suspend fun refresh(): HubDirectoryResult = mutex.withLock {
        supervisorScope {
            val directory = async {
                try {
                    val raw = repository.document(ProviderHubDirectory.URL, revalidate = true)
                    val updated = ProviderHubDirectory.parse(raw)
                    mutableState.update { it.copy(providers = updated) }
                    runCatching { writeCache(raw) }
                    true
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { false }
            }
            val manifest = async {
                try {
                    val raw = repository.document("${ProviderHub.BASE}/manifest.json", revalidate = true)
                    val updated = ProviderHubDirectory.branding(raw)
                    mutableState.update { it.copy(branding = updated) }
                    runCatching { writeBrandingCache(raw) }
                    true
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { false }
            }
            // Publish each document as it arrives: a slow/failed endpoint cannot hide the other.
            val directoryReady = directory.await()
            val manifestReady = manifest.await()
            mutableState.update { it.copy(stale = !directoryReady || !manifestReady) }
            return@supervisorScope mutableState.value
        }
    }
}

data class HubBranding(val key: String, val id: String, val name: String, val logoUrl: String?, val enabled: Boolean = true)
internal data class HubDirectoryResult(val providers: List<HubProvider>, val stale: Boolean, val branding: List<HubBranding> = emptyList()) {
    fun decorate(sources: List<StreamSource>): List<StreamSource> {
        val identities = providers.map { HubBranding("hub-addon:${it.id}", it.id, it.name, it.logoUrl) } + branding
        fun normalize(value: String) = value.lowercase().replace(Regex("[^a-z0-9]"), "")
        return sources.map { source ->
            val identity = identities.firstOrNull {
                normalize(source.providerName) in setOf(normalize(it.name), normalize(it.id))
            }
            if (identity == null) source else source.copy(display = source.display.copy(providerKey = identity.key,
                providerName = identity.name, logoUrl = if (source.display.providerName != null)
                    identity.logoUrl ?: source.display.logoUrl else source.display.logoUrl ?: identity.logoUrl))
        }
    }
}

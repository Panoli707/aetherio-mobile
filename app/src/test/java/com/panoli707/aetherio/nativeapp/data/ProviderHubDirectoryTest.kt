package com.panoli707.aetherio.nativeapp.data

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProviderHubDirectoryTest {
    private fun entry(id: String = "new.provider", path: String = "https://new.example/manifest.json",
        types: String = "movie", hidden: Boolean = false): JSONObject = JSONObject().put("id", id).put("name", id)
        .put("logo", "https://logos.example/$id.png").put("manifestUrl", path).put("hiddenInUi", hidden)
        .put("manifestFallback", JSONObject("""{"id":"$id","name":"$id","types":["$types"],"resources":["stream"],"idPrefixes":["tt"]}"""))
    private fun directory(vararg entries: JSONObject) = JSONObject().put("version", 1).put("addons", JSONArray(entries.toList())).toString()

    @Test fun unknownProvidersAreDiscoveredAndRelativePathsUseTheSameApiAsPc() {
        val providers = ProviderHubDirectory.parse(directory(entry("just-added"), entry("aggregate", "/api/penguplay/manifest.json", hidden = true),
            entry("sports", types = "sport"), entry("disabled").put("enabledByDefault", false)))
        assertEquals(4, providers.size)
        assertTrue(providers[0].config!!.hubManaged)
        assertEquals("https://trkll.aetherio.workers.dev/api/penguplay/manifest.json", providers[1].config!!.manifestUrl)
        assertTrue(providers[1].config!!.payloadAttribution)
        assertNull(providers[2].config)
        assertFalse(providers[3].enabled)
        assertEquals("https://logos.example/just-added.png", providers[0].config!!.logoUrl)
    }

    @Test fun restrictedImdbPrefixesRelativeLogosAndCatalogOnlyAddonsAreHandledWithoutDroppingTheDirectory() {
        val restricted = entry("restricted").put("logo", "/addons/logos/restricted.png")
        restricted.getJSONObject("manifestFallback").put("idPrefixes", JSONArray(listOf("tt09")))
        val catalog = entry("catalog-only")
        catalog.getJSONObject("manifestFallback").put("resources", JSONArray(listOf("catalog")))
        val providers = ProviderHubDirectory.parse(directory(restricted, catalog))
        assertEquals(listOf("tt09"), providers[0].config!!.idPrefixes)
        assertEquals("${ProviderHub.BASE}/addons/logos/restricted.png", providers[0].logoUrl)
        assertNull(providers[1].config)
    }

    @Test fun newlyAddedBridgeWithHundredsOfIgnoredCatalogOptionsDoesNotRejectTheEntireHub() {
        val bridge = entry("new-cnc-bridge")
        bridge.getJSONObject("manifestFallback").put("catalogs", JSONArray().put(JSONObject()
            .put("id", "live-tv").put("extra", JSONArray().put(JSONObject().put("name", "genre")
                .put("options", JSONArray((1..800).map { "Genre $it" }))))))
        val providers = ProviderHubDirectory.parse(directory(entry("existing"), bridge, entry("also-new")))
        assertEquals(listOf("existing", "new-cnc-bridge", "also-new"), providers.map { it.id })
        assertTrue(providers.all { it.config != null })
        // Strict generic/custom manifests keep their collection limits; only the public directory
        // ignores irrelevant catalogs before passing its stream contract to SourceProtocol.
        assertThrows(IllegalArgumentException::class.java) {
            SourceProtocol.manifest(bridge.getJSONObject("manifestFallback").toString(),
                SourceProtocol.manifestUrl("https://bridge.example/manifest.json"))
        }
    }

    @Test fun projectingTheStreamContractCannotTurnMalformedIdentifierRestrictionsIntoUnrestrictedAccess() {
        val malformed = entry().apply { getJSONObject("manifestFallback").put("idPrefixes", "tt") }
        assertThrows(IllegalArgumentException::class.java) { ProviderHubDirectory.parse(directory(malformed)) }
    }

    @Test fun malformedPartialOrDuplicateDirectoriesCannotBeMistakenForAnAdministrativeRemoval() {
        for (raw in listOf("{}", "{", directory(entry(), entry()), directory(entry().put("manifestUrl", "http://unsafe.example/manifest.json")),
            directory(entry().put("manifestUrl", "//unsafe.example/manifest.json")), directory(entry().put("manifestFallback", JSONObject())),
            directory(entry().put("streamTimeoutMs", 999999)), directory(entry().put("enabledByDefault", "true")))) {
            assertThrows(IllegalArgumentException::class.java) { ProviderHubDirectory.parse(raw) }
        }
        assertTrue(ProviderHubDirectory.parse(directory()).isEmpty())
    }

    @Test fun refreshFindsNewProvidersKeepsLastValidListOnFailureAndHonorsExplicitEmpty() = runBlocking {
        var body = directory(entry("first"))
        var cache: String? = null
        val requests = mutableListOf<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            synchronized(requests) { requests.add(chain.request().url.encodedPath) }
            assertNull(chain.request().header("Authorization"))
            val response = if (chain.request().url.encodedPath == "/addons.json") body else """{"scrapers":[]}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(response.toResponseBody()).build()
        }.build()
        try {
            val repository = ProviderHubDirectoryRepository(SourceRepository(client), { cache }, { cache = it })
            assertEquals("first", repository.refresh().providers.single().id)
            body = directory(entry("brand-new"))
            assertEquals("brand-new", repository.refresh().providers.single().id)
            val saved = cache
            body = "{}"
            val failed = repository.refresh()
            assertTrue(failed.stale)
            assertEquals("brand-new", failed.providers.single().id)
            assertEquals(saved, cache)
            val restarted = ProviderHubDirectoryRepository(SourceRepository(client), { cache })
            assertEquals("brand-new", restarted.refresh().providers.single().id)
            body = directory()
            assertTrue(repository.refresh().providers.isEmpty())
            assertEquals(directory(), cache)
            assertTrue(requests.all { it == "/addons.json" || it == "/manifest.json" })
        } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdownNow() }
    }

    @Test fun directoryBrandingAndPayloadIdentityNeverChangeTransportOrCredentials() {
        val providers = ProviderHubDirectory.parse(directory(entry("new.provider")))
        val config = providers.single().config!!
        val result = SourceProtocol.streams("""{"streams":[{"name":"1080p","url":"https://cdn.example/episode.mp4","headers":{"Authorization":"Bearer fixture"}}]}""", config)
        val source = HubDirectoryResult(providers, false).decorate(result.sources).single()
        assertEquals(config.id, source.addonId)
        assertEquals("new.provider", source.providerName)
        assertTrue(source.allowProviderHeaders)
        assertEquals("Bearer fixture", source.headers["Authorization"])
        // Runtime trust is intentionally not persisted in user addons.
        val restored = SourceProtocol.decode(SourceProtocol.encode(listOf(config))).single()
        assertFalse(restored.hubManaged)
        assertFalse(restored.payloadAttribution)
        assertEquals(1, SourceProtocol.streams(source.rawStream.let { "{\"streams\":[$it]}" }, restored).unsupportedCount)
    }

    @Test fun aKnownPayloadProviderUsesItsOwnLogoInsteadOfTheAggregatorsLogo() {
        val source = StreamSource("id", "aggregate-transport", "Aggregator", "1080p", "", "https://cdn.example/video.mp4",
            display = SourceDisplay(providerName = "Real provider", logoUrl = "https://logos.example/aggregator.png"))
        val result = HubDirectoryResult(emptyList(), false,
            listOf(HubBranding("real", "real", "Real provider", "https://logos.example/real.png"))).decorate(listOf(source)).single()
        assertEquals("aggregate-transport", result.addonId)
        assertEquals("https://logos.example/real.png", result.display.logoUrl)
        assertEquals("real", result.providerKey)
    }

    @Test fun sharedUpstreamNameFiltersDoNotHideNonmatchingProviders() {
        val provider = ProviderHubDirectory.parse(directory(entry().put("streamNameFilter", JSONObject("""{"mode":"include","prefixes":["Alpha"]}""")))).single()
        val sources = listOf("Alpha 1080p", "Beta 720p").map { StreamSource(it, "id", "Provider", it, "", "https://cdn.example/$it") }
        assertEquals(listOf(sources[0]), ProviderHubDirectory.filter(provider, SourceResult(sources)).sources)
        assertEquals(listOf(sources[1]), ProviderHubDirectory.filter(provider.copy(filterMode = "exclude"), SourceResult(sources)).sources)
    }

    @Test fun managedAggregatorsCanReturnMoreThanTwoHundredStreamsButRemainBounded() {
        val config = ProviderHubDirectory.parse(directory(entry())).single().config!!
        fun body(count: Int) = JSONObject().put("streams", JSONArray((1..count).map { JSONObject().put("url", "https://cdn.example/$it.mp4") })).toString()
        assertEquals(350, SourceProtocol.streams(body(350), config).sources.size)
        assertThrows(IllegalArgumentException::class.java) { SourceProtocol.streams(body(2001), config) }
        assertThrows(IllegalArgumentException::class.java) { SourceProtocol.streams(body(350), config.copy(hubManaged = false)) }
    }

    @Test fun runtimeProvidersPublishEvenIfTheAddonDirectoryFailsAndMarkTheSnapshotPartial() = runBlocking {
        var failDirectory = false
        var failManifest = false
        var runtime = "first"
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            assertEquals("no-cache", chain.request().header("Cache-Control"))
            val directory = chain.request().url.encodedPath == "/addons.json"
            val body = if (directory) directory(entry("first")) else """{"scrapers":[{"id":"$runtime","name":"$runtime"}]}"""
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(if ((directory && failDirectory) || (!directory && failManifest)) 503 else 200)
                .message("fixture").body(body.toResponseBody()).build()
        }.build()
        try {
            val repository = ProviderHubDirectoryRepository(SourceRepository(client))
            assertFalse(repository.refresh().stale)
            failDirectory = true
            runtime = "new-runtime"
            val partial = repository.refresh()
            assertTrue(partial.stale)
            assertEquals("first", partial.providers.single().id)
            assertEquals("new-runtime", partial.branding.single().id)
            failDirectory = false
            failManifest = true
            assertTrue(repository.refresh().stale)
            failManifest = false
            assertFalse(repository.refresh().stale)
        } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdownNow() }
    }

    @Test fun eitherPublicDocumentCanPopulateAFreshInstallationBeforeTheSlowerEndpointFinishes() = runBlocking {
        for (slowDirectory in listOf(true, false)) {
            val gate = CountDownLatch(1)
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                val directory = chain.request().url.encodedPath == "/addons.json"
                if (directory == slowDirectory) check(gate.await(10, TimeUnit.SECONDS))
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                    .body((if (directory) directory(entry("first")) else """{"scrapers":[{"id":"runtime","name":"Runtime"}]}""").toResponseBody()).build()
            }.build()
            try {
                val repository = ProviderHubDirectoryRepository(SourceRepository(client))
                val refresh = async { repository.refresh() }
                withTimeout(5_000) { repository.state.first {
                    if (slowDirectory) it.branding.isNotEmpty() else it.providers.isNotEmpty()
                } }
                assertFalse(refresh.isCompleted)
                gate.countDown()
                assertFalse(refresh.await().stale)
            } finally { gate.countDown(); client.connectionPool.evictAll(); client.dispatcher.executorService.shutdownNow() }
        }
    }
}

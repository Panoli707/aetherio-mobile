package com.panoli707.aetherio.nativeapp.data

import com.panoli707.aetherio.nativeapp.player.VideoFormat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class SourceProtocolTest {
    private val manifest = """{"id":"org.example.addon","name":"My addon","types":["movie","series"],"resources":["stream"]}"""
    private val privateUrl = "https://example.org/private/config/manifest.json?token=private-secret&scope=1"
    private val movie = MediaItem("550", MediaType.MOVIE, "Movie", imdbId = "tt0137523")
    private val series = MediaItem("tt0903747", MediaType.SERIES, "Series", source = CatalogSource.CINEMETA)

    private fun config(raw: String = manifest, url: String = privateUrl): AddonConfig =
        SourceProtocol.manifest(raw, SourceProtocol.manifestUrl(url))

    private fun rejects(block: () -> Unit) {
        val error = try {
            block()
            throw AssertionError("Expected a sanitized failure")
        } catch (error: Exception) { error }
        assertNull(error.cause)
        assertTrue(error.suppressed.isEmpty())
        assertFalse(error.message.orEmpty().contains("private-secret"))
        assertFalse(error.message.orEmpty().contains("https://"))
        assertFalse(error.message.orEmpty().contains("example.org"))
    }

    @Test fun canonicalManifestIdentityIsUrlSha256AndKeepsManifestIdentitySeparate() {
        val first = config(url = "  HTTPS://EXAMPLE.ORG:443/private/unused/../config/manifest.json?token=private-secret&scope=1  ")
        assertEquals(privateUrl, first.manifestUrl)
        assertEquals(config().id, first.id)
        val expected = MessageDigest.getInstance("SHA-256").digest(privateUrl.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        assertEquals(expected, first.id)
        assertEquals("org.example.addon", first.manifestId)
        val other = config(url = privateUrl.replace("private-secret", "another-config"))
        assertNotEquals(first.id, other.id)
        assertEquals(first.manifestId, other.manifestId)
    }

    @Test fun signedPathsKeepEmptySegmentsAndEncodedQueryValues() {
        val manifestUrl = "https://example.org/private//config/manifest.json?signature=a%2Fb%2Bz"
        val addon = config(url = manifestUrl)
        assertEquals(manifestUrl, addon.manifestUrl)
        assertEquals("https://example.org/private//config/stream/movie/tt0137523.json?signature=a%2Fb%2Bz",
            SourceProtocol.endpoint(addon, SourceQuery(movie)).toString())
        val videoUrl = "https://video.example/videos//clip.mp4?signature=a%2Fb%2Bz"
        val raw = JSONObject().put("streams", JSONArray().put(JSONObject().put("url", videoUrl))).toString()
        assertEquals(videoUrl, SourceProtocol.streams(raw, addon).sources.single().url)
    }

    @Test fun unsafeManifestUrlsFailWithoutExposingInputOrCause() {
        listOf(
            "http://example.org/manifest.json?token=private-secret",
            "https://user:private-secret@example.org/manifest.json",
            "https://@example.org/manifest.json", "https://example.org/manifest.json#private-secret",
            "https://example.org/manifest.json/", "https://example.org/configure",
            "https://example.org/manifest.json?x=private-secret\n", "https://example.org/%0a/manifest.json",
            "https://example.org/a b/manifest.json", "https://example.org\\evil/manifest.json",
            "//example.org/manifest.json", "file:///manifest.json",
            "https://example.org/" + "x".repeat(8192) + "/manifest.json"
        ).forEach { raw -> rejects { SourceProtocol.manifestUrl(raw) } }
    }

    @Test fun stringAndObjectResourcesRestrictToLegitimateAvailableTypesAndImdbPrefixes() {
        assertEquals(setOf(MediaType.MOVIE, MediaType.SERIES), config().types)
        assertTrue(config().idPrefixes.isEmpty())
        val raw = """{"id":"addon","name":"Addon","types":["movie","series","anime"],
            "idPrefixes":["tt","kitsu:"],"resources":["catalog",{"name":"stream",
            "types":["movie","anime"],"idPrefixes":["tt01","kitsu:"]}]}"""
        val addon = config(raw)
        assertEquals(setOf(MediaType.MOVIE), addon.types)
        assertEquals(listOf("tt01"), addon.idPrefixes)
        assertNotNull(SourceProtocol.endpoint(addon, SourceQuery(movie)))
        assertNull(SourceProtocol.endpoint(addon, SourceQuery(series, 1, 1)))
        assertNull(SourceProtocol.endpoint(addon, SourceQuery(movie.copy(imdbId = "tt0903747"))))
    }

    @Test fun unsupportedResourcesTypesPrefixesAndInexpressibleCrossProductsAreRejected() {
        listOf(
            manifest.replace("[\"stream\"]", "[\"catalog\"]"),
            manifest.replace("[\"movie\",\"series\"]", "[\"anime\",\"tv\"]"),
            manifest.replace("\"resources\":", "\"idPrefixes\":[\"kitsu:\"],\"resources\":"),
            manifest.replace("[\"stream\"]", "[{\"name\":\"stream\",\"types\":[\"anime\"]}]"),
            manifest.replace("[\"stream\"]", "[{\"name\":\"stream\",\"idPrefixes\":[\"kitsu:\"]}]"),
            manifest.replace("[\"stream\"]", """[{"name":"stream","types":["movie"],"idPrefixes":["tt01"]},
                {"name":"stream","types":["series"],"idPrefixes":["tt09"]}]""")
        ).forEach { rejects { config(it) } }
    }

    @Test fun overlappingResourceRestrictionsAreNarrowedRatherThanWidened() {
        val raw = manifest.replace("[\"stream\"]", """[
            {"name":"stream","types":["movie"],"idPrefixes":["tt0"]},
            {"name":"stream","types":["series"],"idPrefixes":["tt09"]}]""")
        assertEquals(listOf("tt09"), config(raw).idPrefixes)
        assertEquals(setOf(MediaType.MOVIE, MediaType.SERIES), config(raw).types)
    }

    @Test fun malformedManifestShapesDoNotCoerceNumbersObjectsOrArraysIntoStrings() {
        listOf(
            "[]", "{}", "private-secret", manifest + "private-secret",
            manifest.replace("\"org.example.addon\"", "123"),
            manifest.replace("\"My addon\"", "{}"), manifest.replace("\"My addon\"", "\"   \""),
            manifest.replace("[\"stream\"]", "\"stream\""),
            manifest.replace("[\"stream\"]", "[null]"),
            manifest.replace("[\"stream\"]", "[{\"name\":123}]"),
            manifest.replace("[\"movie\",\"series\"]", "[1]"),
            manifest.replace("\"resources\":", "\"idPrefixes\":null,\"resources\":")
        ).forEach { rejects { config(it) } }
    }

    @Test fun manifestMetadataIsSanitizedAndBounded() {
        val raw = JSONObject(manifest).put("name", "\n" + "A".repeat(100) + "\u0000")
            .put("id", "org.example.\naddon").toString()
        val addon = config(raw)
        assertEquals("A".repeat(80), addon.name)
        assertEquals("org.example.addon", addon.manifestId)
        rejects { config(JSONObject(manifest).put("id", "x".repeat(257)).toString()) }
    }

    @Test fun endpointsKeepConfigurationPrefixAndPrivateQueryAndUseExplicitEpisodeOnly() {
        val addon = config()
        val movieUrl = SourceProtocol.endpoint(addon, SourceQuery(movie))!!
        assertEquals("https://example.org/private/config/stream/movie/tt0137523.json?token=private-secret&scope=1", movieUrl.toString())
        val episodeUrl = SourceProtocol.endpoint(addon, SourceQuery(series, 0, 2))!!
        assertEquals(listOf("private", "config", "stream", "series", "tt0903747:0:2.json"), episodeUrl.pathSegments)
        assertEquals("private-secret", episodeUrl.queryParameter("token"))
        assertEquals(movieUrl.host, episodeUrl.host)
        assertNull(SourceProtocol.endpoint(addon, SourceQuery(series)))
        assertNull(SourceProtocol.endpoint(addon, SourceQuery(series, 1, null)))
        assertNull(SourceProtocol.endpoint(addon, SourceQuery(series, -1, 1)))
        assertNull(SourceProtocol.endpoint(addon, SourceQuery(series, 1, 0)))
        assertNull(SourceProtocol.endpoint(addon.copy(types = emptySet()), SourceQuery(movie)))
    }

    @Test fun identifiersRequireRealImdbOrCinemetaNamespaceAndNeverUseTmdbAsImdb() {
        listOf(null, "550", "tt1234", "tt1234567890123", "tt0137523:1:1", "tt0137523/secret", "TT0137523")
            .forEach { assertNull(SourceProtocol.endpoint(config(), SourceQuery(movie.copy(imdbId = it)))) }
        assertNull(SourceProtocol.endpoint(config(), SourceQuery(movie.copy(id = "tt0137523", imdbId = null))))
        assertNotNull(SourceProtocol.endpoint(config(), SourceQuery(movie.copy(id = "tt0137523", imdbId = null, source = CatalogSource.CINEMETA))))
    }

    @Test fun directStreamsNormalizeDeduplicateKeepOwnAddonNameAndInferOnlyKnownFormats() {
        val raw = """{"streams":[
            {"name":"Direct","url":"HTTPS://VIDEO.EXAMPLE:443/a/../movie.m3u8","behaviorHints":{"notWebReady":true}},
            {"name":"Direct","url":"https://video.example/movie.m3u8"},
            {"name":"DASH","url":"https://video.example/video","mimeType":"application/dash+xml"},
            {"url":"https://video.example/other","behaviorHints":{"filename":"episode.m3u8","proxyHeaders":{"request":{}}}},
            {"url":"https://video.example/unknown","mimeType":"application/private-secret","addonName":"Imposter"},
            {"name":"Other label","url":"https://video.example/movie.m3u8"}]}"""
        val result = SourceProtocol.streams(raw, config())
        assertEquals(5, result.sources.size)
        assertEquals(0, result.unsupportedCount)
        assertEquals("https://video.example/movie.m3u8", result.sources.first().url)
        assertEquals(listOf(VideoFormat.HLS, VideoFormat.DASH, VideoFormat.HLS, VideoFormat.AUTO, VideoFormat.HLS), result.sources.map { it.format })
        assertTrue(result.sources.all { it.addonName == "My addon" && it.addonId == config().id })
        assertEquals(5, result.sources.map { it.id }.toSet().size)
        assertTrue(result.sources.all { it.id.matches(Regex("[0-9a-f]{64}")) })
        assertEquals(result, SourceProtocol.streams(raw, config()))
        assertNotEquals(result.sources.first().id, SourceProtocol.streams(raw, config(url = privateUrl + "&instance=2")).sources.first().id)
    }

    @Test fun unsupportedHeadersDrmTorrentsExternalTargetsAndMalformedStreamsAreCounted() {
        val invalid = JSONArray("""[
            {"url":"http://video.example/a"},{"url":"https://@video.example/a"},
            {"url":"https://user:private-secret@video.example/a"},
            {"url":"https://video.example/a","infoHash":"abc"},
            {"url":"https://video.example/a","externalUrl":"https://example.org/private-secret"},
            {"url":"https://video.example/a","ytId":"youtube"},
            {"url":"https://video.example/a","drm":{"license":"private-secret"}},
            {"url":"https://video.example/a","behaviorHints":{"drm":true}},
            {"url":"https://video.example/a","behaviorHints":{"proxyHeaders":{"request":{"Referer":"private-secret"}}}},
            {"url":"https://video.example/a","proxyHeaders":{"request":{"Authorization":"private-secret"}}},
            {"url":"https://video.example/a","headers":{"Cookie":"private-secret"}},
            {"url":"https://video.example/a","behaviorHints":[]},
            {"url":"magnet:private-secret"},{"url":123},{"name":"Missing URL"},null,[],"private-secret"]""")
        val expected = invalid.length()
        invalid.put(JSONObject().put("url", "https://video.example/good").put("behaviorHints", JSONObject().put("notWebReady", true)))
        val result = SourceProtocol.streams(JSONObject().put("streams", invalid).toString(), config())
        assertEquals(expected, result.unsupportedCount)
        assertEquals(1, result.sources.size)
        assertEquals(VideoFormat.AUTO, result.sources.single().format)
    }

    @Test fun sourceMetadataIsBoundedAndControlFree() {
        val stream = JSONObject().put("url", "https://video.example/a")
            .put("name", "\n" + "N".repeat(150)).put("description", "D".repeat(700) + "\u0000")
        val source = SourceProtocol.streams(JSONObject().put("streams", JSONArray().put(stream)).toString(), config()).sources.single()
        assertEquals("N".repeat(120), source.name)
        assertEquals("D".repeat(600), source.description)
        stream.remove("description")
        stream.put("title", "Episode title\n")
        assertEquals("Episode title", SourceProtocol.streams(JSONObject().put("streams", JSONArray().put(stream)).toString(), config()).sources.single().description)
    }

    @Test fun optionalEmptyArraysAreSupportedButWrongShapesAndOversizedCollectionsAreNot() {
        assertEquals(SourceResult(emptyList()), SourceProtocol.streams("{}", config()))
        assertEquals(SourceResult(emptyList()), SourceProtocol.streams("""{"streams":[]}""", config()))
        listOf("[]", """{"streams":null}""", """{"streams":{}}""", "{} trailing-private-secret")
            .forEach { raw -> rejects { SourceProtocol.streams(raw, config()) } }
        val tooMany = JSONArray((1..201).map { JSONObject().put("url", "https://video.example/$it") })
        rejects { SourceProtocol.streams(JSONObject().put("streams", tooMany).toString(), config()) }
        rejects { config(JSONObject(manifest).put("resources", JSONArray(List(201) { "stream" })).toString()) }
        rejects { config("{\"nested\":" + "[".repeat(40) + "0" + "]".repeat(40) + "}") }
        rejects { SourceProtocol.streams("{\"streams\":[" + List(201) { "null" }.joinToString(";") + "]}", config()) }
        rejects { config(" ".repeat(SourceProtocol.MANIFEST_LIMIT + 1)) }
        rejects { SourceProtocol.streams(" ".repeat(SourceProtocol.STREAM_LIMIT + 1), config()) }
    }

    @Test fun plaintextCodecRoundTripsAllFieldsBeforeEncryptionWithoutClaimingHostKeystoreCoverage() {
        val first = config().copy(idPrefixes = listOf("tt01", "tt09"))
        val second = config(url = privateUrl + "&instance=2").copy(types = setOf(MediaType.SERIES))
        val encoded = SourceProtocol.encode(listOf(first, second, first))
        assertTrue(encoded.contains("private-secret")) // Only the pre-encryption codec is under test.
        assertEquals(listOf(first, second), SourceProtocol.decode(encoded))
        assertEquals(emptyList<AddonConfig>(), SourceProtocol.decode(SourceProtocol.encode(emptyList())))
    }

    @Test fun codecRejectsTamperingInvalidSavedConfigsWrongShapesVersionsAndMoreThan30Configs() {
        val original = JSONObject(SourceProtocol.encode(listOf(config())))
        listOf("id" to "private-secret", "manifestUrl" to "http://example.org/manifest.json",
            "name" to "x".repeat(81)).forEach { (field, value) ->
            val altered = JSONObject(original.toString())
            altered.getJSONArray("addons").getJSONObject(0).put(field, value)
            rejects { SourceProtocol.decode(altered.toString()) }
        }
        listOf("types" to JSONArray().put("tv"), "idPrefixes" to JSONArray().put("kitsu:"),
            "types" to JSONArray()).forEach { (field, value) ->
            val altered = JSONObject(original.toString())
            altered.getJSONArray("addons").getJSONObject(0).put(field, value)
            rejects { SourceProtocol.decode(altered.toString()) }
        }
        rejects { SourceProtocol.decode("""{"version":2,"addons":[]}""") }
        rejects { SourceProtocol.decode("""{"version":"1","addons":[]}""") }
        rejects { SourceProtocol.decode("""{"version":1,"addons":{}}""") }
        rejects { SourceProtocol.encode(List(31) { config() }) }
        val tooMany = JSONArray(List(31) { original.getJSONArray("addons").getJSONObject(0) })
        rejects { SourceProtocol.decode(JSONObject().put("version", 1).put("addons", tooMany).toString()) }
    }
}

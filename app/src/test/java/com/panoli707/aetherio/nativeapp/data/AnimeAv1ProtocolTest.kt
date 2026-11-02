package com.panoli707.aetherio.nativeapp.data

import com.panoli707.aetherio.nativeapp.player.VideoFormat
import java.net.InetAddress
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AnimeAv1ProtocolTest {
    private val series = MediaItem("fixture", MediaType.SERIES, "Blue Sky")

    private fun rejects(block: () -> Unit) {
        try {
            block()
            fail("Expected invalid protocol data")
        } catch (error: IllegalArgumentException) {
            assertEquals("Datos de AnimeAV1 no validos", error.message)
            assertNull(error.cause)
        }
    }

    @Test fun referencesAreLocalReusableAndEscapedWithDubOnlyEmbeds() {
        val raw = """{"nodes":[{"type":"skip"},{"data":[{"results":1},[2,2],{"title":3,"slug":4},"Coraz\u00f3n \"Blue\" } ]","blue-sky"]},{"data":[{"results":1},[2],{"title":3,"slug":4},"Other","other"]}]}"""
        assertEquals(listOf(AnimeAv1Protocol.Title("Coraz\u00f3n \"Blue\" } ]", "blue-sky"),
            AnimeAv1Protocol.Title("Other", "other")), AnimeAv1Protocol.parseCatalog(raw))
        val episode = """{"nodes":[{"data":[{"media":1,"episode":4,"embeds":5},{"title":2,"slug":3},"Blue Sky","blue-sky",3,{"DUB":6},[7,7],{"server":8,"url":9},"Fixture","https:\/\/player.zilla-networks.com\/play\/fixture"]}]}"""
        assertEquals(listOf(AnimeAv1Protocol.Embed("Fixture", "DUB", "https://player.zilla-networks.com/play/fixture")),
            AnimeAv1Protocol.parseEpisode(episode, "blue-sky", 3))
    }

    @Test fun invalidReferencesShapesDepthNodesBytesAndCollectionLimitsAreRejected() {
        val valid = AnimeAv1Fixtures.catalog("Blue Sky" to "blue-sky")
        listOf("-1", "999", "1.0", "\"1\"").forEach { reference ->
            rejects { AnimeAv1Protocol.parseCatalog(valid.replace("\"results\":1", "\"results\":$reference")) }
        }
        rejects { AnimeAv1Protocol.parseCatalog("""{"nodes":[{"data":[{"results":3}]},{"data":[{},0,0,[]]}]}""") }
        rejects { AnimeAv1Protocol.parseCatalog(valid.replace("\"nodes\":[", "\"nodes\":{\"not\":[")) }
        rejects { AnimeAv1Protocol.parseCatalog(valid + " trailing") }
        rejects { AnimeAv1Protocol.parseCatalog("[".repeat(33) + "0" + "]".repeat(33)) }
        rejects { AnimeAv1Protocol.parseCatalog("{\"unused\":[" + "0,".repeat(32769) + "0]}") }
        rejects { AnimeAv1Protocol.parseCatalog(" ".repeat(AnimeAv1Protocol.BODY_LIMIT + 1)) }
        rejects { AnimeAv1Protocol.parseCatalog("\u00e9".repeat(AnimeAv1Protocol.BODY_LIMIT / 2 + 1)) }
        rejects { AnimeAv1Protocol.parseCatalog(AnimeAv1Fixtures.catalog(*Array(33) { "Blue Sky" to "blue-sky" })) }
        rejects { AnimeAv1Protocol.parseEpisode(AnimeAv1Fixtures.episode(embeds = List(25) {
            AnimeAv1Protocol.Embed("Fixture", "SUB", "https://video.example/a.mp4")
        }), "blue-sky", 3) }
    }

    @Test fun titleConfidenceSeasonMarkersAndAmbiguityNeverFallBackToFirstResult() {
        fun select(vararg titles: Pair<String, String>, season: Int = 1, title: String = "Blue Sky") =
            AnimeAv1Protocol.selectTitle(titles.map { AnimeAv1Protocol.Title(it.first, it.second) },
                SourceQuery(series.copy(title = title), season, 3))
        assertNull(select("Other" to "other"))
        assertNull(select("Blue Sky Movie" to "blue-sky-movie"))
        assertNull(select("Blue Sky" to "blue-sky", "Blue Sky" to "blue-sky-remake"))
        assertEquals("blue-sky", select("Blue Sky Season 1" to "blue-sky-season-1", "Blue Sky" to "blue-sky")!!.slug)
        assertNull(select("Blue Sky" to "blue-sky", season = 2))
        listOf("Season2", "2ndSeason", "Temporada2", "II").forEach { marker ->
            assertEquals("blue-sky-2", select("Blue Sky $marker" to "blue-sky-2", season = 2)!!.slug)
        }
        assertEquals("blue-sky-temporada-2", select("Blue Sky" to "blue-sky-temporada-2", season = 2)!!.slug)
        assertNull(select("Blue Sky Season 2" to "blue-sky-season-3", season = 2))
        assertNull(select("Blue Sky IIX" to "blue-sky-iix", season = 2))
        assertNull(select("Blue Sky Part 2" to "blue-sky-part-2", season = 2))
        assertNull(select("Blue Sky II" to "blue-sky-ii", season = 1))
        listOf("III" to 3, "IV" to 4, "V" to 5).forEach { (roman, number) ->
            assertNotNull(select("Blue Sky $roman" to "blue-sky-${roman.lowercase()}", season = number))
        }
        assertEquals("corazon-azul", select("Coraz\u00f3n Azul" to "corazon-azul", title = "CORAZON AZUL")!!.slug)
        assertEquals("blue-sky", select("Cielo Azul" to "blue-sky")!!.slug)
    }

    @Test fun seriesNeedsExplicitEpisodeAndEpisodeMetadataMustMatchSelection() {
        listOf(SourceQuery(series), SourceQuery(series, 1), SourceQuery(series, null, 3),
            SourceQuery(series, 0, 3), SourceQuery(series, 1, 0), SourceQuery(series, -1, 3)).forEach {
            assertNull(AnimeAv1Protocol.episodeFor(it))
        }
        assertEquals(3, AnimeAv1Protocol.episodeFor(SourceQuery(series, 2, 3)))
        assertEquals(1, AnimeAv1Protocol.episodeFor(SourceQuery(series.copy(type = MediaType.MOVIE))))
        assertEquals("https://animeav1.com/media/blue-sky/3/__data.json", AnimeAv1Protocol.episodeUrl("blue-sky", 3).toString())
        val valid = AnimeAv1Fixtures.episode()
        assertEquals(1, AnimeAv1Protocol.parseEpisode(valid, "blue-sky", 3).size)
        rejects { AnimeAv1Protocol.parseEpisode(valid, "other", 3) }
        rejects { AnimeAv1Protocol.parseEpisode(valid, "blue-sky", 4) }
        rejects { AnimeAv1Protocol.episodeUrl("../other", 1) }
        val document = JSONObject(valid)
        val pool = document.getJSONArray("nodes").getJSONObject(0).getJSONArray("data")
        val number = pool.length()
        pool.put(3)
        pool.put(4, JSONObject().put("number", number))
        assertEquals(1, AnimeAv1Protocol.parseEpisode(document.toString(), "blue-sky", 3).size)
        pool.put(number, 4)
        rejects { AnimeAv1Protocol.parseEpisode(document.toString(), "blue-sky", 3) }
    }

    @Test fun httpsAndFetchOriginsRejectDowngradesCredentialsAndPrivateDestinations() {
        listOf("http://animeav1.com/a", "https://user:password@animeav1.com/a", "https://@animeav1.com/a",
            "https://animeav1.com/a#fragment", "https://animeav1.com/a%0d", "https://animeav1.com/a\n",
            "https://127.0.0.1/a.mp4", "https://[::1]/a.mp4", "https://0x7f.0.0.1/a.mp4",
            "https://host.local/a.mp4", "https://localhost/a.mp4").forEach { rejects { AnimeAv1Protocol.httpsUrl(it) } }
        listOf("https://animeav1.com.evil.example/", "https://evil.example/", "https://x.yourupload.com/",
            "https://animeav1.com:8443/").forEach { rejects { AnimeAv1Protocol.fetchUrl(it) } }
        listOf("https://animeav1.com/", "https://www.animeav1.com/", "https://player.zilla-networks.com/",
            "https://mp4upload.com/", "https://www.mp4upload.com/", "https://a.mp4upload.com/",
            "https://yourupload.com/", "https://www.yourupload.com/").forEach {
            assertEquals(it, AnimeAv1Protocol.fetchUrl(it).toString())
        }
        listOf("127.0.0.1", "10.0.0.1", "169.254.1.1", "100.64.0.1", "::1", "fc00::1").forEach {
            assertFalse(AnimeAv1Protocol.isPublicAddress(InetAddress.getByName(it)))
        }
        assertTrue(AnimeAv1Protocol.isPublicAddress(InetAddress.getByName("8.8.8.8")))
        assertNull(AnimeAv1Protocol.direct("https://unknown.example/embed/fixture"))
    }

    @Test fun resolversOnlyReturnRecognizedDirectMediaWithSafeProviderHeaders() {
        val zilla = AnimeAv1Protocol.zilla("https://player.zilla-networks.com/play/fixture_1")!!
        assertEquals("https://player.zilla-networks.com/m3u8/fixture_1", zilla.url.toString())
        assertEquals(VideoFormat.HLS, zilla.format)
        assertEquals(mapOf("Referer" to "https://player.zilla-networks.com/", "Origin" to "https://player.zilla-networks.com",
            "User-Agent" to AnimeAv1Protocol.USER_AGENT, "Sec-Fetch-Dest" to "empty", "Sec-Fetch-Mode" to "cors",
            "Sec-Fetch-Site" to "same-origin"), zilla.headers)
        listOf("https://player.zilla-networks.com/play/fixture?token=x", "https://player.zilla-networks.com/play/fixture/",
            "https://player.zilla-networks.com/play/%66ixture", "https://player.zilla-networks.com/embed/fixture").forEach {
            assertNull(AnimeAv1Protocol.zilla(it))
        }
        assertEquals(VideoFormat.DASH, AnimeAv1Protocol.direct("https://video.example/a.mpd?signature=fixture")!!.format)
        assertEquals(VideoFormat.HLS, AnimeAv1Protocol.direct("https://video.example/a.m3u8")!!.format)
        val mp4 = AnimeAv1Protocol.parseMp4Upload("src: 'https://a.mp4upload.com:8443/d/fixture/video.mp4?signature=fixture'")!!
        assertEquals(VideoFormat.AUTO, mp4.format)
        assertEquals("https://www.mp4upload.com/", mp4.headers["Referer"])
        assertEquals("https://www.mp4upload.com", mp4.headers["Origin"])
        assertNull(AnimeAv1Protocol.parseMp4Upload("src:'https://evil.example/d/fixture/video.mp4'"))
        assertNull(AnimeAv1Protocol.parseMp4Upload("eval(function(p,a,c,k,e,d){return 'packed';})"))
        val embed = AnimeAv1Protocol.fetchUrl("https://yourupload.com/embed/fixture")
        val your = AnimeAv1Protocol.parseYourUpload("file: 'https://cdn.example/fixture.mp4?signature=fixture'", embed)!!
        assertEquals(embed.toString(), your.headers["Referer"])
        assertEquals("https://yourupload.com", your.headers["Origin"])
        assertEquals(AnimeAv1Protocol.USER_AGENT, your.headers["User-Agent"])
        assertNull(AnimeAv1Protocol.parseYourUpload("file:'http://cdn.example/fixture.mp4'", embed))
        assertNull(AnimeAv1Protocol.parseYourUpload("file:'https://cdn.example/embed/fixture'", embed))
    }
}

internal object AnimeAv1Fixtures {
    fun document(pool: JSONArray): String = JSONObject().put("nodes", JSONArray()
        .put(JSONObject().put("type", "data").put("data", pool))).toString()

    fun catalog(vararg titles: Pair<String, String> = arrayOf("Blue Sky" to "blue-sky")): String {
        val results = JSONArray()
        val pool = JSONArray().put(JSONObject().put("results", 1)).put(results)
        titles.forEach { (title, slug) ->
            val index = pool.length()
            results.put(index)
            pool.put(JSONObject().put("title", index + 1).put("slug", index + 2)).put(title).put(slug)
        }
        return document(pool)
    }

    fun episode(
        slug: String = "blue-sky",
        number: Int = 3,
        embeds: List<AnimeAv1Protocol.Embed> = listOf(AnimeAv1Protocol.Embed("Zilla", "SUB", "https://player.zilla-networks.com/play/fixture"))
    ): String {
        val variants = JSONObject()
        val pool = JSONArray().put(JSONObject().put("media", 1).put("episode", 4).put("embeds", 5))
            .put(JSONObject().put("title", 2).put("slug", 3)).put("Blue Sky").put(slug).put(number).put(variants)
        embeds.groupBy { it.variant }.forEach { (variant, values) ->
            val entries = JSONArray()
            variants.put(variant, pool.length())
            pool.put(entries)
            values.forEach { embed ->
                val index = pool.length()
                entries.put(index)
                pool.put(JSONObject().put("server", index + 1).put("url", index + 2)).put(embed.server).put(embed.url)
            }
        }
        return document(pool)
    }
}

package com.panoli707.aetherio.nativeapp.data

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CatalogEpisodeImagesTest {
    @Test fun tmdbStillsAreDisplaySizedAndMissingImagesStayMissing() {
        val episodes = CatalogJson.episodes(JSONObject("""{"episodes":[
            {"episode_number":1,"still_path":"/still.jpg"},
            {"episode_number":2,"still_path":null},
            {"episode_number":3,"still_path":""},
            {"episode_number":4,"still_path":"//other.example/still.jpg"},
            {"episode_number":5,"still_path":"https://other.example/still.jpg"}]}"""), 2)
        assertEquals("https://image.tmdb.org/t/p/w500/still.jpg", episodes.first().imageUrl)
        assertTrue(episodes.drop(1).all { it.imageUrl == null })
        assertTrue(episodes.all { it.season == 2 })
    }

    @Test fun cinemetaPreservesSignedUrlsAndDoesNotInventOrRenumberImages() {
        val url = "https://episodes.metahub.space/tt43650419/1/7/w780.jpg?signature=a%2Fb&expires=123"
        val json = JSONObject("""{"videos":[
            {"season":1,"episode":7,"thumbnail":"$url","released":"2099-11-14T16:30:00Z"},
            {"season":1,"episode":8,"thumbnail":null},
            {"season":1,"episode":9,"thumbnail":"http://example.org/insecure.jpg"},
            {"season":1,"episode":10,"thumbnail":"https://user:secret@example.org/still.jpg"},
            {"season":2,"episode":7,"thumbnail":"https://example.org/other.jpg"}]}""")
        val episodes = CatalogJson.episodes(json, 1, cinemeta = true)
        assertEquals(listOf(7, 8, 9, 10), episodes.map { it.number })
        assertEquals(url, episodes.first().imageUrl)
        assertFalse(episodes.first().released)
        assertTrue(episodes.drop(1).all { it.imageUrl == null })
    }
}

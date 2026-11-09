package com.panoli707.aetherio.nativeapp.data

import org.json.JSONObject

internal enum class ArtworkSelectionKind { PORTRAIT, BACKDROP, LOGO }

/** Null means provider default; an empty logo explicitly means title text. */
internal data class ArtworkSelection(val url: String, val ratio: Float = 2f / 3f) {
    fun encode(): String = JSONObject().put("url", url).put("ratio", ratio).toString()
    companion object {
        fun decode(raw: String?): ArtworkSelection? = runCatching {
            val json = JSONObject(requireNotNull(raw))
            val url = json.getString("url")
            require(url.isEmpty() || CatalogJson.image(url) != null ||
                Regex("file:///android_asset/anime-logos/anilist_[0-9]+\\.png").matches(url))
            val ratio = json.getDouble("ratio").toFloat()
            require(ratio.isFinite() && ratio in .35f..3f)
            ArtworkSelection(url, ratio)
        }.getOrNull()
    }
}

internal object ArtworkSelectionIdentity {
    fun key(item: MediaItem, kind: ArtworkSelectionKind) = "${kind.name}:${CanonicalArtworkIdentity.key(item)}"
}

internal data class ArtworkOption(val url: String, val label: String)

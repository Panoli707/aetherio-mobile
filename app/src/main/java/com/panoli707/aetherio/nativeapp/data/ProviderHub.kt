package com.panoli707.aetherio.nativeapp.data

/** Bundled public stream-only contract. No scripts, directory, catalog or meta requests. */
internal object ProviderHub {
    const val BASE = "https://aetherio-provider-hub.xoolinnbeats.workers.dev"
    const val MANIFEST_ID = "com.aetherio.hub"
    val config: AddonConfig = SourceProtocol.manifest(
        """{"id":"com.aetherio.hub","name":"Aetherio Provider Hub","resources":["stream"],"types":["movie","series"],"idPrefixes":["tt"]}""",
        SourceProtocol.manifestUrl("$BASE/manifest.json"))

    fun isHub(config: AddonConfig): Boolean = config.id == this.config.id && config.manifestUrl == this.config.manifestUrl
    fun withDefault(custom: List<AddonConfig>): List<AddonConfig> = listOf(config) + custom.filterNot(::isHub).distinctBy { it.id }
}

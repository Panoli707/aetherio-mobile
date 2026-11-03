package com.panoli707.aetherio.nativeapp.data

import android.content.Context

/** The managed registration is separate from the encrypted, user-owned addon store. */
internal class ProviderDefaultsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("aetherio_provider_defaults", Context.MODE_PRIVATE)
    fun initialize(custom: List<AddonConfig>): List<AddonConfig> = synchronized(LOCK) {
        if (!preferences.getBoolean("hub_registered_v1", false)) {
            check(preferences.edit().putBoolean("hub_registered_v1", true).commit()) { "No se pudo registrar Provider Hub" }
        }
        ProviderHub.withDefault(custom)
    }
    private companion object { val LOCK = Any() }
}

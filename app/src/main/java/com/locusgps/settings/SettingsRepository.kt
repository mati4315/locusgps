package com.locusgps.settings

import android.content.Context

class SettingsRepository(context: Context) {
    private val preferences = context.getSharedPreferences("locus-settings", Context.MODE_PRIVATE)

    var voiceEnabled: Boolean
        get() = preferences.getBoolean("voice_enabled", false)
        set(value) { preferences.edit().putBoolean("voice_enabled", value).apply() }

    var mapLayersEnabled: Boolean
        get() = preferences.getBoolean("map_layers_enabled", true)
        set(value) { preferences.edit().putBoolean("map_layers_enabled", value).apply() }
}

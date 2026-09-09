package com.locusgps.map

import com.locusgps.BuildConfig

/** Single configuration point: tiles can be swapped without affecting navigation logic. */
object MapStyleConfig {
    fun styleUrl(): String? = BuildConfig.MAPTILER_KEY
        .takeIf { it.isNotBlank() }
        ?.let { "https://api.maptiler.com/maps/streets-v2/style.json?key=$it" }
}

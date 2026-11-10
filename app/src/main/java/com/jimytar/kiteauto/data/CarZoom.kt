package com.jimytar.kiteauto.data

import android.content.Context

/**
 * Page zoom for the car screen, in percent, applied like browser zoom (the car display's density is scaled).
 * 73% default: on a 1292x499 BMW surface it puts m.youtube past its 1280 CSS px wide-layout breakpoint,
 * where the whole player (with its controls) fits on screen.
 */
class CarZoom(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("car", Context.MODE_PRIVATE)

    var percent: Int
        get() = prefs.getInt(KEY, DEFAULT)
        set(value) = prefs.edit().putInt(KEY, value.coerceIn(MIN, MAX)).apply()

    companion object {
        private const val KEY = "zoom"
        const val DEFAULT = 73
        const val MIN = 50
        const val MAX = 150
    }
}

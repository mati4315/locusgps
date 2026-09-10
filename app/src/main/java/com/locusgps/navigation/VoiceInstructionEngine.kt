package com.locusgps.navigation

import android.content.Context
import android.speech.tts.TextToSpeech
import com.locusgps.api.RoutePoint
import com.locusgps.api.RouteResult
import com.locusgps.location.UserLocation
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class VoiceInstructionEngine(context: Context) : TextToSpeech.OnInitListener {
    private val textToSpeech = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var lastInstructionIndex = -1
    var enabled: Boolean = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) textToSpeech.language = Locale("es", "ES")
    }

    fun announceRoute(distanceMeters: Double, durationSeconds: Int) {
        if (!enabled || !ready) return
        lastInstructionIndex = -1
        val distance = if (distanceMeters >= 1000) "%.1f kilómetros".format(distanceMeters / 1000) else "%.0f metros".format(distanceMeters)
        val minutes = (durationSeconds / 60).coerceAtLeast(1)
        textToSpeech.speak("Ruta iniciada. $distance. Tiempo estimado: $minutes minutos.", TextToSpeech.QUEUE_FLUSH, null, "route-start")
    }

    fun announceNextInstruction(route: RouteResult, location: UserLocation) {
        if (!enabled || !ready || route.instructions.isEmpty()) return
        val nearestIndex = route.geometry.indices.minByOrNull { index -> distance(route.geometry[index], RoutePoint(location.latitude, location.longitude)) } ?: return
        val nextIndex = route.instructions.indexOfFirst { it.intervalStart >= nearestIndex && route.instructions.indexOf(it) > lastInstructionIndex }
        if (nextIndex < 0 || nextIndex == lastInstructionIndex) return
        val instruction = route.instructions[nextIndex]
        val startPoint = route.geometry.getOrNull(instruction.intervalStart) ?: return
        if (distance(startPoint, RoutePoint(location.latitude, location.longitude)) <= 150.0) {
            lastInstructionIndex = nextIndex
            textToSpeech.speak(instruction.text, TextToSpeech.QUEUE_ADD, null, "instruction-$nextIndex")
        }
    }

    private fun distance(a: RoutePoint, b: RoutePoint): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * earthRadius * atan2(sqrt(h), sqrt(1 - h))
    }

    fun shutdown() {
        textToSpeech.stop()
        textToSpeech.shutdown()
    }
}

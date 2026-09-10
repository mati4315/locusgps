package com.locusgps.navigation

import com.locusgps.api.RoutePoint
import com.locusgps.api.RouteResult
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class SimulationCursor(val segment: Int = 0, val offsetMeters: Double = 0.0)

object RouteSimulator {
    fun advance(route: RouteResult, cursor: SimulationCursor, speedKmh: Float, seconds: Double): Pair<SimulationCursor, RoutePoint> {
        if (route.geometry.size < 2) return cursor to route.geometry.first()
        var segment = cursor.segment.coerceIn(0, route.geometry.lastIndex - 1)
        var offset = cursor.offsetMeters + speedKmh.coerceAtLeast(1f) / 3.6 * seconds
        while (segment < route.geometry.lastIndex - 1) {
            val length = distance(route.geometry[segment], route.geometry[segment + 1])
            if (offset <= length) break
            offset -= length
            segment++
        }
        val start = route.geometry[segment]
        val end = route.geometry[(segment + 1).coerceAtMost(route.geometry.lastIndex)]
        val length = distance(start, end).coerceAtLeast(1.0)
        val ratio = (offset / length).coerceIn(0.0, 1.0)
        val point = RoutePoint(start.latitude + (end.latitude - start.latitude) * ratio, start.longitude + (end.longitude - start.longitude) * ratio)
        return SimulationCursor(segment, offset) to point
    }

    private fun distance(a: RoutePoint, b: RoutePoint): Double {
        val radius = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * radius * atan2(sqrt(h), sqrt(1 - h))
    }
}

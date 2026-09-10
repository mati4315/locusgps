package com.locusgps.navigation

import com.locusgps.api.MapPoint
import com.locusgps.api.RoutePoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class PointAlert(val pointId: Long, val title: String, val distanceMeters: Double)

class PointAlertEngine {
    private val alerted = mutableSetOf<Long>()

    fun update(points: List<MapPoint>, location: RoutePoint): PointAlert? {
        var nearest: PointAlert? = null
        points.filter { it.alertEnabled }.forEach { point ->
            val distance = distanceMeters(location, RoutePoint(point.latitude, point.longitude))
            if (distance > 500) alerted.remove(point.id)
            if (distance <= 350 && point.id !in alerted) {
                if (nearest == null || distance < nearest!!.distanceMeters) nearest = PointAlert(point.id, point.name, distance)
            }
        }
        nearest?.let { alerted += it.pointId }
        return nearest
    }

    private fun distanceMeters(a: RoutePoint, b: RoutePoint): Double {
        val radius = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * radius * atan2(sqrt(h), sqrt(1 - h))
    }
}

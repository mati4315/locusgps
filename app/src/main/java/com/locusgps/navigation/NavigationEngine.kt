package com.locusgps.navigation

import com.locusgps.api.RoutePoint
import com.locusgps.api.RouteResult
import com.locusgps.location.UserLocation
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class NavigationState(val remainingMeters: Double, val distanceFromRouteMeters: Double, val offRoute: Boolean)

object NavigationEngine {
    private const val OFF_ROUTE_THRESHOLD_METERS = 80.0

    fun update(route: RouteResult, location: UserLocation?): NavigationState? {
        if (location == null || route.geometry.size < 2) return null
        val user = RoutePoint(location.latitude, location.longitude)
        var nearestIndex = 0
        var nearestDistance = Double.MAX_VALUE
        route.geometry.forEachIndexed { index, point ->
            val distance = distanceMeters(user, point)
            if (distance < nearestDistance) {
                nearestDistance = distance
                nearestIndex = index
            }
        }
        val remaining = if (nearestIndex >= route.geometry.lastIndex) 0.0 else {
            route.geometry.drop(nearestIndex).zipWithNext().sumOf { (a, b) -> distanceMeters(a, b) }
        }
        return NavigationState(remaining, nearestDistance, nearestDistance > OFF_ROUTE_THRESHOLD_METERS)
    }

    private fun distanceMeters(a: RoutePoint, b: RoutePoint): Double {
        val earthRadius = 6_371_000.0
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
        return 2 * earthRadius * atan2(sqrt(h), sqrt(1 - h))
    }
}

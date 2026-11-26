package com.ryda.app.core.location

import com.ryda.app.core.models.ClubRideWaypoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

data class RouteGuidanceStatus(
    val isOffRoute: Boolean,
    val distanceToRouteMeters: Double,
    val nearestPointLat: Double,
    val nearestPointLng: Double,
    val returnCardinal: String,
    val nextWaypointLabel: String,
    val distanceToNextWaypointMeters: Double
)

object RouteGuidanceCalculator {
    const val OFF_ROUTE_THRESHOLD_METERS = 150.0

    /**
     * Evaluates the rider's current position against the fixed planned route polyline
     * (or waypoints if polyline is unavailable). Returns off-route status, nearest point
     * on the route, cardinal return direction, and distance to the next upcoming pit stop.
     */
    fun evaluate(
        currentLat: Double,
        currentLng: Double,
        waypoints: List<ClubRideWaypoint>,
        encodedPolyline: String = ""
    ): RouteGuidanceStatus? {
        val routePoints: List<Pair<Double, Double>> = if (encodedPolyline.isNotBlank()) {
            PolylineDecoder.decode(encodedPolyline).map { Pair(it.latitude, it.longitude) }
        } else {
            waypoints.map { Pair(it.lat, it.lng) }
        }
        if (routePoints.isEmpty()) return null

        var minDistance = Double.MAX_VALUE
        var bestLat = routePoints[0].first
        var bestLng = routePoints[0].second
        var bestSegmentIndex = 0

        if (routePoints.size == 1) {
            minDistance = Haversine.distanceInMeters(currentLat, currentLng, bestLat, bestLng)
        } else {
            for (i in 0 until routePoints.size - 1) {
                val a = routePoints[i]
                val b = routePoints[i + 1]
                val (projLat, projLng) = projectPointOnSegment(
                    currentLat, currentLng,
                    a.first, a.second,
                    b.first, b.second
                )
                val d = Haversine.distanceInMeters(currentLat, currentLng, projLat, projLng)
                if (d < minDistance) {
                    minDistance = d
                    bestLat = projLat
                    bestLng = projLng
                    bestSegmentIndex = i
                }
            }
        }

        val nextWp = if (encodedPolyline.isBlank()) {
            waypoints.getOrNull(bestSegmentIndex + 1) ?: waypoints.lastOrNull()
        } else {
            waypoints.firstOrNull()
        }
        val nextLabel = nextWp?.label?.ifBlank { "Pit Stop" } ?: "Destination"
        val distToNext = if (nextWp != null) {
            Haversine.distanceInMeters(currentLat, currentLng, nextWp.lat, nextWp.lng)
        } else {
            minDistance
        }
        val bearing = bearingDegrees(currentLat, currentLng, bestLat, bestLng)

        return RouteGuidanceStatus(
            isOffRoute = minDistance > OFF_ROUTE_THRESHOLD_METERS,
            distanceToRouteMeters = minDistance,
            nearestPointLat = bestLat,
            nearestPointLng = bestLng,
            returnCardinal = cardinalDirection(bearing),
            nextWaypointLabel = nextLabel,
            distanceToNextWaypointMeters = distToNext
        )
    }

    private fun projectPointOnSegment(
        pLat: Double, pLng: Double,
        aLat: Double, aLng: Double,
        bLat: Double, bLng: Double
    ): Pair<Double, Double> {
        val dx = bLat - aLat
        val dy = bLng - aLng
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0.0) return Pair(aLat, aLng)
        val t = (((pLat - aLat) * dx + (pLng - aLng) * dy) / lenSq).coerceIn(0.0, 1.0)
        return Pair(aLat + t * dx, aLng + t * dy)
    }

    private fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    private fun cardinalDirection(deg: Double): String {
        val dirs = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")
        val idx = (((deg + 22.5) % 360.0) / 45.0).toInt().coerceIn(0, 7)
        return dirs[idx]
    }
}

package com.ryda.app.core.location

import com.ryda.app.core.models.ClubRideWaypoint
import com.ryda.app.core.models.ComputedRouteOption

enum class PlannerSelectionMode {
    START,
    DESTINATION,
    PIT_STOP
}

data class RoutePlannerState(
    val start: ResolvedPlace? = null,
    val destination: ResolvedPlace? = null,
    val pitStops: List<ClubRideWaypoint> = emptyList(),
    val alternatives: List<ComputedRouteOption> = emptyList(),
    val selectedRouteIndex: Int = 0,
    val selectionMode: PlannerSelectionMode = PlannerSelectionMode.START,
    val isComputingRoute: Boolean = false,
    val routeError: String? = null
) {
    val selectedRoute: ComputedRouteOption?
        get() = alternatives.getOrNull(selectedRouteIndex) ?: alternatives.firstOrNull()

    fun withPitStopAdded(stop: ClubRideWaypoint): RoutePlannerState =
        if (pitStops.size >= 20) this else copy(pitStops = pitStops + stop, alternatives = emptyList(), selectedRouteIndex = 0)

    fun withPitStopRemoved(index: Int): RoutePlannerState {
        if (index !in pitStops.indices) return this
        return copy(
            pitStops = pitStops.filterIndexed { i, _ -> i != index },
            alternatives = emptyList(),
            selectedRouteIndex = 0
        )
    }

    fun withPitStopMoved(fromIndex: Int, toIndex: Int): RoutePlannerState {
        if (fromIndex !in pitStops.indices || toIndex !in pitStops.indices || fromIndex == toIndex) return this
        val mutable = pitStops.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        return copy(pitStops = mutable, alternatives = emptyList(), selectedRouteIndex = 0)
    }

    fun withSelectedRoute(index: Int): RoutePlannerState {
        if (index !in alternatives.indices) return this
        return copy(selectedRouteIndex = index)
    }
}

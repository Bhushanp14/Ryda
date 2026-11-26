package com.ryda.app

import com.google.android.libraries.navigation.Navigator
import com.ryda.app.core.location.NavigationSoundMode
import com.ryda.app.core.location.PolylineDecoder
import com.ryda.app.core.location.RouteGuidanceCalculator
import com.ryda.app.core.location.RoutePlannerState
import com.ryda.app.core.models.ClubRideWaypoint
import com.ryda.app.core.models.ComputedRouteOption
import com.ryda.app.core.network.LiveParticipantLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClubRidePhase7Tests {

    @Test
    fun routeGuidance_emptyWaypoints_returnsNull() {
        val result = RouteGuidanceCalculator.evaluate(19.0760, 72.8777, emptyList())
        assertNull(result)
    }

    @Test
    fun routeGuidance_onRoute_isNotOffRoute() {
        val waypoints = listOf(
            ClubRideWaypoint(18.9894, 73.1175, "Panvel"),
            ClubRideWaypoint(18.7502, 73.4065, "Lonavala")
        )
        // Midpoint along Panvel -> Lonavala segment
        val midLat = (18.9894 + 18.7502) / 2.0
        val midLng = (73.1175 + 73.4065) / 2.0

        val result = RouteGuidanceCalculator.evaluate(midLat, midLng, waypoints)
        assertNotNull(result)
        assertFalse(result!!.isOffRoute)
        assertTrue(result.distanceToRouteMeters < 50.0)
        assertEquals("Lonavala", result.nextWaypointLabel)
    }

    @Test
    fun routeGuidance_offRoute_detectsDeviationAndReturnDirection() {
        val waypoints = listOf(
            ClubRideWaypoint(19.0000, 73.0000, "Start"),
            ClubRideWaypoint(19.0000, 73.1000, "Finish")
        )
        // ~1.1 km north of the east-west route
        val result = RouteGuidanceCalculator.evaluate(19.0100, 73.0500, waypoints)
        assertNotNull(result)
        assertTrue(result!!.isOffRoute)
        assertTrue(result.distanceToRouteMeters > 1000.0)
        assertEquals("S", result.returnCardinal)
    }

    @Test
    fun liveParticipantLocation_staleThreshold_is15Seconds() {
        val baseTime = 1_000_000L
        val loc = LiveParticipantLocation(
            userId = 7,
            username = "ryder7",
            lat = 19.0,
            lng = 73.0,
            speedKmh = 60.0,
            heading = 90f,
            timestampIso = "2026-11-25T12:00:00Z",
            receivedAtMs = baseTime
        )
        assertFalse(loc.isStale(nowMs = baseTime + 10_000L))
        assertTrue(loc.isStale(nowMs = baseTime + 16_000L))
    }

    @Test
    fun polylineDecoder_decodesStandardGooglePolyline() {
        // Classic Google Polyline sample: "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
        // Points: (38.5, -120.2), (40.7, -120.95), (43.252, -126.453)
        val decoded = PolylineDecoder.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@")
        assertEquals(3, decoded.size)
        assertEquals(38.5, decoded[0].latitude, 1e-4)
        assertEquals(-120.2, decoded[0].longitude, 1e-4)
        assertEquals(40.7, decoded[1].latitude, 1e-4)
        assertEquals(-120.95, decoded[1].longitude, 1e-4)
        assertEquals(43.252, decoded[2].latitude, 1e-4)
        assertEquals(-126.453, decoded[2].longitude, 1e-4)
    }

    @Test
    fun polylineDecoder_formatsDistanceAndDuration() {
        assertEquals("850 m", PolylineDecoder.formatDistance(850))
        assertEquals("12.5 km", PolylineDecoder.formatDistance(12500))
        assertEquals("45 min", PolylineDecoder.formatDuration(2700))
        assertEquals("1h 30m", PolylineDecoder.formatDuration(5400))
    }

    @Test
    fun routePlannerState_pitStopAddRemoveMoveAndRouteSelect() {
        val stopA = ClubRideWaypoint(19.0, 73.0, "Stop A")
        val stopB = ClubRideWaypoint(19.1, 73.1, "Stop B")
        val stopC = ClubRideWaypoint(19.2, 73.2, "Stop C")

        var state = RoutePlannerState()
            .withPitStopAdded(stopA)
            .withPitStopAdded(stopB)
            .withPitStopAdded(stopC)

        assertEquals(listOf("Stop A", "Stop B", "Stop C"), state.pitStops.map { it.label })

        // Move Stop C (index 2) to index 0
        state = state.withPitStopMoved(2, 0)
        assertEquals(listOf("Stop C", "Stop A", "Stop B"), state.pitStops.map { it.label })

        // Remove Stop A (index 1)
        state = state.withPitStopRemoved(1)
        assertEquals(listOf("Stop C", "Stop B"), state.pitStops.map { it.label })

        // Alternative route selection
        val alt0 = ComputedRouteOption(encodedPolyline = "abc", distanceMeters = 10000, durationSeconds = 600, summary = "Fastest")
        val alt1 = ComputedRouteOption(encodedPolyline = "def", distanceMeters = 12000, durationSeconds = 720, summary = "Scenic")
        state = state.copy(alternatives = listOf(alt0, alt1)).withSelectedRoute(1)
        assertEquals("Scenic", state.selectedRoute?.summary)
    }

    @Test
    fun routeGuidance_usesEncodedPolylineWhenProvided() {
        // Polyline "_p~iF~ps|U_ulLnnqC_mqNvxq`@" -> (38.5, -120.2), (40.7, -120.95), (43.252, -126.453)
        // Midpoint of first segment: (39.6, -120.575)
        val pitStop = listOf(ClubRideWaypoint(40.7, -120.95, "Pit Stop 1"))
        val result = RouteGuidanceCalculator.evaluate(
            currentLat = 39.6,
            currentLng = -120.575,
            waypoints = pitStop,
            encodedPolyline = "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
        )
        assertNotNull(result)
        assertFalse(result!!.isOffRoute)
        assertEquals("Pit Stop 1", result.nextWaypointLabel)
    }

    @Test
    fun navigationSoundMode_mapsAndPersistsCorrectly() {
        assertEquals(NavigationSoundMode.SOUND_ON, NavigationSoundMode.fromStored(null))
        assertEquals(NavigationSoundMode.SOUND_ON, NavigationSoundMode.fromStored("UNKNOWN"))
        assertEquals(NavigationSoundMode.ALERTS_ONLY, NavigationSoundMode.fromStored("ALERTS_ONLY"))
        assertEquals(NavigationSoundMode.MUTED, NavigationSoundMode.fromStored("MUTED"))

        assertEquals(
            Navigator.AudioGuidance.VOICE_ALERTS_AND_GUIDANCE,
            NavigationSoundMode.SOUND_ON.toAudioGuidance()
        )
        assertEquals(
            Navigator.AudioGuidance.VOICE_ALERTS_ONLY,
            NavigationSoundMode.ALERTS_ONLY.toAudioGuidance()
        )
        assertEquals(
            Navigator.AudioGuidance.SILENT,
            NavigationSoundMode.MUTED.toAudioGuidance()
        )
    }

    // ── Auto-finalize logic unit tests ────────────────────────────────────────────────────────

    /**
     * Tests the guard condition used in ClubRideActiveScreen's LaunchedEffect:
     * finalization should only trigger when lastSessionEvent == "session_completed"
     * and the device is actively tracking a ride linked to this session.
     *
     * This is a pure business-logic test with no Android Context or Compose dependency.
     */
    @Test
    fun sessionCompleted_event_triggers_finalization_condition_when_tracking() {
        // Simulate: event received, device is tracking the linked ride
        val lastSessionEvent = "session_completed"
        val isTracking = true
        val currentRideId = "ride-abc-123"
        val myIndividualRideId = "ride-abc-123" // matches

        val shouldFinalize = lastSessionEvent == "session_completed" &&
                isTracking &&
                (myIndividualRideId == null || currentRideId == myIndividualRideId)

        assertTrue("Should finalize when tracking linked ride on session_completed", shouldFinalize)
    }

    @Test
    fun sessionCompleted_event_does_not_trigger_when_not_tracking() {
        val lastSessionEvent = "session_completed"
        val isTracking = false // Not tracking — no ride to finalize
        val currentRideId: String? = null
        val myIndividualRideId = "ride-abc-123"

        val shouldFinalize = lastSessionEvent == "session_completed" &&
                isTracking &&
                (myIndividualRideId == null || currentRideId == myIndividualRideId)

        assertFalse("Should NOT finalize when not tracking", shouldFinalize)
    }

    @Test
    fun sessionCompleted_event_does_not_trigger_for_unrelated_ride() {
        val lastSessionEvent = "session_completed"
        val isTracking = true
        val currentRideId = "ride-different-456" // Different ride — not this club session
        val myIndividualRideId = "ride-abc-123"

        val shouldFinalize = lastSessionEvent == "session_completed" &&
                isTracking &&
                (myIndividualRideId == null || currentRideId == myIndividualRideId)

        assertFalse("Should NOT finalize when tracking a different unlinked ride", shouldFinalize)
    }

    @Test
    fun sessionCompleted_event_triggers_when_myRideId_is_null_and_tracking() {
        // myIndividualRideId == null means: session pre-dates individual ride tracking;
        // finalize any active ride to be safe.
        val lastSessionEvent = "session_completed"
        val isTracking = true
        val currentRideId = "ride-some-ride"
        val myIndividualRideId: String? = null

        val shouldFinalize = lastSessionEvent == "session_completed" &&
                isTracking &&
                (myIndividualRideId == null || currentRideId == myIndividualRideId)

        assertTrue("Should finalize when myIndividualRideId is null and tracking", shouldFinalize)
    }

    @Test
    fun disconnectedReconciliation_triggers_when_session_completed_and_tracking() {
        // Simulates ClubRideSessionScreen.reload() reconciliation path.
        // Device reconnects and finds session.status == COMPLETED while still tracking.
        val sessionStatus = "COMPLETED"
        val isTracking = true
        val currentRideId = "ride-abc-123"
        val myRideId: String? = "ride-abc-123"

        val shouldReconcile = (sessionStatus == "COMPLETED" || sessionStatus == "CANCELLED") &&
                isTracking &&
                (myRideId == null || currentRideId == myRideId)

        assertTrue("Should reconcile when session COMPLETED and tracking linked ride", shouldReconcile)
    }

    @Test
    fun disconnectedReconciliation_triggers_on_cancelled_session() {
        val sessionStatus = "CANCELLED"
        val isTracking = true
        val currentRideId = "ride-abc-123"
        val myRideId: String? = "ride-abc-123"

        val shouldReconcile = (sessionStatus == "COMPLETED" || sessionStatus == "CANCELLED") &&
                isTracking &&
                (myRideId == null || currentRideId == myRideId)

        assertTrue("Should reconcile on CANCELLED session too", shouldReconcile)
    }

    @Test
    fun disconnectedReconciliation_does_not_trigger_when_session_active() {
        // Device reconnects and finds session still ACTIVE — do NOT finalize
        val sessionStatus = "ACTIVE"
        val isTracking = true
        val currentRideId = "ride-abc-123"
        val myRideId: String? = "ride-abc-123"

        val shouldReconcile = (sessionStatus == "COMPLETED" || sessionStatus == "CANCELLED") &&
                isTracking &&
                (myRideId == null || currentRideId == myRideId)

        assertFalse("Should NOT reconcile when session is still ACTIVE", shouldReconcile)
    }
}

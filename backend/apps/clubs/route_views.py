"""
Backend proxy for Google Routes API (v2: computeRoutes).
Keeps server-side Google Routes API credentials off the Android client,
enforces role authorization, input coordinate validation, and per-user rate limiting.
"""
import json
import time
import urllib.error
import urllib.request
from collections import defaultdict

from django.conf import settings
from rest_framework import permissions, status, views
from rest_framework.response import Response

from .models import ClubMember

ROUTES_API_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
FIELD_MASK = (
    "routes.duration,"
    "routes.distanceMeters,"
    "routes.polyline.encodedPolyline,"
    "routes.description,"
    "routes.routeLabels"
)

# Simple sliding-window rate limit: max 30 route computations per 60 seconds per user.
_RATE_LIMIT_WINDOW_SEC = 60.0
_RATE_LIMIT_MAX_REQUESTS = 30
_USER_REQUEST_TIMESTAMPS = defaultdict(list)


def _check_rate_limit(user_id: int) -> bool:
    now = time.monotonic()
    history = _USER_REQUEST_TIMESTAMPS[user_id]
    history[:] = [t for t in history if (now - t) < _RATE_LIMIT_WINDOW_SEC]
    if len(history) >= _RATE_LIMIT_MAX_REQUESTS:
        return False
    history.append(now)
    return True


def _validate_lat_lng(point, label):
    if not isinstance(point, dict):
        raise ValueError(f"{label} must be an object with 'lat' and 'lng'.")
    try:
        lat = float(point['lat'])
        lng = float(point['lng'])
    except (KeyError, TypeError, ValueError):
        raise ValueError(f"{label} must contain numeric 'lat' and 'lng'.")
    if not (-90.0 <= lat <= 90.0) or not (-180.0 <= lng <= 180.0):
        raise ValueError(f"{label} coordinates are out of valid bounds.")
    return lat, lng


def _parse_duration_seconds(duration_str: str) -> int:
    if not duration_str:
        return 0
    cleaned = duration_str.strip().rstrip('s')
    try:
        return int(float(cleaned))
    except (TypeError, ValueError):
        return 0


def _call_google_routes_api(api_key: str, payload: dict) -> dict:
    body = json.dumps(payload).encode('utf-8')
    req = urllib.request.Request(
        ROUTES_API_URL,
        data=body,
        headers={
            'Content-Type': 'application/json',
            'X-Goog-Api-Key': api_key,
            'X-Goog-FieldMask': FIELD_MASK,
        },
        method='POST',
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.loads(resp.read().decode('utf-8'))


class ClubRideRouteComputeView(views.APIView):
    """
    POST /api/v1/clubs/routes/compute/
    Computes road routes (and alternatives when no intermediates are provided)
    via Google Routes API v2.
    """
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request):
        if not _check_rate_limit(request.user.id):
            return Response(
                {'detail': "Rate limit exceeded. Please wait before computing more routes."},
                status=status.HTTP_429_TOO_MANY_REQUESTS,
            )

        club_id = request.data.get('club_id')
        if club_id:
            is_leader = ClubMember.objects.filter(
                club_id=club_id, user=request.user, role='LEADER'
            ).exists()
        else:
            is_leader = ClubMember.objects.filter(
                user=request.user, role='LEADER'
            ).exists()

        if not is_leader:
            return Response(
                {'detail': "Only club leaders can plan club ride routes."},
                status=status.HTTP_403_FORBIDDEN,
            )

        try:
            orig_lat, orig_lng = _validate_lat_lng(request.data.get('origin'), 'origin')
            dest_lat, dest_lng = _validate_lat_lng(request.data.get('destination'), 'destination')
        except ValueError as e:
            return Response({'detail': str(e)}, status=status.HTTP_400_BAD_REQUEST)

        intermediates_raw = request.data.get('intermediates') or []
        if not isinstance(intermediates_raw, list):
            return Response(
                {'detail': "'intermediates' must be a list of coordinate objects."},
                status=status.HTTP_400_BAD_REQUEST,
            )
        if len(intermediates_raw) > 20:
            return Response(
                {'detail': "Maximum 20 intermediate pit stops are allowed."},
                status=status.HTTP_400_BAD_REQUEST,
            )

        intermediates_payload = []
        for idx, item in enumerate(intermediates_raw):
            try:
                lat, lng = _validate_lat_lng(item, f"intermediates[{idx}]")
            except ValueError as e:
                return Response({'detail': str(e)}, status=status.HTTP_400_BAD_REQUEST)
            intermediates_payload.append({
                'location': {
                    'latLng': {'latitude': lat, 'longitude': lng}
                }
            })

        api_key = getattr(settings, 'GOOGLE_MAPS_API_KEY', '') or ''
        if not api_key:
            return Response(
                {'detail': "Google Routes API key is not configured on the server (GOOGLE_MAPS_API_KEY)."},
                status=status.HTTP_503_SERVICE_UNAVAILABLE,
            )

        compute_alternatives = bool(request.data.get('compute_alternatives', True))
        # Google Routes API v2 only supports computeAlternativeRoutes when intermediates is empty.
        enable_alternatives = compute_alternatives and len(intermediates_payload) == 0

        requested_mode = str(request.data.get('travel_mode', 'TWO_WHEELER') or 'TWO_WHEELER').upper()
        if requested_mode != 'TWO_WHEELER':
            return Response(
                {'detail': "Club Ride routes only support TWO_WHEELER motorcycle routing. Car or bicycle fallback is disabled."},
                status=status.HTTP_400_BAD_REQUEST,
            )
        travel_mode = 'TWO_WHEELER'

        google_payload = {
            'origin': {'location': {'latLng': {'latitude': orig_lat, 'longitude': orig_lng}}},
            'destination': {'location': {'latLng': {'latitude': dest_lat, 'longitude': dest_lng}}},
            'travelMode': travel_mode,
            'routingPreference': 'TRAFFIC_AWARE',
            'computeAlternativeRoutes': enable_alternatives,
            'polylineQuality': 'HIGH_QUALITY',
            'polylineEncoding': 'ENCODED_POLYLINE',
        }
        if intermediates_payload:
            google_payload['intermediates'] = intermediates_payload

        try:
            raw_resp = _call_google_routes_api(api_key, google_payload)
        except urllib.error.HTTPError as e:
            err_body = ''
            google_msg = ''
            google_status = ''
            try:
                err_body = e.read().decode('utf-8')
                parsed_err = json.loads(err_body)
                err_obj = parsed_err.get('error') or {}
                google_msg = err_obj.get('message', '')
                google_status = err_obj.get('status', '')
            except Exception:
                pass
            if e.code == 404 or google_status in ('NOT_FOUND', 'ZERO_RESULTS'):
                return Response(
                    {'detail': "No supported motorcycle route is available for the selected points."},
                    status=status.HTTP_404_NOT_FOUND,
                )
            detail_msg = google_msg or err_body[:300] or f"HTTP {e.code}"
            return Response(
                {'detail': f"Google Motorcycle Routes API error ({google_status or f'HTTP {e.code}'}): {detail_msg}"},
                status=status.HTTP_502_BAD_GATEWAY,
            )
        except Exception as e:
            return Response(
                {'detail': f"Failed to reach Google Routes API: {str(e)}"},
                status=status.HTTP_502_BAD_GATEWAY,
            )

        raw_routes = raw_resp.get('routes', [])
        if not raw_routes:
            return Response(
                {'detail': "No supported motorcycle route is available for the selected points."},
                status=status.HTTP_404_NOT_FOUND,
            )

        routes_out = []
        for idx, route in enumerate(raw_routes):
            encoded = (route.get('polyline') or {}).get('encodedPolyline', '')
            if not encoded:
                continue
            labels = route.get('routeLabels') or []
            routes_out.append({
                'index': idx,
                'summary': route.get('description') or f"Route {idx + 1}",
                'distance_meters': int(route.get('distanceMeters') or 0),
                'duration_seconds': _parse_duration_seconds(route.get('duration', '')),
                'encoded_polyline': encoded,
                'is_default': 'DEFAULT_ROUTE' in labels or idx == 0,
            })

        if not routes_out:
            return Response(
                {'detail': "Google Routes API returned no valid route polylines."},
                status=status.HTTP_502_BAD_GATEWAY,
            )

        return Response({
            'travel_mode': 'TWO_WHEELER',
            'beta_notice': "Two-wheeler routes are in beta and may not always provide optimal motorcycle paths.",
            'routes': routes_out,
        }, status=status.HTTP_200_OK)

import math

EARTH_RADIUS_METERS = 6371000.0

def haversine_distance_meters(lat1, lon1, lat2, lon2):
    """Calculates spherical distance between two points in meters."""
    d_lat = math.radians(lat2 - lat1)
    d_lon = math.radians(lon2 - lon1)
    a = (math.sin(d_lat / 2.0) ** 2 +
         math.cos(math.radians(lat1)) * math.cos(math.radians(lat2)) *
         math.sin(d_lon / 2.0) ** 2)
    c = 2.0 * math.atan2(math.sqrt(a), math.sqrt(1.0 - a))
    return EARTH_RADIUS_METERS * c

def recalculate_ride_statistics(gps_points):
    """
    Given a list or queryset of GpsPoint objects ordered by sequence_number,
    calculates trusted server-side total distance, moving time, elapsed time, stopped time, max speed, avg speed, elevation gain and elevation loss.
    """
    if len(gps_points) < 2:
        return {
            'total_distance_meters': 0.0,
            'moving_time_seconds': 0,
            'elapsed_time_seconds': 0,
            'stopped_time_seconds': 0,
            'max_speed_kmh': 0.0,
            'avg_speed_kmh': 0.0,
            'elevation_gain_meters': 0.0,
            'elevation_loss_meters': 0.0,
            'highest_elevation_meters': 0.0,
            'lowest_elevation_meters': 0.0
        }

    total_distance = 0.0
    moving_time_seconds = 0
    max_speed_kmh = 0.0

    points = list(gps_points)
    start_time = points[0].timestamp
    end_time = points[-1].timestamp
    elapsed_time_seconds = max(0, int((end_time - start_time).total_seconds()))

    # Filter altitude noise
    valid_alt_points = [p for p in points if p.accuracy <= 25.0 and p.altitude != 0.0]
    elevation_gain = 0.0
    elevation_loss = 0.0
    highest_alt = max([p.altitude for p in valid_alt_points], default=0.0)
    lowest_alt = min([p.altitude for p in valid_alt_points], default=0.0)

    if len(valid_alt_points) >= 2:
        prev_alt = valid_alt_points[0].altitude
        for i in range(1, len(valid_alt_points)):
            curr_alt = valid_alt_points[i].altitude
            delta = curr_alt - prev_alt
            if abs(delta) >= 3.0: # Minimum 3.0m threshold delta to suppress GPS altitude noise
                if delta > 0:
                    elevation_gain += delta
                else:
                    elevation_loss += abs(delta)
                prev_alt = curr_alt

    for i in range(1, len(points)):
        prev = points[i - 1]
        curr = points[i]

        if curr.accuracy > 25.0:
            continue

        step_dist = haversine_distance_meters(prev.latitude, prev.longitude, curr.latitude, curr.longitude)
        time_diff = max(0.001, (curr.timestamp - prev.timestamp).total_seconds())
        step_speed_kmh = (step_dist / time_diff) * 3.6

        if step_speed_kmh > 180.0:
            continue

        total_distance += step_dist

        reported_speed = curr.speed_kmh or step_speed_kmh
        max_speed_kmh = max(max_speed_kmh, reported_speed)

        if reported_speed >= 5.4 or step_dist > 3.0:
            moving_time_seconds += int(time_diff)

    moving_time_seconds = min(moving_time_seconds, elapsed_time_seconds)
    stopped_time_seconds = max(0, elapsed_time_seconds - moving_time_seconds)
    avg_speed_kmh = (total_distance / moving_time_seconds * 3.6) if moving_time_seconds > 0 else 0.0

    return {
        'total_distance_meters': round(total_distance, 2),
        'moving_time_seconds': moving_time_seconds,
        'elapsed_time_seconds': elapsed_time_seconds,
        'stopped_time_seconds': stopped_time_seconds,
        'max_speed_kmh': round(max_speed_kmh, 1),
        'avg_speed_kmh': round(avg_speed_kmh, 1),
        'elevation_gain_meters': round(elevation_gain, 1),
        'elevation_loss_meters': round(elevation_loss, 1),
        'highest_elevation_meters': round(highest_alt, 1),
        'lowest_elevation_meters': round(lowest_alt, 1)
    }

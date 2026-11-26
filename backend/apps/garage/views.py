from rest_framework import viewsets, permissions
from rest_framework.decorators import action
from rest_framework.response import Response
from django.db.models import Sum, Avg, Max, Count
from .models import Motorcycle
from .serializers import MotorcycleSerializer
from apps.rides.models import Ride, RideStatus

class MotorcycleViewSet(viewsets.ModelViewSet):
    serializer_class = MotorcycleSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        return Motorcycle.objects.filter(owner=self.request.user)

    def perform_create(self, serializer):
        serializer.save(owner=self.request.user)

    @action(detail=True, methods=['get'])
    def stats(self, request, pk=None):
        motorcycle = self.get_object()
        completed_rides = Ride.objects.filter(
            rider=request.user,
            motorcycle=motorcycle,
            status=RideStatus.COMPLETED
        )

        agg = completed_rides.aggregate(
            total_distance_meters=Sum('total_distance_meters'),
            total_rides=Count('id'),
            total_riding_time=Sum('moving_time_seconds'),
            avg_speed=Avg('avg_speed_kmh')
        )

        total_distance_m = agg['total_distance_meters'] or 0.0
        total_rides = agg['total_rides'] or 0
        total_riding_time = agg['total_riding_time'] or 0
        avg_speed = agg['avg_speed'] or 0.0
        total_distance_km = total_distance_m / 1000.0

        avg_distance_km = (total_distance_km / total_rides) if total_rides > 0 else 0.0

        longest_ride = completed_rides.order_by('-total_distance_meters').first()
        longest_ride_km = (longest_ride.total_distance_meters / 1000.0) if longest_ride else 0.0

        monthly_data = {}
        yearly_data = {}
        for r in completed_rides.filter(start_time__isnull=False):
            month_key = r.start_time.strftime('%Y-%m')
            monthly_data[month_key] = monthly_data.get(month_key, 0.0) + (r.total_distance_meters / 1000.0)

            year_key = r.start_time.strftime('%Y')
            yearly_data[year_key] = yearly_data.get(year_key, 0.0) + (r.total_distance_meters / 1000.0)

        data = {
            'motorcycle_id': str(motorcycle.id),
            'motorcycle_name': motorcycle.displayName,
            'total_distance_km': round(total_distance_km, 2),
            'total_distance_meters': round(total_distance_m, 2),
            'total_rides': total_rides,
            'total_riding_time_seconds': total_riding_time,
            'avg_distance_km': round(avg_distance_km, 2),
            'longest_ride_km': round(longest_ride_km, 2),
            'longest_ride_id': str(longest_ride.id) if longest_ride else None,
            'longest_ride_title': longest_ride.title if longest_ride else None,
            'avg_speed_kmh': round(avg_speed, 1),
            'monthly_distance': {k: round(v, 2) for k, v in sorted(monthly_data.items())},
            'yearly_distance': {k: round(v, 2) for k, v in sorted(yearly_data.items())},
        }
        return Response(data)

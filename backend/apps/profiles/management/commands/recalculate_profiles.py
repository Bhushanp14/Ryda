"""
Management command to recalculate profile statistics and personal records for all users based on historical completed rides.
"""
from django.core.management.base import BaseCommand
from django.contrib.auth.models import User
from apps.rides.models import Ride, RideStatus
from apps.profiles.models import RiderProfile


class Command(BaseCommand):
    help = "Recalculate profile statistics and personal records for all users based on historical completed rides."

    def handle(self, *args, **options):
        users = User.objects.all()
        count = 0
        for user in users:
            completed_rides = Ride.objects.filter(rider=user, status=RideStatus.COMPLETED).order_by('start_time')
            total_rides = completed_rides.count()
            total_dist_km = sum(r.total_distance_km for r in completed_rides)
            total_hours = sum(r.moving_time_seconds for r in completed_rides) / 3600.0

            longest_ride_km = 0.0
            longest_duration_sec = 0
            monthly_dist_map = {}
            monthly_count_map = {}

            for r in completed_rides:
                rk = r.total_distance_km
                if rk > longest_ride_km:
                    longest_ride_km = rk
                if r.moving_time_seconds > longest_duration_sec:
                    longest_duration_sec = r.moving_time_seconds

                if r.start_time:
                    month_key = (r.start_time.year, r.start_time.month)
                    monthly_dist_map[month_key] = monthly_dist_map.get(month_key, 0.0) + rk
                    monthly_count_map[month_key] = monthly_count_map.get(month_key, 0) + 1

            highest_monthly_km = max(monthly_dist_map.values(), default=0.0)
            most_rides_month = max(monthly_count_map.values(), default=0)

            profile, _ = RiderProfile.objects.get_or_create(user=user, defaults={'display_name': user.username})
            profile.total_rides = total_rides
            profile.total_distance_km = round(total_dist_km, 2)
            profile.total_time_hours = round(total_hours, 2)
            profile.longest_ride_km = round(longest_ride_km, 2)
            profile.highest_monthly_distance_km = round(highest_monthly_km, 2)
            profile.most_rides_monthly = most_rides_month
            profile.longest_duration_seconds = longest_duration_sec
            profile.save()
            count += 1

        self.stdout.write(self.style.SUCCESS(f"Recalculated profiles and personal records for {count} user(s)."))

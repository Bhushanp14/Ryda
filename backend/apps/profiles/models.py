from django.db import models
from django.contrib.auth.models import User

class RiderProfile(models.Model):
    user = models.OneToOneField(User, on_delete=models.CASCADE, related_name='rider_profile')
    display_name = models.CharField(max_length=100)
    bio = models.TextField(blank=True, default='')
    city = models.CharField(max_length=100, blank=True, default='')
    avatar_url = models.CharField(max_length=255, blank=True, default='')
    is_profile_public = models.BooleanField(default=False)
    total_rides = models.PositiveIntegerField(default=0)
    total_distance_km = models.FloatField(default=0.0)
    total_time_hours = models.FloatField(default=0.0)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    def __str__(self):
        return f"{self.display_name} ({self.user.username})"

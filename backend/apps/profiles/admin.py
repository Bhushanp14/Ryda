from django.contrib import admin
from .models import RiderProfile

@admin.register(RiderProfile)
class RiderProfileAdmin(admin.ModelAdmin):
    list_display = ('display_name', 'user', 'city', 'avatar_url', 'total_rides', 'total_distance_km', 'total_time_hours', 'updated_at')
    search_fields = ('display_name', 'user__username', 'city')

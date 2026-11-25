from django.contrib import admin
from .models import Ride, GpsPoint, RideUploadBatch

@admin.register(Ride)
class RideAdmin(admin.ModelAdmin):
    list_display = ('title', 'rider', 'status', 'total_distance_km', 'moving_time_seconds', 'elapsed_time_seconds', 'avg_speed_kmh', 'max_speed_kmh', 'elevation_gain_meters', 'elevation_loss_meters', 'is_private', 'start_time')
    list_filter = ('status', 'is_private')
    search_fields = ('title', 'rider__username')

@admin.register(GpsPoint)
class GpsPointAdmin(admin.ModelAdmin):
    list_display = ('ride', 'sequence_number', 'timestamp', 'latitude', 'longitude', 'altitude', 'speed_kmh')
    search_fields = ('ride__title', 'ride__id')

@admin.register(RideUploadBatch)
class RideUploadBatchAdmin(admin.ModelAdmin):
    list_display = ('batch_id', 'ride', 'sequence_start', 'sequence_end', 'received_at')

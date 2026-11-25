import uuid
from django.db import models
from django.contrib.auth.models import User
from apps.garage.models import Motorcycle

class RideStatus(models.TextChoices):
    RECORDING = 'RECORDING', 'Recording'
    PAUSED = 'PAUSED', 'Paused'
    COMPLETED = 'COMPLETED', 'Completed'
    DISCARDED = 'DISCARDED', 'Discarded'

class Ride(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    rider = models.ForeignKey(User, on_delete=models.CASCADE, related_name='rides')
    motorcycle = models.ForeignKey(Motorcycle, on_delete=models.SET_NULL, null=True, blank=True, related_name='rides')
    title = models.CharField(max_length=150, default='Motorcycle Ride')
    status = models.CharField(max_length=20, choices=RideStatus.choices, default=RideStatus.RECORDING)
    start_time = models.DateTimeField()
    end_time = models.DateTimeField(null=True, blank=True)
    total_distance_meters = models.FloatField(default=0.0)
    moving_time_seconds = models.PositiveIntegerField(default=0)
    elapsed_time_seconds = models.PositiveIntegerField(default=0)
    max_speed_kmh = models.FloatField(default=0.0)
    avg_speed_kmh = models.FloatField(default=0.0)
    elevation_gain_meters = models.FloatField(default=0.0)
    elevation_loss_meters = models.FloatField(default=0.0)
    is_private = models.BooleanField(default=True)
    image_url = models.CharField(max_length=500, blank=True, default='')
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ['-start_time']

    @property
    def total_distance_km(self):
        return self.total_distance_meters / 1000.0

    def __str__(self):
        return f"{self.title} ({self.rider.username}) - {self.total_distance_km:.1f} km"

class GpsPoint(models.Model):
    ride = models.ForeignKey(Ride, on_delete=models.CASCADE, related_name='gps_points')
    sequence_number = models.PositiveIntegerField()
    timestamp = models.DateTimeField()
    latitude = models.FloatField()
    longitude = models.FloatField()
    altitude = models.FloatField(default=0.0)
    accuracy = models.FloatField(default=0.0)
    speed_kmh = models.FloatField(default=0.0)

    class Meta:
        ordering = ['sequence_number']
        unique_together = ('ride', 'sequence_number')
        indexes = [
            models.Index(fields=['ride', 'sequence_number']),
            models.Index(fields=['ride', 'timestamp']),
        ]

    def __str__(self):
        return f"Fix #{self.sequence_number} for {self.ride_id} ({self.latitude}, {self.longitude})"

class RideUploadBatch(models.Model):
    batch_id = models.CharField(max_length=64, unique=True)
    ride = models.ForeignKey(Ride, on_delete=models.CASCADE, related_name='upload_batches')
    batch_index = models.IntegerField(default=0)
    sequence_start = models.PositiveIntegerField()
    sequence_end = models.PositiveIntegerField()
    received_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        ordering = ['-received_at']
        unique_together = ('ride', 'batch_index')

    def __str__(self):
        return f"Batch {self.batch_id} [idx {self.batch_index}] (Seq {self.sequence_start}-{self.sequence_end}) for {self.ride_id}"

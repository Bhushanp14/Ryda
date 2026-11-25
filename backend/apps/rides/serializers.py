from rest_framework import serializers
from .models import Ride, GpsPoint, RideUploadBatch

class GpsPointSerializer(serializers.ModelSerializer):
    class Meta:
        model = GpsPoint
        fields = (
            'id', 'sequence_number', 'timestamp', 'latitude', 'longitude',
            'altitude', 'accuracy', 'speed_kmh'
        )

class RideSerializer(serializers.ModelSerializer):
    total_distance_km = serializers.FloatField(read_only=True)
    motorcycle_name = serializers.CharField(source='motorcycle.displayName', read_only=True)
    stopped_time_seconds = serializers.SerializerMethodField()

    class Meta:
        model = Ride
        fields = (
            'id', 'motorcycle', 'motorcycle_name', 'title', 'status',
            'start_time', 'end_time', 'total_distance_meters', 'total_distance_km',
            'moving_time_seconds', 'elapsed_time_seconds', 'stopped_time_seconds', 'max_speed_kmh',
            'avg_speed_kmh', 'elevation_gain_meters', 'elevation_loss_meters', 'is_private', 'image_url', 'created_at'
        )
        read_only_fields = ('created_at',)
        extra_kwargs = {
            'id': {'read_only': False, 'required': False},
            'motorcycle': {'required': False, 'allow_null': True}
        }

    def to_internal_value(self, data):
        # If motorcycle ID does not exist on server, gracefully fallback to None so ride upload succeeds
        if isinstance(data, dict) and 'motorcycle' in data and data['motorcycle']:
            from apps.garage.models import Motorcycle
            moto_id = data['motorcycle']
            if not Motorcycle.objects.filter(pk=moto_id).exists():
                data = data.copy()
                data['motorcycle'] = None
        return super().to_internal_value(data)

    def get_stopped_time_seconds(self, obj):
        return max(0, obj.elapsed_time_seconds - obj.moving_time_seconds)

class GpsPointBatchUploadSerializer(serializers.Serializer):
    batch_id = serializers.CharField(max_length=64)
    batch_index = serializers.IntegerField()
    sequence_start = serializers.IntegerField()
    sequence_end = serializers.IntegerField()
    points = GpsPointSerializer(many=True)

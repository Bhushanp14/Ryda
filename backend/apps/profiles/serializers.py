from rest_framework import serializers
from .models import RiderProfile

class RiderProfileSerializer(serializers.ModelSerializer):
    id = serializers.CharField(read_only=True)
    username = serializers.CharField(source='user.username', read_only=True)
    email = serializers.CharField(source='user.email', read_only=True)

    class Meta:
        model = RiderProfile
        fields = (
            'id', 'username', 'email', 'display_name', 'bio', 'city', 'avatar_url',
            'is_profile_public', 'total_rides', 'total_distance_km',
            'total_time_hours', 'created_at', 'updated_at'
        )
        read_only_fields = ('total_rides', 'total_distance_km', 'total_time_hours')

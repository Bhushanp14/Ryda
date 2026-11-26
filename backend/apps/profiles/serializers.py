from rest_framework import serializers
from .models import RiderProfile

class RiderProfileSerializer(serializers.ModelSerializer):
    id = serializers.CharField(read_only=True)
    username = serializers.CharField(source='user.username', read_only=True)
    email = serializers.CharField(source='user.email', read_only=True)
    date_joined = serializers.SerializerMethodField()
    followers_count = serializers.SerializerMethodField()
    following_count = serializers.SerializerMethodField()

    class Meta:
        model = RiderProfile
        fields = (
            'id', 'username', 'email', 'display_name', 'bio', 'city', 'avatar_url',
            'date_joined', 'is_profile_public', 'followers_count', 'following_count',
            'total_rides', 'total_distance_km', 'total_time_hours',
            'longest_ride_km', 'highest_monthly_distance_km', 'most_rides_monthly',
            'longest_duration_seconds', 'active_tag_ids', 'showcased_achievement_ids',
            'created_at', 'updated_at'
        )
        read_only_fields = ('total_rides', 'total_distance_km', 'total_time_hours')

    def get_date_joined(self, obj):
        return obj.user.date_joined.strftime("%b %Y") if obj.user.date_joined else "Jan 2024"

    def get_followers_count(self, obj):
        return getattr(obj.user, 'followers', None).count() if hasattr(obj.user, 'followers') else 0

    def get_following_count(self, obj):
        return getattr(obj.user, 'following', None).count() if hasattr(obj.user, 'following') else 0

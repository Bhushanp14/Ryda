from rest_framework import serializers
from .models import Post, PostLike, PostComment, RiderFollow
from apps.profiles.models import RiderProfile
from apps.rides.models import Ride

class PostCommentSerializer(serializers.ModelSerializer):
    user_id = serializers.IntegerField(source='user.id', read_only=True)
    username = serializers.CharField(source='user.username', read_only=True)
    display_name = serializers.SerializerMethodField()
    avatar_url = serializers.SerializerMethodField()

    class Meta:
        model = PostComment
        fields = ['id', 'user_id', 'username', 'display_name', 'avatar_url', 'text', 'created_at']

    def get_display_name(self, obj):
        try:
            return obj.user.rider_profile.display_name or obj.user.username
        except Exception:
            return obj.user.username

    def get_avatar_url(self, obj):
        try:
            return obj.user.rider_profile.avatar_url
        except Exception:
            return ''

class PostSerializer(serializers.ModelSerializer):
    author_id = serializers.IntegerField(source='author.id', read_only=True)
    author_username = serializers.CharField(source='author.username', read_only=True)
    author_display_name = serializers.SerializerMethodField()
    author_avatar_url = serializers.SerializerMethodField()
    author_city = serializers.SerializerMethodField()
    ride_telemetry = serializers.SerializerMethodField()
    likes_count = serializers.SerializerMethodField()
    comments_count = serializers.SerializerMethodField()
    is_liked = serializers.SerializerMethodField()
    is_following_author = serializers.SerializerMethodField()

    class Meta:
        model = Post
        fields = [
            'id', 'author_id', 'author_username', 'author_display_name', 'author_avatar_url', 'author_city',
            'ride', 'post_type', 'caption', 'image_url', 'created_at',
            'ride_telemetry', 'likes_count', 'comments_count', 'is_liked', 'is_following_author'
        ]

    def get_author_display_name(self, obj):
        try:
            return obj.author.rider_profile.display_name or obj.author.username
        except Exception:
            return obj.author.username

    def get_author_avatar_url(self, obj):
        try:
            return obj.author.rider_profile.avatar_url
        except Exception:
            return ''

    def get_author_city(self, obj):
        try:
            return obj.author.rider_profile.city
        except Exception:
            return ''

    def get_ride_telemetry(self, obj):
        if not obj.ride:
            return None
        ride = obj.ride
        bike_name = ride.motorcycle.displayName if ride.motorcycle else 'Motorcycle'
        return {
            'id': str(ride.id),
            'title': ride.title,
            'total_distance_km': round(ride.total_distance_meters / 1000.0, 2),
            'moving_time_seconds': ride.moving_time_seconds,
            'avg_speed_kmh': round(ride.avg_speed_kmh, 1),
            'motorcycle_name': bike_name
        }

    def get_likes_count(self, obj):
        return obj.likes.count()

    def get_comments_count(self, obj):
        return obj.comments.count()

    def get_is_liked(self, obj):
        request = self.context.get('request')
        if request and request.user.is_authenticated:
            return obj.likes.filter(user=request.user).exists()
        return False

    def get_is_following_author(self, obj):
        request = self.context.get('request')
        if request and request.user.is_authenticated:
            if request.user == obj.author:
                return False
            return RiderFollow.objects.filter(follower=request.user, following=obj.author).exists()
        return False

class PostCreateSerializer(serializers.ModelSerializer):
    class Meta:
        model = Post
        fields = ['ride', 'post_type', 'caption', 'image_url']

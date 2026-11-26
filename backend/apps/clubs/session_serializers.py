"""
Serializers for ClubRideSession and ClubRideParticipant.
Completely separate from the existing RidingEvent/EventRsvp serializers.
"""
from rest_framework import serializers
from .models import ClubRideSession, ClubRideParticipant, ParticipantStatus


class ParticipantSerializer(serializers.ModelSerializer):
    """
    Read-only participant row.  Exposed to LEADERs (full detail) and
    to the participant themselves (own row only via my_status on session).
    """
    username = serializers.CharField(source='user.username', read_only=True)
    display_name = serializers.SerializerMethodField()
    avatar_url = serializers.SerializerMethodField()
    individual_ride_id = serializers.SerializerMethodField()

    class Meta:
        model = ClubRideParticipant
        fields = [
            'id', 'user_id', 'username', 'display_name', 'avatar_url',
            'status', 'joined_at', 'ready_at', 'verified_at',
            'individual_ride_id',
        ]
        read_only_fields = fields

    def get_display_name(self, obj):
        try:
            return obj.user.rider_profile.display_name or obj.user.username
        except Exception:
            return obj.user.username

    def get_avatar_url(self, obj):
        try:
            return obj.user.rider_profile.avatar_url or ''
        except Exception:
            return ''

    def get_individual_ride_id(self, obj):
        return str(obj.individual_ride_id) if obj.individual_ride_id else None


class ClubRideSessionListSerializer(serializers.ModelSerializer):
    """
    Compact session representation for list views.
    """
    participant_count = serializers.SerializerMethodField()
    my_status = serializers.SerializerMethodField()
    my_individual_ride_id = serializers.SerializerMethodField()
    created_by_username = serializers.CharField(source='created_by.username', read_only=True)
    is_expired = serializers.SerializerMethodField()

    class Meta:
        model = ClubRideSession
        fields = [
            'id', 'club_id', 'title', 'description', 'status',
            'meeting_point', 'destination',
            'start_lat', 'start_lng', 'dest_lat', 'dest_lng',
            'encoded_polyline', 'route_distance_meters', 'route_duration_seconds', 'route_summary',
            'scheduled_start', 'expires_at',
            'actual_start', 'actual_end',
            'max_participants', 'participant_count',
            'my_status', 'my_individual_ride_id', 'is_expired',
            'created_by_username', 'created_at',
        ]
        read_only_fields = fields

    def get_participant_count(self, obj):
        return obj.participants.exclude(
            status__in=[ParticipantStatus.REMOVED, ParticipantStatus.NOT_ATTENDING]
        ).count()

    def get_my_status(self, obj):
        request = self.context.get('request')
        if request and request.user.is_authenticated:
            p = obj.participants.filter(user=request.user).first()
            return p.status if p else None
        return None

    def get_my_individual_ride_id(self, obj):
        request = self.context.get('request')
        if request and request.user.is_authenticated:
            p = obj.participants.filter(user=request.user).first()
            if p and p.individual_ride_id:
                return str(p.individual_ride_id)
        return None

    def get_is_expired(self, obj):
        return obj.is_expired_now


class ClubRideSessionDetailSerializer(ClubRideSessionListSerializer):
    """
    Full session representation including waypoints and participant list.
    Participant list is only shown to LEADERs (filtered in the view).
    """
    waypoints = serializers.SerializerMethodField()
    participants = serializers.SerializerMethodField()

    class Meta(ClubRideSessionListSerializer.Meta):
        fields = ClubRideSessionListSerializer.Meta.fields + ['waypoints', 'participants']
        read_only_fields = fields

    def get_waypoints(self, obj):
        return obj.waypoints  # property that JSON-decodes waypoints_json

    def get_participants(self, obj):
        """
        Full participant list — only sent when the requesting user is LEADER.
        Otherwise returns None so the field is omitted cleanly.
        """
        request = self.context.get('request')
        if not request or not request.user.is_authenticated:
            return None
        is_leader = obj.club.members.filter(user=request.user, role='LEADER').exists()
        if not is_leader:
            return None
        participants = obj.participants.select_related('user', 'user__rider_profile').all()
        return ParticipantSerializer(participants, many=True, context=self.context).data


class ClubRideSessionCreateSerializer(serializers.ModelSerializer):
    """
    Write-only serializer for creating a new scheduled session.
    Accepts waypoints as a list of dicts.
    """
    waypoints = serializers.ListField(
        child=serializers.DictField(),
        required=False,
        default=list,
        write_only=True
    )

    class Meta:
        model = ClubRideSession
        fields = [
            'title', 'description', 'meeting_point', 'destination',
            'start_lat', 'start_lng', 'dest_lat', 'dest_lng',
            'encoded_polyline', 'route_distance_meters', 'route_duration_seconds', 'route_summary',
            'scheduled_start', 'max_participants', 'waypoints',
        ]

    def _validate_coord(self, val, min_v, max_v, name):
        if val is None:
            return val
        if not (min_v <= val <= max_v):
            raise serializers.ValidationError(f"{name} must be between {min_v} and {max_v}.")
        return val

    def validate_start_lat(self, value):
        return self._validate_coord(value, -90.0, 90.0, "start_lat")

    def validate_start_lng(self, value):
        return self._validate_coord(value, -180.0, 180.0, "start_lng")

    def validate_dest_lat(self, value):
        return self._validate_coord(value, -90.0, 90.0, "dest_lat")

    def validate_dest_lng(self, value):
        return self._validate_coord(value, -180.0, 180.0, "dest_lng")

    def validate_scheduled_start(self, value):
        from django.utils import timezone
        if value <= timezone.now():
            raise serializers.ValidationError(
                "Scheduled start must be in the future."
            )
        return value

    def validate_max_participants(self, value):
        if value < 2:
            raise serializers.ValidationError(
                "A club ride must allow at least 2 participants."
            )
        if value > 200:
            raise serializers.ValidationError(
                "Max participants cannot exceed 200."
            )
        return value

    def validate_waypoints(self, value):
        if len(value) > 20:
            raise serializers.ValidationError("Maximum 20 pit stops allowed.")
        for wp in value:
            if 'lat' not in wp or 'lng' not in wp:
                raise serializers.ValidationError(
                    "Each pit stop must have 'lat' and 'lng' keys."
                )
            try:
                lat = float(wp['lat'])
                lng = float(wp['lng'])
            except (TypeError, ValueError):
                raise serializers.ValidationError(
                    "Pit stop 'lat' and 'lng' must be numeric."
                )
            if not (-90.0 <= lat <= 90.0) or not (-180.0 <= lng <= 180.0):
                raise serializers.ValidationError(
                    "Pit stop coordinates are out of valid bounds."
                )
        return value

    def create(self, validated_data):
        import json
        waypoints = validated_data.pop('waypoints', [])
        session = ClubRideSession(**validated_data)
        session.waypoints_json = json.dumps(waypoints)
        # expires_at is set automatically by ClubRideSession.save()
        session.save()
        return session

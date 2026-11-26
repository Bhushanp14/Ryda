import uuid
from django.utils.text import slugify
from rest_framework import serializers
from .models import Club, ClubMember, RidingEvent, EventRsvp

class EventRsvpSerializer(serializers.ModelSerializer):
    username = serializers.CharField(source='user.username', read_only=True)
    display_name = serializers.SerializerMethodField()
    avatar_url = serializers.SerializerMethodField()

    class Meta:
        model = EventRsvp
        fields = ['id', 'user_id', 'username', 'display_name', 'avatar_url', 'status', 'created_at']

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

class RidingEventSerializer(serializers.ModelSerializer):
    club_id = serializers.UUIDField(source='club.id', read_only=True)
    club_name = serializers.CharField(source='club.name', read_only=True)
    confirmed_count = serializers.SerializerMethodField()
    interested_count = serializers.SerializerMethodField()
    my_rsvp = serializers.SerializerMethodField()

    class Meta:
        model = RidingEvent
        fields = [
            'id', 'club_id', 'club_name', 'title', 'description', 'meeting_point', 'destination',
            'start_time', 'rider_limit', 'pace', 'distance_km', 'is_official',
            'confirmed_count', 'interested_count', 'my_rsvp'
        ]

    def get_confirmed_count(self, obj):
        return obj.rsvps.filter(status='GOING').count()

    def get_interested_count(self, obj):
        return obj.rsvps.filter(status='INTERESTED').count()

    def get_my_rsvp(self, obj):
        request = self.context.get('request')
        if request and request.user.is_authenticated:
            rsvp = obj.rsvps.filter(user=request.user).first()
            return rsvp.status if rsvp else None
        return None

class ClubMemberSerializer(serializers.ModelSerializer):
    username = serializers.CharField(source='user.username', read_only=True)
    display_name = serializers.SerializerMethodField()
    avatar_url = serializers.SerializerMethodField()
    city = serializers.SerializerMethodField()

    class Meta:
        model = ClubMember
        fields = ['id', 'user_id', 'username', 'display_name', 'avatar_url', 'city', 'role', 'joined_at']

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

    def get_city(self, obj):
        try:
            return obj.user.rider_profile.city
        except Exception:
            return ''

class ClubSerializer(serializers.ModelSerializer):
    members_count = serializers.SerializerMethodField()
    is_member = serializers.SerializerMethodField()
    my_role = serializers.SerializerMethodField()
    upcoming_events = serializers.SerializerMethodField()
    slug = serializers.SlugField(required=False)

    class Meta:
        model = Club
        fields = [
            'id', 'name', 'slug', 'description', 'city', 'banner_url', 'logo_url',
            'is_verified', 'policy', 'total_mileage_km', 'members_count', 'is_member',
            'my_role', 'upcoming_events'
        ]
        extra_kwargs = {
            'id': {'read_only': False, 'required': False},
            'slug': {'required': False},
            'description': {'required': False, 'allow_blank': True},
            'city': {'required': False},
            'banner_url': {'required': False, 'allow_blank': True},
            'logo_url': {'required': False, 'allow_blank': True},
        }

    def validate(self, attrs):
        name = attrs.get('name', '')
        slug = attrs.get('slug')
        if not slug and name:
            slug = slugify(name)
        if not slug:
            slug = f"club-{uuid.uuid4().hex[:8]}"

        # Guarantee unique slug
        base_slug = slug
        instance_id = self.instance.id if self.instance else None
        count = 1
        while Club.objects.filter(slug=slug).exclude(id=instance_id).exists():
            slug = f"{base_slug}-{uuid.uuid4().hex[:6]}"
            count += 1
            if count > 10:
                slug = f"{base_slug}-{uuid.uuid4().hex}"
                break
        attrs['slug'] = slug

        if 'id' in attrs:
            try:
                uuid.UUID(str(attrs['id']))
            except (ValueError, TypeError):
                attrs.pop('id', None)

        return attrs

    def get_members_count(self, obj):
        return obj.members.count()

    def get_is_member(self, obj):
        request = self.context.get('request')
        if request and request.user.is_authenticated:
            return obj.members.filter(user=request.user).exists()
        return False

    def get_my_role(self, obj):
        request = self.context.get('request')
        if request and request.user.is_authenticated:
            membership = obj.members.filter(user=request.user).first()
            return membership.role if membership else None
        return None

    def get_upcoming_events(self, obj):
        events = obj.events.all()[:3]
        return RidingEventSerializer(events, many=True, context=self.context).data

import uuid
from django.db import models
from django.contrib.auth.models import User

class ClubPolicy(models.TextChoices):
    OPEN = 'OPEN', 'Open'
    INVITE_ONLY = 'INVITE_ONLY', 'Invite Only'

class ClubRole(models.TextChoices):
    LEADER = 'LEADER', 'Leader'
    MEMBER = 'MEMBER', 'Member'

class RsvpStatus(models.TextChoices):
    GOING = 'GOING', 'Going'
    INTERESTED = 'INTERESTED', 'Interested'
    DECLINED = 'DECLINED', 'Declined'

class Club(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    name = models.CharField(max_length=120)
    slug = models.SlugField(max_length=140, unique=True)
    description = models.TextField(blank=True, default='')
    city = models.CharField(max_length=100, default='Mumbai')
    banner_url = models.URLField(max_length=500, blank=True, default='')
    logo_url = models.URLField(max_length=500, blank=True, default='')
    is_verified = models.BooleanField(default=True)
    policy = models.CharField(max_length=20, choices=ClubPolicy.choices, default=ClubPolicy.OPEN)
    total_mileage_km = models.FloatField(default=0.0)
    created_by = models.ForeignKey(User, on_delete=models.SET_NULL, null=True, related_name='created_clubs')
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        app_label = 'clubs'
        ordering = ['-total_mileage_km', 'name']

    def __str__(self):
        return self.name

class ClubMember(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    club = models.ForeignKey(Club, on_delete=models.CASCADE, related_name='members')
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='club_memberships')
    role = models.CharField(max_length=20, choices=ClubRole.choices, default=ClubRole.MEMBER)
    joined_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        app_label = 'clubs'
        unique_together = ('club', 'user')

class RidingEvent(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    club = models.ForeignKey(Club, on_delete=models.CASCADE, related_name='events')
    title = models.CharField(max_length=200)
    description = models.TextField(blank=True, default='')
    meeting_point = models.CharField(max_length=255)
    destination = models.CharField(max_length=255)
    start_time = models.DateTimeField()
    rider_limit = models.PositiveIntegerField(default=35)
    pace = models.CharField(max_length=50, default='Intermediate')
    distance_km = models.FloatField(default=150.0)
    is_official = models.BooleanField(default=True)
    created_by = models.ForeignKey(User, on_delete=models.SET_NULL, null=True, related_name='created_events')
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        app_label = 'clubs'
        ordering = ['start_time']

    def __str__(self):
        return f"{self.title} ({self.club.name})"

class EventRsvp(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    event = models.ForeignKey(RidingEvent, on_delete=models.CASCADE, related_name='rsvps')
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='event_rsvps')
    status = models.CharField(max_length=20, choices=RsvpStatus.choices, default=RsvpStatus.GOING)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        app_label = 'clubs'
        unique_together = ('event', 'user')

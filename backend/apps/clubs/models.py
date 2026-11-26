import json
import uuid
from datetime import timedelta
from django.db import models
from django.contrib.auth.models import User
from django.utils import timezone


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


# ── NEW: Club Ride Session ──────────────────────────────────────────────────────

class SessionStatus(models.TextChoices):
    SCHEDULED  = 'SCHEDULED',  'Scheduled'
    ACTIVE     = 'ACTIVE',     'Active'
    COMPLETED  = 'COMPLETED',  'Completed'
    EXPIRED    = 'EXPIRED',    'Expired'
    CANCELLED  = 'CANCELLED',  'Cancelled'


class ParticipantStatus(models.TextChoices):
    JOINED        = 'JOINED',        'Joined'
    READY         = 'READY',         'Ready'
    VERIFIED      = 'VERIFIED',      'Verified'
    NOT_ATTENDING = 'NOT_ATTENDING', 'Not Attending'
    REMOVED       = 'REMOVED',       'Removed'


# Valid state transitions enforced by the backend.
# Key = current status, Value = set of statuses this may transition TO.
SESSION_VALID_TRANSITIONS = {
    SessionStatus.SCHEDULED: {SessionStatus.ACTIVE, SessionStatus.CANCELLED, SessionStatus.EXPIRED},
    SessionStatus.ACTIVE:    {SessionStatus.COMPLETED, SessionStatus.CANCELLED},
    SessionStatus.COMPLETED: set(),
    SessionStatus.EXPIRED:   set(),
    SessionStatus.CANCELLED: set(),
}

PARTICIPANT_VALID_TRANSITIONS = {
    ParticipantStatus.JOINED:        {ParticipantStatus.READY, ParticipantStatus.NOT_ATTENDING, ParticipantStatus.REMOVED},
    ParticipantStatus.READY:         {ParticipantStatus.VERIFIED, ParticipantStatus.NOT_ATTENDING},
    ParticipantStatus.VERIFIED:      set(),   # terminal once session starts
    ParticipantStatus.NOT_ATTENDING: set(),
    ParticipantStatus.REMOVED:       set(),
}


class ClubRideSession(models.Model):
    """
    A scheduled Club Ride Session.  Lifecycle: SCHEDULED → ACTIVE → COMPLETED.
    Alternative endings: EXPIRED (12 h after scheduled_start, never started)
                         CANCELLED (Leader aborted before or during session).

    Route/waypoints are stored as JSON text to avoid PostGIS dependency in dev.
    """
    id               = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    club             = models.ForeignKey('Club', on_delete=models.CASCADE, related_name='club_rides')
    title            = models.CharField(max_length=200)
    description      = models.TextField(blank=True, default='')
    status           = models.CharField(
        max_length=20, choices=SessionStatus.choices, default=SessionStatus.SCHEDULED, db_index=True
    )
    meeting_point    = models.CharField(max_length=255)
    destination      = models.CharField(max_length=255)
    start_lat        = models.FloatField(null=True, blank=True)
    start_lng        = models.FloatField(null=True, blank=True)
    dest_lat         = models.FloatField(null=True, blank=True)
    dest_lng         = models.FloatField(null=True, blank=True)
    # Ordered pit stops JSON: list of {"lat": float, "lng": float, "label": str}
    waypoints_json   = models.TextField(blank=True, default='[]')
    encoded_polyline = models.TextField(blank=True, default='')
    route_distance_meters  = models.PositiveIntegerField(default=0)
    route_duration_seconds = models.PositiveIntegerField(default=0)
    route_summary          = models.CharField(max_length=255, blank=True, default='')
    max_participants = models.PositiveIntegerField(default=50)
    scheduled_start  = models.DateTimeField()
    # Always set to scheduled_start + 12 h on save.
    expires_at       = models.DateTimeField()
    actual_start     = models.DateTimeField(null=True, blank=True)
    actual_end       = models.DateTimeField(null=True, blank=True)
    created_by       = models.ForeignKey(
        User, on_delete=models.SET_NULL, null=True, related_name='created_club_rides'
    )
    started_by       = models.ForeignKey(
        User, on_delete=models.SET_NULL, null=True, blank=True, related_name='started_club_rides'
    )
    created_at       = models.DateTimeField(auto_now_add=True)
    updated_at       = models.DateTimeField(auto_now=True)

    class Meta:
        app_label = 'clubs'
        ordering = ['scheduled_start']
        indexes = [
            models.Index(fields=['club', 'status']),
            models.Index(fields=['status', 'expires_at']),
        ]

    def save(self, *args, **kwargs):
        # Always derive expires_at from scheduled_start (12-hour rule).
        self.expires_at = self.scheduled_start + timedelta(hours=12)
        super().save(*args, **kwargs)

    def transition_to(self, new_status, actor=None):
        """
        Enforce valid state transitions.  Raises ValueError on illegal move.
        Updates started_by / actual_start / actual_end where appropriate.
        Does NOT call save() — caller is responsible.
        """
        current = self.status
        allowed = SESSION_VALID_TRANSITIONS.get(current, set())
        if new_status not in allowed:
            raise ValueError(
                f"Cannot transition ClubRideSession from {current} to {new_status}."
            )
        self.status = new_status
        if new_status == SessionStatus.ACTIVE:
            self.actual_start = timezone.now()
            if actor:
                self.started_by = actor
        elif new_status in (SessionStatus.COMPLETED, SessionStatus.CANCELLED, SessionStatus.EXPIRED):
            self.actual_end = timezone.now()

    @property
    def waypoints(self):
        try:
            return json.loads(self.waypoints_json)
        except (ValueError, TypeError):
            return []

    @waypoints.setter
    def waypoints(self, value):
        self.waypoints_json = json.dumps(value)

    @property
    def is_joinable(self):
        return self.status == SessionStatus.SCHEDULED and not self.is_expired_now

    @property
    def is_expired_now(self):
        return timezone.now() >= self.expires_at

    @property
    def verified_participant_count(self):
        return self.participants.filter(status=ParticipantStatus.VERIFIED).count()

    def __str__(self):
        return f"{self.title} [{self.club.name}] — {self.status}"


class ClubRideParticipant(models.Model):
    """
    Tracks a rider's membership in a ClubRideSession and their readiness/verification state.

    State machine (see PARTICIPANT_VALID_TRANSITIONS above):
      JOINED → READY         (member taps "I'm Ready")
      READY  → VERIFIED      (LEADER verifies physical attendance)
      READY  → NOT_ATTENDING (LEADER marks absent)
      JOINED → NOT_ATTENDING (LEADER marks absent without ready step)
      JOINED → REMOVED       (LEADER removes before session start)

    individual_ride is set by the backend when the session starts (ACTIVE transition)
    for each VERIFIED participant.  It links to the auto-created solo Ride record.
    """
    id              = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    session         = models.ForeignKey(
        ClubRideSession, on_delete=models.CASCADE, related_name='participants'
    )
    user            = models.ForeignKey(
        User, on_delete=models.CASCADE, related_name='club_ride_participations'
    )
    status          = models.CharField(
        max_length=20, choices=ParticipantStatus.choices, default=ParticipantStatus.JOINED, db_index=True
    )
    joined_at       = models.DateTimeField(auto_now_add=True)
    ready_at        = models.DateTimeField(null=True, blank=True)
    verified_at     = models.DateTimeField(null=True, blank=True)
    verified_by     = models.ForeignKey(
        User, on_delete=models.SET_NULL, null=True, blank=True,
        related_name='verifications_given'
    )
    # Set only when session transitions to ACTIVE and participant is VERIFIED.
    individual_ride = models.OneToOneField(
        'rides.Ride', on_delete=models.SET_NULL, null=True, blank=True,
        related_name='club_participation'
    )

    class Meta:
        app_label = 'clubs'
        unique_together = ('session', 'user')
        indexes = [
            models.Index(fields=['session', 'status']),
        ]

    def transition_to(self, new_status, actor=None):
        """
        Enforce participant state transitions.  Raises ValueError on illegal move.
        Updates ready_at / verified_at timestamps. Does NOT call save().
        """
        current = self.status
        allowed = PARTICIPANT_VALID_TRANSITIONS.get(current, set())
        if new_status not in allowed:
            raise ValueError(
                f"Cannot transition participant from {current} to {new_status}."
            )
        self.status = new_status
        now = timezone.now()
        if new_status == ParticipantStatus.READY:
            self.ready_at = now
        elif new_status == ParticipantStatus.VERIFIED:
            self.verified_at = now
            if actor:
                self.verified_by = actor

    def __str__(self):
        return f"{self.user.username} in {self.session.title} — {self.status}"


# ── Existing models (unchanged) ─────────────────────────────────────────────────

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


class SosStatus(models.TextChoices):
    ACTIVE = 'ACTIVE', 'Active'
    ACKNOWLEDGED = 'ACKNOWLEDGED', 'Acknowledged'
    RESOLVED = 'RESOLVED', 'Resolved'
    CANCELLED = 'CANCELLED', 'Cancelled'


class ClubRideSosEvent(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    session = models.ForeignKey(ClubRideSession, on_delete=models.CASCADE, related_name='sos_events')
    rider = models.ForeignKey(User, on_delete=models.CASCADE, related_name='sos_events_raised')
    latitude = models.FloatField()
    longitude = models.FloatField()
    accuracy = models.FloatField(default=0.0)
    timestamp = models.DateTimeField(default=timezone.now)
    status = models.CharField(max_length=20, choices=SosStatus.choices, default=SosStatus.ACTIVE)
    acknowledged_by = models.ManyToManyField(User, blank=True, related_name='sos_events_acknowledged')
    resolved_by = models.ForeignKey(User, on_delete=models.SET_NULL, null=True, blank=True, related_name='sos_events_resolved')
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        app_label = 'clubs'
        ordering = ['-timestamp']

    def __str__(self):
        return f"SOS by {self.rider.username} in {self.session.title} [{self.status}]"

"""
Views for the ClubRideSession lifecycle.
Endpoints are mounted under /api/v1/clubs/<club_id>/rides/ and
                              /api/v1/clubs/<club_id>/rides/<session_id>/...

Permission rules (enforced here, not at model level):
  - View sessions:                Any authenticated user (member or not)
  - Create session:               LEADER of the club only
  - Join / leave / mark ready:    Authenticated club MEMBER (or LEADER)
  - Verify / absent participant:  LEADER only
  - Start / complete / cancel:    LEADER only

Locking strategy:
  - All session-state mutations use select_for_update() to prevent race conditions.
  - Duplicate participant creation is prevented by unique_together (session, user).
  - Duplicate individual ride creation is prevented by OneToOneField on ClubRideParticipant.
"""
import json

from django.db import transaction
from django.shortcuts import get_object_or_404
from django.utils import timezone
from rest_framework import permissions, status, views
from rest_framework.response import Response

from apps.notifications.models import Notification, NotificationType
from apps.rides.models import Ride, RideStatus
from .management.commands.expire_club_rides import expire_overdue_sessions
from .models import (
    Club, ClubMember, ClubRideParticipant, ClubRideSession,
    ParticipantStatus, SessionStatus,
)
from .session_serializers import (
    ClubRideSessionCreateSerializer,
    ClubRideSessionDetailSerializer,
    ClubRideSessionListSerializer,
    ParticipantSerializer,
)


# ── Helpers ────────────────────────────────────────────────────────────────────

def _get_club_or_404(club_id):
    return get_object_or_404(Club, id=club_id)


def _get_membership(club, user):
    """Return ClubMember or None."""
    return ClubMember.objects.filter(club=club, user=user).first()


def _require_leader(club, user):
    """
    Raises PermissionError string if user is not LEADER.
    Returns the ClubMember instance on success.
    """
    membership = _get_membership(club, user)
    if not membership or membership.role != 'LEADER':
        raise PermissionError("Only club leaders can perform this action.")
    return membership


def _require_member(club, user):
    """Raises PermissionError if user is not a member (any role)."""
    membership = _get_membership(club, user)
    if not membership:
        raise PermissionError("You must be a club member to participate in club rides.")
    return membership


def _get_session_or_404(club, session_id):
    return get_object_or_404(ClubRideSession, id=session_id, club=club)


def _broadcast_session_event(session_id, event_type):
    """Best-effort WebSocket group broadcast for session lifecycle events."""
    try:
        from asgiref.sync import async_to_sync
        from channels.layers import get_channel_layer
        channel_layer = get_channel_layer()
        if channel_layer is not None:
            async_to_sync(channel_layer.group_send)(
                f"club_ride_{session_id}",
                {
                    'type': 'broadcast_message',
                    'payload': {
                        'type': event_type,
                        'session_id': str(session_id),
                        'timestamp': timezone.now().isoformat(),
                    },
                },
            )
    except Exception:
        pass


def _notify_all_verified(session, title, message):
    """Create in-app notifications for all VERIFIED participants."""
    verified = ClubRideParticipant.objects.filter(
        session=session, status=ParticipantStatus.VERIFIED
    ).select_related('user')
    notifications = [
        Notification(
            user=p.user,
            notification_type=NotificationType.CLUB_RIDE,
            title=title,
            message=message,
            related_id=str(session.id),
        )
        for p in verified
    ]
    if notifications:
        Notification.objects.bulk_create(notifications)


# ── Views ──────────────────────────────────────────────────────────────────────

class ClubRideSessionListCreateView(views.APIView):
    """
    GET  /api/v1/clubs/<club_id>/rides/
         Lists sessions for the club.  Optional ?status= filter.
         Non-members see only SCHEDULED sessions.
         Members + LEADERs see all statuses.

    POST /api/v1/clubs/<club_id>/rides/
         Create a new SCHEDULED session.  LEADER only.
    """
    permission_classes = [permissions.IsAuthenticated]

    def get(self, request, club_id):
        club = _get_club_or_404(club_id)
        expire_overdue_sessions()
        qs = ClubRideSession.objects.filter(club=club)

        section = (request.query_params.get('section') or '').strip().lower()
        status_filter = request.query_params.get('status')

        if section == 'upcoming':
            qs = qs.filter(status__in=[SessionStatus.SCHEDULED, SessionStatus.ACTIVE]).order_by('scheduled_start')
        elif section == 'previous':
            qs = qs.filter(status__in=[SessionStatus.COMPLETED, SessionStatus.EXPIRED, SessionStatus.CANCELLED]).order_by('-scheduled_start')
        elif status_filter:
            upper_status = status_filter.upper()
            qs = qs.filter(status=upper_status)
            if upper_status in (SessionStatus.COMPLETED, SessionStatus.EXPIRED, SessionStatus.CANCELLED):
                qs = qs.order_by('-scheduled_start')
            else:
                qs = qs.order_by('scheduled_start')
        else:
            qs = qs.order_by('scheduled_start')

        serializer = ClubRideSessionListSerializer(qs, many=True, context={'request': request})
        return Response(serializer.data, status=status.HTTP_200_OK)

    def post(self, request, club_id):
        club = _get_club_or_404(club_id)
        try:
            _require_leader(club, request.user)
        except PermissionError as e:
            return Response({'detail': str(e)}, status=status.HTTP_403_FORBIDDEN)

        serializer = ClubRideSessionCreateSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        session = serializer.save(club=club, created_by=request.user)
        out = ClubRideSessionDetailSerializer(session, context={'request': request})
        return Response(out.data, status=status.HTTP_201_CREATED)


class ClubRideSessionDetailView(views.APIView):
    """
    GET   /api/v1/clubs/<club_id>/rides/<session_id>/
          Full session detail.  LEADER gets full participant list.
          Member gets own status only.

    PATCH /api/v1/clubs/<club_id>/rides/<session_id>/
          Update a SCHEDULED session.  LEADER only.
    """
    permission_classes = [permissions.IsAuthenticated]

    def get(self, request, club_id, session_id):
        club = _get_club_or_404(club_id)
        expire_overdue_sessions()
        session = _get_session_or_404(club, session_id)
        serializer = ClubRideSessionDetailSerializer(session, context={'request': request})
        return Response(serializer.data, status=status.HTTP_200_OK)

    def patch(self, request, club_id, session_id):
        club = _get_club_or_404(club_id)
        try:
            _require_leader(club, request.user)
        except PermissionError as e:
            return Response({'detail': str(e)}, status=status.HTTP_403_FORBIDDEN)

        with transaction.atomic():
            session = get_object_or_404(
                ClubRideSession.objects.select_for_update(), id=session_id, club=club
            )
            if session.status != SessionStatus.SCHEDULED:
                return Response(
                    {'detail': f"Only SCHEDULED sessions can be edited (current: {session.status})."},
                    status=status.HTTP_409_CONFLICT,
                )
            serializer = ClubRideSessionCreateSerializer(
                session, data=request.data, partial=True
            )
            if not serializer.is_valid():
                return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

            waypoints = serializer.validated_data.pop('waypoints', None)
            for attr, val in serializer.validated_data.items():
                setattr(session, attr, val)
            if waypoints is not None:
                session.waypoints_json = json.dumps(waypoints)
            session.save()

        out = ClubRideSessionDetailSerializer(session, context={'request': request})
        return Response(out.data, status=status.HTTP_200_OK)


class ClubRideJoinView(views.APIView):
    """
    POST /api/v1/clubs/<club_id>/rides/<session_id>/join/
         Join a SCHEDULED session.  Must be a club member.
         Idempotent — joining twice returns current status.
    """
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request, club_id, session_id):
        club = _get_club_or_404(club_id)
        try:
            _require_member(club, request.user)
        except PermissionError as e:
            return Response({'detail': str(e)}, status=status.HTTP_403_FORBIDDEN)

        with transaction.atomic():
            session = get_object_or_404(
                ClubRideSession.objects.select_for_update(), id=session_id, club=club
            )

            if session.status != SessionStatus.SCHEDULED:
                return Response(
                    {'detail': f"Cannot join a session with status '{session.status}'. Only SCHEDULED sessions can be joined."},
                    status=status.HTTP_409_CONFLICT,
                )
            if session.is_expired_now:
                return Response(
                    {'detail': "This session has expired and can no longer be joined."},
                    status=status.HTTP_410_GONE,
                )

            active_count = session.participants.exclude(
                status__in=[ParticipantStatus.REMOVED, ParticipantStatus.NOT_ATTENDING]
            ).count()
            if active_count >= session.max_participants:
                return Response(
                    {'detail': "This session has reached its participant limit."},
                    status=status.HTTP_409_CONFLICT,
                )

            participant, created = ClubRideParticipant.objects.get_or_create(
                session=session,
                user=request.user,
                defaults={'status': ParticipantStatus.JOINED},
            )

            if not created and participant.status in (
                ParticipantStatus.REMOVED, ParticipantStatus.NOT_ATTENDING
            ):
                return Response(
                    {'detail': f"You have been marked as '{participant.status}' and cannot re-join."},
                    status=status.HTTP_403_FORBIDDEN,
                )

        return Response({
            'session_id': str(session.id),
            'my_status': participant.status,
            'participant_count': session.participants.exclude(
                status__in=[ParticipantStatus.REMOVED, ParticipantStatus.NOT_ATTENDING]
            ).count(),
        }, status=status.HTTP_200_OK)


class ClubRideLeaveView(views.APIView):
    """
    POST /api/v1/clubs/<club_id>/rides/<session_id>/leave/
         Leave a SCHEDULED session.  Only JOINED or READY participants can leave.
         VERIFIED participants cannot leave — the roster is locked once verified.
    """
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request, club_id, session_id):
        club = _get_club_or_404(club_id)
        with transaction.atomic():
            session = get_object_or_404(
                ClubRideSession.objects.select_for_update(), id=session_id, club=club
            )
            if session.status != SessionStatus.SCHEDULED:
                return Response(
                    {'detail': "You can only leave a SCHEDULED session."},
                    status=status.HTTP_409_CONFLICT,
                )
            participant = ClubRideParticipant.objects.filter(
                session=session, user=request.user
            ).first()
            if not participant:
                return Response(
                    {'detail': "You are not registered for this session."},
                    status=status.HTTP_404_NOT_FOUND,
                )
            if participant.status == ParticipantStatus.VERIFIED:
                return Response(
                    {'detail': "Your attendance has been verified. You cannot leave once verified."},
                    status=status.HTTP_403_FORBIDDEN,
                )
            participant.delete()

        return Response({
            'session_id': str(session.id),
            'my_status': None,
            'participant_count': session.participants.exclude(
                status__in=[ParticipantStatus.REMOVED, ParticipantStatus.NOT_ATTENDING]
            ).count(),
        }, status=status.HTTP_200_OK)


class ClubRideReadyView(views.APIView):
    """
    POST /api/v1/clubs/<club_id>/rides/<session_id>/ready/
         Member taps "I'm Ready" at the meeting point.
         Only JOINED participants may become READY.
         Session must be SCHEDULED.
    """
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request, club_id, session_id):
        club = _get_club_or_404(club_id)
        with transaction.atomic():
            session = get_object_or_404(
                ClubRideSession.objects.select_for_update(), id=session_id, club=club
            )
            if session.status != SessionStatus.SCHEDULED:
                return Response(
                    {'detail': "Only SCHEDULED sessions accept readiness updates."},
                    status=status.HTTP_409_CONFLICT,
                )
            participant = ClubRideParticipant.objects.filter(
                session=session, user=request.user
            ).first()
            if not participant:
                return Response(
                    {'detail': "You have not joined this session."},
                    status=status.HTTP_404_NOT_FOUND,
                )
            try:
                participant.transition_to(ParticipantStatus.READY)
                participant.save()
            except ValueError as e:
                return Response({'detail': str(e)}, status=status.HTTP_409_CONFLICT)

        return Response({
            'my_status': participant.status,
            'ready_at': participant.ready_at,
        }, status=status.HTTP_200_OK)


class ClubRideVerifyParticipantView(views.APIView):
    """
    POST /api/v1/clubs/<club_id>/rides/<session_id>/verify/<user_id>/
         LEADER verifies physical attendance.  Participant must be READY.
         Session must be SCHEDULED.
    """
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request, club_id, session_id, user_id):
        club = _get_club_or_404(club_id)
        try:
            _require_leader(club, request.user)
        except PermissionError as e:
            return Response({'detail': str(e)}, status=status.HTTP_403_FORBIDDEN)

        with transaction.atomic():
            session = get_object_or_404(
                ClubRideSession.objects.select_for_update(), id=session_id, club=club
            )
            if session.status != SessionStatus.SCHEDULED:
                return Response(
                    {'detail': "Participant verification is only available for SCHEDULED sessions."},
                    status=status.HTTP_409_CONFLICT,
                )
            participant = get_object_or_404(
                ClubRideParticipant, session=session, user_id=user_id
            )
            try:
                participant.transition_to(ParticipantStatus.VERIFIED, actor=request.user)
                participant.save()
            except ValueError as e:
                return Response({'detail': str(e)}, status=status.HTTP_409_CONFLICT)

        return Response({
            'user_id': str(user_id),
            'username': participant.user.username,
            'status': participant.status,
            'verified_at': participant.verified_at,
        }, status=status.HTTP_200_OK)


class ClubRideAbsentParticipantView(views.APIView):
    """
    POST /api/v1/clubs/<club_id>/rides/<session_id>/absent/<user_id>/
         LEADER marks a participant as NOT_ATTENDING.
         Valid from JOINED or READY states.  Session must be SCHEDULED.
    """
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request, club_id, session_id, user_id):
        club = _get_club_or_404(club_id)
        try:
            _require_leader(club, request.user)
        except PermissionError as e:
            return Response({'detail': str(e)}, status=status.HTTP_403_FORBIDDEN)

        with transaction.atomic():
            session = get_object_or_404(
                ClubRideSession.objects.select_for_update(), id=session_id, club=club
            )
            if session.status != SessionStatus.SCHEDULED:
                return Response(
                    {'detail': "Attendance updates are only available for SCHEDULED sessions."},
                    status=status.HTTP_409_CONFLICT,
                )
            participant = get_object_or_404(
                ClubRideParticipant, session=session, user_id=user_id
            )
            try:
                participant.transition_to(ParticipantStatus.NOT_ATTENDING)
                participant.save()
            except ValueError as e:
                return Response({'detail': str(e)}, status=status.HTTP_409_CONFLICT)

        return Response({
            'user_id': str(user_id),
            'status': participant.status,
        }, status=status.HTTP_200_OK)


class ClubRideStartView(views.APIView):
    """
    POST /api/v1/clubs/<club_id>/rides/<session_id>/start/
         LEADER starts the session.  Session must be SCHEDULED and not expired.
         At least 1 VERIFIED participant is required.

         Backend actions on success:
         1. Transitions session → ACTIVE.
         2. Locks roster (only VERIFIED participants matter from here).
         3. For each VERIFIED participant: creates an individual Ride record
            with status=RECORDING, title=session.title, club_ride_session=session.
            Sets ClubRideParticipant.individual_ride.
         4. Sends CLUB_RIDE notifications to all verified participants.
         5. Returns locked roster with individual ride IDs so Android can
            begin recording immediately.
    """
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request, club_id, session_id):
        club = _get_club_or_404(club_id)
        try:
            _require_leader(club, request.user)
        except PermissionError as e:
            return Response({'detail': str(e)}, status=status.HTTP_403_FORBIDDEN)

        with transaction.atomic():
            session = get_object_or_404(
                ClubRideSession.objects.select_for_update(), id=session_id, club=club
            )

            if session.status != SessionStatus.SCHEDULED:
                return Response(
                    {'detail': f"Session is already '{session.status}'. Only SCHEDULED sessions can be started."},
                    status=status.HTTP_409_CONFLICT,
                )
            if session.is_expired_now:
                # Opportunistically expire it.
                session.transition_to(SessionStatus.EXPIRED, actor=request.user)
                session.save()
                return Response(
                    {'detail': "Session has expired and can no longer be started."},
                    status=status.HTTP_410_GONE,
                )

            verified_participants = list(
                ClubRideParticipant.objects.select_for_update()
                .filter(session=session, status=ParticipantStatus.VERIFIED)
                .select_related('user')
            )
            if not verified_participants:
                return Response(
                    {'detail': "No verified participants. At least 1 participant must be verified before starting."},
                    status=status.HTTP_422_UNPROCESSABLE_ENTITY,
                )

            # Transition session to ACTIVE.
            session.transition_to(SessionStatus.ACTIVE, actor=request.user)
            session.save()

            # Create individual Ride records for each VERIFIED participant.
            roster = []
            for participant in verified_participants:
                # Guard: skip if individual ride already exists (prevents duplicates on retry).
                if participant.individual_ride_id:
                    roster.append({
                        'user_id': str(participant.user_id),
                        'username': participant.user.username,
                        'ride_id': str(participant.individual_ride_id),
                    })
                    continue

                ride = Ride.objects.create(
                    rider=participant.user,
                    motorcycle=None,  # Android sets its own motorcycle locally
                    title=session.title,
                    status=RideStatus.RECORDING,
                    start_time=session.actual_start,
                    is_private=True,
                    club_ride_session=session,
                )
                participant.individual_ride = ride
                participant.save(update_fields=['individual_ride'])

                roster.append({
                    'user_id': str(participant.user_id),
                    'username': participant.user.username,
                    'ride_id': str(ride.id),
                })

            # Notify all verified participants.
            _notify_all_verified(
                session,
                title=f"Club Ride Started: {session.title}",
                message=f"Your club ride '{session.title}' has started! Begin recording your ride.",
            )

        _broadcast_session_event(session.id, 'session_started')

        return Response({
            'session_id': str(session.id),
            'status': session.status,
            'actual_start': session.actual_start,
            'roster': roster,
        }, status=status.HTTP_200_OK)


class ClubRideCancelView(views.APIView):
    """
    POST /api/v1/clubs/<club_id>/rides/<session_id>/cancel/
         LEADER cancels the session.  Valid from SCHEDULED or ACTIVE.
         Individual rides already started are NOT deleted — they become orphaned
         solo rides in RECORDING state; the rider may finish or discard them.
    """
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request, club_id, session_id):
        club = _get_club_or_404(club_id)
        try:
            _require_leader(club, request.user)
        except PermissionError as e:
            return Response({'detail': str(e)}, status=status.HTTP_403_FORBIDDEN)

        with transaction.atomic():
            session = get_object_or_404(
                ClubRideSession.objects.select_for_update(), id=session_id, club=club
            )
            try:
                session.transition_to(SessionStatus.CANCELLED, actor=request.user)
                session.save()
            except ValueError as e:
                return Response({'detail': str(e)}, status=status.HTTP_409_CONFLICT)

            # Notify verified participants of cancellation.
            _notify_all_verified(
                session,
                title=f"Club Ride Cancelled: {session.title}",
                message=(
                    f"The club ride '{session.title}' has been cancelled. "
                    "If you were already recording, your individual ride is preserved."
                ),
            )

        _broadcast_session_event(session.id, 'session_cancelled')

        return Response({
            'session_id': str(session.id),
            'status': session.status,
            'actual_end': session.actual_end,
        }, status=status.HTTP_200_OK)


class ClubRideCompleteView(views.APIView):
    """
    POST /api/v1/clubs/<club_id>/rides/<session_id>/complete/
         LEADER marks the session complete.  Session must be ACTIVE.
         Individual rides are NOT auto-completed — each participant finishes
         their own recording locally (via the existing solo ride engine).
    """
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request, club_id, session_id):
        club = _get_club_or_404(club_id)
        try:
            _require_leader(club, request.user)
        except PermissionError as e:
            return Response({'detail': str(e)}, status=status.HTTP_403_FORBIDDEN)

        with transaction.atomic():
            session = get_object_or_404(
                ClubRideSession.objects.select_for_update(), id=session_id, club=club
            )
            if session.status == SessionStatus.COMPLETED:
                return Response({
                    'session_id': str(session.id),
                    'status': session.status,
                    'actual_end': session.actual_end,
                    'already_completed': True,
                }, status=status.HTTP_200_OK)
            try:
                session.transition_to(SessionStatus.COMPLETED, actor=request.user)
                session.save()
            except ValueError as e:
                return Response({'detail': str(e)}, status=status.HTTP_409_CONFLICT)

            _notify_all_verified(
                session,
                title=f"Club Ride Completed: {session.title}",
                message=f"The club ride '{session.title}' is now complete. Don't forget to finish your personal ride recording!",
            )

        _broadcast_session_event(session.id, 'session_completed')

        return Response({
            'session_id': str(session.id),
            'status': session.status,
            'actual_end': session.actual_end,
        }, status=status.HTTP_200_OK)


class ClubRideParticipantListView(views.APIView):
    """
    GET /api/v1/clubs/<club_id>/rides/<session_id>/participants/
        Returns the full participant list.  LEADER only.
        Members can see their own status via the session detail endpoint.
    """
    permission_classes = [permissions.IsAuthenticated]

    def get(self, request, club_id, session_id):
        club = _get_club_or_404(club_id)
        try:
            _require_leader(club, request.user)
        except PermissionError as e:
            return Response({'detail': str(e)}, status=status.HTTP_403_FORBIDDEN)

        session = _get_session_or_404(club, session_id)
        participants = session.participants.select_related(
            'user', 'user__rider_profile'
        ).all()
        serializer = ParticipantSerializer(participants, many=True, context={'request': request})
        return Response(serializer.data, status=status.HTTP_200_OK)

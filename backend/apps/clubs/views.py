from django.shortcuts import get_object_or_404
from django.utils import timezone
from django.contrib.auth.models import User
from rest_framework import views, generics, permissions, status
from rest_framework.response import Response
from .models import Club, ClubMember, RidingEvent, EventRsvp
from .serializers import ClubSerializer, RidingEventSerializer, ClubMemberSerializer


def _check_club_creation_eligibility(user):
    """
    Returns (eligible: bool, completed_rides: int, public_ride_posts: int).
    Eligibility requires: ≥5 completed rides AND ≥1 public ride post.
    """
    from apps.rides.models import Ride, RideStatus
    completed_rides = Ride.objects.filter(rider=user, status=RideStatus.COMPLETED).count()
    try:
        from apps.community.models import Post
        public_posts = Post.objects.filter(
            author=user,
            post_type='RIDE',
            ride__is_private=False
        ).count()
    except Exception:
        public_posts = 0
    eligible = completed_rides >= 5 and public_posts >= 1
    return eligible, completed_rides, public_posts


class ClubEligibilityView(views.APIView):
    permission_classes = [permissions.IsAuthenticated]

    def get(self, request):
        eligible, completed_rides, public_posts = _check_club_creation_eligibility(request.user)
        return Response({
            'eligible': eligible,
            'completed_rides': completed_rides,
            'public_posts': public_posts,
            'needs_rides': max(0, 5 - completed_rides),
            'needs_posts': max(0, 1 - public_posts),
            'requirements': {
                'min_completed_rides': 5,
                'min_public_ride_posts': 1,
            }
        }, status=status.HTTP_200_OK)


class ClubListView(generics.ListCreateAPIView):
    serializer_class = ClubSerializer
    permission_classes = [permissions.IsAuthenticatedOrReadOnly]

    def get_queryset(self):
        city = self.request.query_params.get('city')
        qs = Club.objects.all().prefetch_related('members', 'events')
        if city:
            qs = qs.filter(city__icontains=city)
        return qs

    def perform_create(self, serializer):
        eligible, completed_rides, public_posts = _check_club_creation_eligibility(self.request.user)
        if not eligible:
            from rest_framework.exceptions import PermissionDenied
            msg = []
            if completed_rides < 5:
                msg.append(f"You need {5 - completed_rides} more completed ride(s) (have {completed_rides}, need 5).")
            if public_posts < 1:
                msg.append("You need at least 1 public ride post in the community.")
            raise PermissionDenied(" ".join(msg))
        club = serializer.save(created_by=self.request.user)
        # Creator is automatically a LEADER member
        ClubMember.objects.create(club=club, user=self.request.user, role='LEADER')

class ClubDetailView(generics.RetrieveAPIView):
    queryset = Club.objects.all().prefetch_related('members', 'events')
    serializer_class = ClubSerializer
    permission_classes = [permissions.IsAuthenticatedOrReadOnly]

class ClubJoinToggleView(views.APIView):
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request, club_id):
        club = get_object_or_404(Club, id=club_id)
        membership = ClubMember.objects.filter(club=club, user=request.user).first()
        if membership:
            membership.delete()
            is_member = False
        else:
            ClubMember.objects.create(club=club, user=request.user, role='MEMBER')
            is_member = True

        return Response({
            'club_id': str(club.id),
            'is_member': is_member,
            'members_count': club.members.count()
        }, status=status.HTTP_200_OK)

class ClubEventsView(views.APIView):
    permission_classes = [permissions.IsAuthenticatedOrReadOnly]

    def get(self, request, club_id):
        club = get_object_or_404(Club, id=club_id)
        events = club.events.all().prefetch_related('rsvps')
        serializer = RidingEventSerializer(events, many=True, context={'request': request})
        return Response(serializer.data, status=status.HTTP_200_OK)

    def post(self, request, club_id):
        if not request.user.is_authenticated:
            return Response({'error': 'Authentication required'}, status=status.HTTP_401_UNAUTHORIZED)
        club = get_object_or_404(Club, id=club_id)
        serializer = RidingEventSerializer(data=request.data, context={'request': request})
        if serializer.is_valid():
            event = serializer.save(club=club, created_by=request.user)
            # Creator is automatically RSVP'd as GOING
            EventRsvp.objects.create(event=event, user=request.user, status='GOING')
            out = RidingEventSerializer(event, context={'request': request}).data
            return Response(out, status=status.HTTP_201_CREATED)
        return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

class ExploreEventsView(views.APIView):
    permission_classes = [permissions.IsAuthenticatedOrReadOnly]

    def get(self, request):
        events = RidingEvent.objects.all().select_related('club').prefetch_related('rsvps')
        serializer = RidingEventSerializer(events[:20], many=True, context={'request': request})
        return Response(serializer.data, status=status.HTTP_200_OK)

class EventRsvpToggleView(views.APIView):
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request, event_id):
        event = get_object_or_404(RidingEvent, id=event_id)
        desired_status = request.data.get('status', 'GOING').upper()
        if desired_status not in ['GOING', 'INTERESTED', 'DECLINED']:
            desired_status = 'GOING'

        rsvp = EventRsvp.objects.filter(event=event, user=request.user).first()
        if rsvp and rsvp.status == desired_status:
            # Tapping the same button again cancels RSVP
            rsvp.delete()
            current_status = None
        else:
            rsvp, _ = EventRsvp.objects.update_or_create(
                event=event,
                user=request.user,
                defaults={'status': desired_status}
            )
            current_status = rsvp.status

        return Response({
            'event_id': str(event.id),
            'my_rsvp': current_status,
            'confirmed_count': event.rsvps.filter(status='GOING').count(),
            'interested_count': event.rsvps.filter(status='INTERESTED').count()
        }, status=status.HTTP_200_OK)

class ClubLeaderboardView(views.APIView):
    permission_classes = [permissions.IsAuthenticated]

    def get(self, request, club_id):
        club = get_object_or_404(Club, id=club_id)
        period = request.query_params.get('period', 'monthly').lower()

        member_users = set(ClubMember.objects.filter(club=club).values_list('user', flat=True))
        if club.created_by_id:
            member_users.add(club.created_by_id)

        users = User.objects.filter(id__in=member_users).select_related('rider_profile')

        leaderboard_entries = []
        now = timezone.now()

        from apps.rides.models import Ride, RideStatus
        from django.db.models import Sum

        for u in users:
            profile = getattr(u, 'rider_profile', None)
            disp_name = profile.display_name if profile else u.username
            avatar = profile.avatar_url if profile else ''
            membership = ClubMember.objects.filter(club=club, user=u).first()
            role = membership.role if membership else ('LEADER' if club.created_by == u else 'MEMBER')

            if period == 'monthly':
                dist_m = Ride.objects.filter(
                    rider=u,
                    status=RideStatus.COMPLETED,
                    start_time__year=now.year,
                    start_time__month=now.month
                ).aggregate(total=Sum('total_distance_meters'))['total'] or 0.0
            else: # 'all_time'
                dist_m = Ride.objects.filter(
                    rider=u,
                    status=RideStatus.COMPLETED
                ).aggregate(total=Sum('total_distance_meters'))['total'] or 0.0

            dist_km = dist_m / 1000.0
            leaderboard_entries.append({
                'user_id': u.id,
                'username': u.username,
                'display_name': disp_name,
                'avatar_url': avatar,
                'role': role,
                'distance_km': round(dist_km, 1)
            })

        leaderboard_entries.sort(key=lambda x: x['distance_km'], reverse=True)

        for idx, entry in enumerate(leaderboard_entries):
            entry['rank'] = idx + 1

        return Response({
            'club_id': str(club.id),
            'club_name': club.name,
            'period': period,
            'leaderboard': leaderboard_entries
        }, status=status.HTTP_200_OK)

from django.urls import path
from .views import (
    ClubListView,
    ClubDetailView,
    ClubJoinToggleView,
    ClubEventsView,
    ExploreEventsView,
    EventRsvpToggleView,
    ClubEligibilityView
)
from .session_views import (
    ClubRideSessionListCreateView,
    ClubRideSessionDetailView,
    ClubRideJoinView,
    ClubRideLeaveView,
    ClubRideReadyView,
    ClubRideVerifyParticipantView,
    ClubRideAbsentParticipantView,
    ClubRideStartView,
    ClubRideCancelView,
    ClubRideCompleteView,
    ClubRideParticipantListView,
)
from .route_views import ClubRideRouteComputeView

urlpatterns = [
    path('', ClubListView.as_view(), name='club-list-create'),
    path('eligibility/', ClubEligibilityView.as_view(), name='club-eligibility'),
    path('events/', ExploreEventsView.as_view(), name='club-explore-events'),
    path('events/<uuid:event_id>/rsvp/', EventRsvpToggleView.as_view(), name='club-event-rsvp'),
    path('routes/compute/', ClubRideRouteComputeView.as_view(), name='club-ride-route-compute'),
    path('<uuid:pk>/', ClubDetailView.as_view(), name='club-detail'),
    path('<uuid:club_id>/join/', ClubJoinToggleView.as_view(), name='club-join-toggle'),
    path('<uuid:club_id>/events/', ClubEventsView.as_view(), name='club-events'),

    # Club Ride Sessions (separate from RidingEvent)
    path('<uuid:club_id>/rides/', ClubRideSessionListCreateView.as_view(), name='club-ride-list-create'),
    path('<uuid:club_id>/rides/<uuid:session_id>/', ClubRideSessionDetailView.as_view(), name='club-ride-detail'),
    path('<uuid:club_id>/rides/<uuid:session_id>/join/', ClubRideJoinView.as_view(), name='club-ride-join'),
    path('<uuid:club_id>/rides/<uuid:session_id>/leave/', ClubRideLeaveView.as_view(), name='club-ride-leave'),
    path('<uuid:club_id>/rides/<uuid:session_id>/ready/', ClubRideReadyView.as_view(), name='club-ride-ready'),
    path('<uuid:club_id>/rides/<uuid:session_id>/participants/', ClubRideParticipantListView.as_view(), name='club-ride-participants'),
    path('<uuid:club_id>/rides/<uuid:session_id>/verify/<int:user_id>/', ClubRideVerifyParticipantView.as_view(), name='club-ride-verify'),
    path('<uuid:club_id>/rides/<uuid:session_id>/absent/<int:user_id>/', ClubRideAbsentParticipantView.as_view(), name='club-ride-absent'),
    path('<uuid:club_id>/rides/<uuid:session_id>/start/', ClubRideStartView.as_view(), name='club-ride-start'),
    path('<uuid:club_id>/rides/<uuid:session_id>/cancel/', ClubRideCancelView.as_view(), name='club-ride-cancel'),
    path('<uuid:club_id>/rides/<uuid:session_id>/complete/', ClubRideCompleteView.as_view(), name='club-ride-complete'),
]

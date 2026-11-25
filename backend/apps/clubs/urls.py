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

urlpatterns = [
    path('', ClubListView.as_view(), name='club-list-create'),
    path('eligibility/', ClubEligibilityView.as_view(), name='club-eligibility'),
    path('events/', ExploreEventsView.as_view(), name='club-explore-events'),
    path('events/<uuid:event_id>/rsvp/', EventRsvpToggleView.as_view(), name='club-event-rsvp'),
    path('<uuid:pk>/', ClubDetailView.as_view(), name='club-detail'),
    path('<uuid:club_id>/join/', ClubJoinToggleView.as_view(), name='club-join-toggle'),
    path('<uuid:club_id>/events/', ClubEventsView.as_view(), name='club-events'),
]

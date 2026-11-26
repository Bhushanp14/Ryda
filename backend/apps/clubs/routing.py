from django.urls import re_path
from .consumers import ClubRideLocationConsumer

websocket_urlpatterns = [
    re_path(
        r'^ws/club-rides/(?P<session_id>[0-9a-fA-F-]+)/location/$',
        ClubRideLocationConsumer.as_asgi(),
    ),
]

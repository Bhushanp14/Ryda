from django.urls import path
from .views import (
    PostFeedView,
    PostLikeToggleView,
    PostCommentsView,
    RiderFollowToggleView
)

urlpatterns = [
    path('feed/', PostFeedView.as_view(), name='community-feed'),
    path('posts/', PostFeedView.as_view(), name='community-posts-create'),
    path('posts/<uuid:post_id>/like/', PostLikeToggleView.as_view(), name='community-post-like'),
    path('posts/<uuid:post_id>/comments/', PostCommentsView.as_view(), name='community-post-comments'),
    path('riders/<int:user_id>/follow/', RiderFollowToggleView.as_view(), name='community-rider-follow'),
]

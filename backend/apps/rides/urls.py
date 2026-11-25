from django.urls import path
from .views import (
    RideListCreateView,
    RideDetailView,
    GpsPointBatchUploadView,
    RideCompleteView,
    RideSummaryView,
    RidePhotoUploadView
)

urlpatterns = [
    path('', RideListCreateView.as_view(), name='ride-list-create'),
    path('<uuid:pk>/', RideDetailView.as_view(), name='ride-detail'),
    path('<uuid:pk>/photo/', RidePhotoUploadView.as_view(), name='ride-photo-upload'),
    path('<uuid:pk>/points/batch/', GpsPointBatchUploadView.as_view(), name='ride-points-batch'),
    path('<uuid:pk>/complete/', RideCompleteView.as_view(), name='ride-complete'),
    path('<uuid:pk>/summary/', RideSummaryView.as_view(), name='ride-summary'),
]

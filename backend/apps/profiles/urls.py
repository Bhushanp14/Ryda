from django.urls import path
from .views import ProfileMeView, ProfileDataExportView, ProfileAvatarUploadView

urlpatterns = [
    path('me/', ProfileMeView.as_view(), name='profile-me'),
    path('avatar/', ProfileAvatarUploadView.as_view(), name='profile-avatar-upload'),
    path('export/', ProfileDataExportView.as_view(), name='profile-export'),
]

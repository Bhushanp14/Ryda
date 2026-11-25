from django.contrib import admin
from django.urls import path, include
from django.conf import settings
from django.conf.urls.static import static
from apps.accounts.views import AccountDeletionWebPageView

urlpatterns = [
    path('admin/', admin.site.urls),
    path('privacy/delete-account/', AccountDeletionWebPageView.as_view(), name='privacy-delete-account'),
    path('api/v1/auth/', include('apps.accounts.urls')),
    path('api/v1/profile/', include('apps.profiles.urls')),
    path('api/v1/garage/', include('apps.garage.urls')),
    path('api/v1/rides/', include('apps.rides.urls')),
    path('api/v1/community/', include('apps.community.urls')),
    path('api/v1/clubs/', include('apps.clubs.urls')),
    path('api/v1/achievements/', include('apps.achievements.urls')),
    path('api/v1/notifications/', include('apps.notifications.urls')),
]

if settings.DEBUG:
    urlpatterns += static(settings.MEDIA_URL, document_root=settings.MEDIA_ROOT)

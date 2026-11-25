from django.urls import path, include
from rest_framework.routers import DefaultRouter
from .views import MotorcycleViewSet

router = DefaultRouter()
router.register(r'motorcycles', MotorcycleViewSet, basename='motorcycles')

urlpatterns = [
    path('', include(router.urls)),
]

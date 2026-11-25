from rest_framework import viewsets, permissions
from .models import Motorcycle
from .serializers import MotorcycleSerializer

class MotorcycleViewSet(viewsets.ModelViewSet):
    serializer_class = MotorcycleSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        return Motorcycle.objects.filter(owner=self.request.user)

    def perform_create(self, serializer):
        serializer.save(owner=self.request.user)

from django.shortcuts import get_object_or_404
from rest_framework import views, permissions, status
from rest_framework.response import Response
from .models import Notification
from .serializers import NotificationSerializer

class NotificationListView(views.APIView):
    permission_classes = [permissions.IsAuthenticated]

    def get(self, request):
        notifications = Notification.objects.filter(user=request.user)
        unread_count = notifications.filter(is_read=False).count()
        serializer = NotificationSerializer(notifications[:50], many=True)
        return Response({
            'unread_count': unread_count,
            'notifications': serializer.data
        }, status=status.HTTP_200_OK)

class NotificationMarkReadView(views.APIView):
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request, pk):
        notif = get_object_or_404(Notification, id=pk, user=request.user)
        notif.is_read = True
        notif.save()
        return Response({'success': True, 'id': str(notif.id)}, status=status.HTTP_200_OK)

class NotificationMarkAllReadView(views.APIView):
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request):
        Notification.objects.filter(user=request.user, is_read=False).update(is_read=True)
        return Response({'success': True, 'unread_count': 0}, status=status.HTTP_200_OK)

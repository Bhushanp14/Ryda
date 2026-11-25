import os
import uuid
from rest_framework import generics, permissions, views, status
from rest_framework.response import Response
from rest_framework.parsers import MultiPartParser, FormParser, JSONParser
from django.utils import timezone
from django.core.files.storage import default_storage
from .models import RiderProfile
from .serializers import RiderProfileSerializer
from apps.garage.models import Motorcycle
from apps.garage.serializers import MotorcycleSerializer
from apps.rides.models import Ride, GpsPoint
from apps.rides.serializers import RideSerializer, GpsPointSerializer

class ProfileMeView(generics.RetrieveUpdateAPIView):
    serializer_class = RiderProfileSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_object(self):
        profile, _ = RiderProfile.objects.get_or_create(
            user=self.request.user,
            defaults={'display_name': self.request.user.username}
        )
        return profile

class ProfileDataExportView(views.APIView):
    permission_classes = [permissions.IsAuthenticated]

    def get(self, request):
        user = request.user
        profile, _ = RiderProfile.objects.get_or_create(user=user, defaults={'display_name': user.username})
        profile_data = RiderProfileSerializer(profile).data

        motorcycles = Motorcycle.objects.filter(owner=user)
        motorcycles_data = MotorcycleSerializer(motorcycles, many=True).data

        rides = Ride.objects.filter(rider=user).order_by('-start_time')
        rides_data = []
        for ride in rides:
            r_data = RideSerializer(ride).data
            points = GpsPoint.objects.filter(ride=ride).order_by('sequence_number')
            r_data['points'] = GpsPointSerializer(points, many=True).data
            rides_data.append(r_data)

        export_payload = {
            'export_version': '1.0',
            'exported_at': timezone.now().isoformat(),
            'user': {
                'id': user.id,
                'username': user.username,
                'email': user.email,
                'date_joined': user.date_joined.isoformat(),
            },
            'profile': profile_data,
            'garage': motorcycles_data,
            'rides': rides_data,
            'total_rides_count': len(rides_data),
        }
        return Response(export_payload, status=status.HTTP_200_OK)


class ProfileAvatarUploadView(views.APIView):
    permission_classes = [permissions.IsAuthenticated]
    parser_classes = [MultiPartParser, FormParser, JSONParser]

    def post(self, request):
        profile, _ = RiderProfile.objects.get_or_create(
            user=request.user,
            defaults={'display_name': request.user.username}
        )
        avatar_file = request.FILES.get('avatar') or request.FILES.get('image')
        avatar_url = request.data.get('avatar_url')

        if avatar_file:
            ext = os.path.splitext(avatar_file.name)[1] or '.jpg'
            file_name = f"avatars/{request.user.id}_{uuid.uuid4().hex[:8]}{ext}"
            saved_path = default_storage.save(file_name, avatar_file)
            full_url = f"/media/{saved_path}"
            profile.avatar_url = full_url
            profile.save(update_fields=['avatar_url'])
            return Response({
                'avatar_url': profile.avatar_url,
                'message': 'Avatar uploaded successfully.'
            }, status=status.HTTP_200_OK)
        elif avatar_url is not None:
            profile.avatar_url = avatar_url
            profile.save(update_fields=['avatar_url'])
            return Response({
                'avatar_url': profile.avatar_url,
                'message': 'Avatar updated successfully.'
            }, status=status.HTTP_200_OK)
        else:
            return Response(
                {'error': 'No avatar file or avatar_url provided.'},
                status=status.HTTP_400_BAD_REQUEST
            )

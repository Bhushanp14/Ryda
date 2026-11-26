import os
import uuid
from rest_framework import generics, status, permissions, serializers
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.parsers import MultiPartParser, FormParser, JSONParser
from django.shortcuts import get_object_or_404
from django.utils import timezone
from django.core.files.storage import default_storage
from django.core.files.base import ContentFile
from .models import Ride, GpsPoint, RideUploadBatch, RideStatus
from .serializers import RideSerializer, GpsPointSerializer, GpsPointBatchUploadSerializer
from .calculations import recalculate_ride_statistics
from apps.profiles.models import RiderProfile

class RideListCreateView(generics.ListCreateAPIView):
    serializer_class = RideSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        qs = Ride.objects.filter(rider=self.request.user)
        moto_id = self.request.query_params.get('motorcycle') or self.request.query_params.get('motorcycle_id')
        if moto_id:
            qs = qs.filter(motorcycle_id=moto_id)
        return qs

    def create(self, request, *args, **kwargs):
        data = request.data.copy() if hasattr(request.data, 'copy') else dict(request.data)
        if 'motorcycle_id' in data and 'motorcycle' not in data:
            data['motorcycle'] = data['motorcycle_id']
        serializer = self.get_serializer(data=data)
        serializer.is_valid(raise_exception=True)

        ride_id = serializer.validated_data.get('id')
        if ride_id:
            existing_ride = Ride.objects.filter(id=ride_id, rider=request.user).first()
            if existing_ride:
                for key, value in serializer.validated_data.items():
                    if key != 'id':
                        setattr(existing_ride, key, value)
                existing_ride.save()
                return Response(RideSerializer(existing_ride).data, status=status.HTTP_200_OK)

        self.perform_create(serializer)
        headers = self.get_success_headers(serializer.data)
        return Response(serializer.data, status=status.HTTP_201_CREATED, headers=headers)

    def perform_create(self, serializer):
        serializer.save(rider=self.request.user)

class RideDetailView(generics.RetrieveUpdateDestroyAPIView):
    serializer_class = RideSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_queryset(self):
        return Ride.objects.filter(rider=self.request.user)

    def retrieve(self, request, *args, **kwargs):
        ride = self.get_object()
        serializer = self.get_serializer(ride)
        points = GpsPoint.objects.filter(ride=ride).order_by('sequence_number')
        points_data = GpsPointSerializer(points, many=True).data
        response_data = serializer.data
        response_data['points'] = points_data
        return Response(response_data)

class GpsPointBatchUploadView(APIView):
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request, pk):
        ride = get_object_or_404(Ride, pk=pk, rider=request.user)
        serializer = GpsPointBatchUploadSerializer(data=request.data)
        if not serializer.is_valid():
            return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

        batch_id = serializer.validated_data['batch_id']
        batch_index = serializer.validated_data['batch_index']
        seq_start = serializer.validated_data['sequence_start']
        seq_end = serializer.validated_data['sequence_end']
        points_data = serializer.validated_data['points']

        # Validate point_count matches sequence range (point_count = seq_end - seq_start + 1)
        expected_point_count = (seq_end - seq_start) + 1
        if len(points_data) != expected_point_count:
            return Response(
                {'error': f'Point count mismatch: received {len(points_data)}, expected {expected_point_count} for range {seq_start}-{seq_end}'},
                status=status.HTTP_400_BAD_REQUEST
            )

        # Check existing batch by batch_index for this ride
        existing_batch_by_index = RideUploadBatch.objects.filter(ride=ride, batch_index=batch_index).first()
        existing_batch_by_id = RideUploadBatch.objects.filter(batch_id=batch_id).first()

        if existing_batch_by_index:
            is_identical = (
                existing_batch_by_index.batch_id == batch_id and
                existing_batch_by_index.sequence_start == seq_start and
                existing_batch_by_index.sequence_end == seq_end
            )
            if is_identical:
                return Response({
                    'message': 'Batch already processed (idempotent duplicate)',
                    'batch_id': batch_id,
                    'batch_index': batch_index,
                    'status': 'ACKNOWLEDGED'
                }, status=status.HTTP_200_OK)
            else:
                return Response(
                    {'error': f'Batch index {batch_index} already exists with different content'},
                    status=status.HTTP_400_BAD_REQUEST
                )

        if existing_batch_by_id:
            return Response(
                {'error': f'Batch ID {batch_id} already exists'},
                status=status.HTTP_400_BAD_REQUEST
            )

        # Bulk create GPS points
        new_points = [
            GpsPoint(
                ride=ride,
                sequence_number=p['sequence_number'],
                timestamp=p['timestamp'],
                latitude=p['latitude'],
                longitude=p['longitude'],
                altitude=p.get('altitude', 0.0),
                accuracy=p.get('accuracy', 0.0),
                speed_kmh=p.get('speed_kmh', 0.0)
            )
            for p in points_data
        ]

        GpsPoint.objects.bulk_create(new_points, ignore_conflicts=True)

        # Record upload batch
        RideUploadBatch.objects.create(
            batch_id=batch_id,
            ride=ride,
            batch_index=batch_index,
            sequence_start=seq_start,
            sequence_end=seq_end
        )

        return Response({
            'message': f"Successfully processed {len(new_points)} points for ride {ride.id}",
            'batch_id': batch_id,
            'batch_index': batch_index,
            'total_saved': len(new_points),
            'count': len(new_points)
        }, status=status.HTTP_201_CREATED)

class RideCompleteView(APIView):
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request, pk):
        ride = get_object_or_404(Ride, pk=pk, rider=request.user)

        if ride.status == RideStatus.COMPLETED:
            return Response({
                'status': 'ALREADY_COMPLETED',
                'message': 'Ride is already completed.',
                'ride': RideSerializer(ride).data
            }, status=status.HTTP_200_OK)

        batches = RideUploadBatch.objects.filter(ride=ride).order_by('batch_index')
        actual_batches_count = batches.count()
        actual_points_count = GpsPoint.objects.filter(ride=ride).count()

        expected_points_count = request.data.get('expected_points_count')
        expected_batches_count = request.data.get('expected_batches_count')

        # 1. Exactly expected_batches_count batches exist & 2. Every index from 0 through expected_batches_count - 1 exists
        if expected_batches_count is not None:
            try:
                expected_batches_count = int(expected_batches_count)
            except (ValueError, TypeError):
                return Response({'error': 'Invalid expected_batches_count'}, status=status.HTTP_400_BAD_REQUEST)

            if actual_batches_count != expected_batches_count:
                return Response(
                    {'error': f'Incomplete batches count: received {actual_batches_count}, expected {expected_batches_count}'},
                    status=status.HTTP_400_BAD_REQUEST
                )

            batch_indices = list(batches.values_list('batch_index', flat=True))
            expected_indices = list(range(expected_batches_count))
            if batch_indices != expected_indices:
                return Response(
                    {'error': f'Missing batch indices: found {batch_indices}, expected {expected_indices}'},
                    status=status.HTTP_400_BAD_REQUEST
                )
        elif actual_batches_count > 0:
            batch_indices = list(batches.values_list('batch_index', flat=True))
            expected_indices = list(range(actual_batches_count))
            if batch_indices != expected_indices:
                return Response(
                    {'error': f'Missing batch indices: found {batch_indices}'},
                    status=status.HTTP_400_BAD_REQUEST
                )

        # 3. Sequence ranges are contiguous without gaps or overlaps & 4. First sequence starts at 0
        if actual_batches_count > 0:
            sorted_batches = list(batches)
            if sorted_batches[0].sequence_start != 0:
                return Response(
                    {'error': f'Sequence does not start at 0: starts at {sorted_batches[0].sequence_start}'},
                    status=status.HTTP_400_BAD_REQUEST
                )

            for i in range(len(sorted_batches)):
                b = sorted_batches[i]
                if b.sequence_end < b.sequence_start:
                    return Response(
                        {'error': f'Invalid sequence range in batch {b.batch_index}: {b.sequence_start} to {b.sequence_end}'},
                        status=status.HTTP_400_BAD_REQUEST
                    )
                if i > 0:
                    prev_b = sorted_batches[i - 1]
                    if b.sequence_start != prev_b.sequence_end + 1:
                        return Response(
                            {'error': f'Sequence gap or overlap between batch {prev_b.batch_index} (end {prev_b.sequence_end}) and batch {b.batch_index} (start {b.sequence_start})'},
                            status=status.HTTP_400_BAD_REQUEST
                        )

            expected_total_span = sorted_batches[-1].sequence_end + 1
            if actual_points_count != expected_total_span:
                return Response(
                    {'error': f'Sequence span / point count mismatch: {actual_points_count} points for span 0 to {expected_total_span}'},
                    status=status.HTTP_400_BAD_REQUEST
                )

        # 5. Persisted point count matches expected_points_count
        if expected_points_count is not None:
            try:
                expected_points_count = int(expected_points_count)
            except (ValueError, TypeError):
                return Response({'error': 'Invalid expected_points_count'}, status=status.HTTP_400_BAD_REQUEST)

            if actual_points_count != expected_points_count:
                return Response(
                    {'error': f'Incomplete points count: received {actual_points_count}, expected {expected_points_count}'},
                    status=status.HTTP_400_BAD_REQUEST
                )

        # Recalculate trusted server-side metrics
        points = GpsPoint.objects.filter(ride=ride).order_by('sequence_number')
        stats = recalculate_ride_statistics(points)

        ride.status = RideStatus.COMPLETED
        if request.data.get('end_time'):
            try:
                ride.end_time = request.data.get('end_time')
            except Exception:
                ride.end_time = ride.end_time or timezone.now()
        else:
            ride.end_time = ride.end_time or timezone.now()

        # If points provided meaningful stats with measured distance > 0, use server stats.
        # Otherwise fallback to client-supplied metrics if provided.
        if stats['total_distance_meters'] > 0:
            ride.total_distance_meters = stats['total_distance_meters']
            ride.moving_time_seconds = stats['moving_time_seconds']
            ride.elapsed_time_seconds = stats['elapsed_time_seconds']
            ride.max_speed_kmh = stats['max_speed_kmh']
            ride.avg_speed_kmh = stats['avg_speed_kmh']
            ride.elevation_gain_meters = stats['elevation_gain_meters']
            ride.elevation_loss_meters = stats['elevation_loss_meters']
        else:
            if 'total_distance_meters' in request.data:
                try:
                    val = float(request.data['total_distance_meters'])
                    if val > 0:
                        ride.total_distance_meters = val
                except (ValueError, TypeError):
                    pass
            if 'moving_time_seconds' in request.data:
                try:
                    val = int(request.data['moving_time_seconds'])
                    if val > 0:
                        ride.moving_time_seconds = val
                except (ValueError, TypeError):
                    pass
            if 'elapsed_time_seconds' in request.data:
                try:
                    val = int(request.data['elapsed_time_seconds'])
                    if val > 0:
                        ride.elapsed_time_seconds = val
                except (ValueError, TypeError):
                    pass
            if 'max_speed_kmh' in request.data:
                try:
                    val = float(request.data['max_speed_kmh'])
                    if val > 0:
                        ride.max_speed_kmh = val
                except (ValueError, TypeError):
                    pass
            if 'avg_speed_kmh' in request.data:
                try:
                    val = float(request.data['avg_speed_kmh'])
                    if val > 0:
                        ride.avg_speed_kmh = val
                except (ValueError, TypeError):
                    pass
            if 'elevation_gain_meters' in request.data:
                try:
                    val = float(request.data['elevation_gain_meters'])
                    if val > 0:
                        ride.elevation_gain_meters = val
                except (ValueError, TypeError):
                    pass
            if 'elevation_loss_meters' in request.data:
                try:
                    val = float(request.data['elevation_loss_meters'])
                    if val > 0:
                        ride.elevation_loss_meters = val
                except (ValueError, TypeError):
                    pass

        ride.save()

        # Update lifetime rider totals & Personal Records (PR System)
        user_rides = Ride.objects.filter(rider=request.user, status=RideStatus.COMPLETED)
        total_rides = user_rides.count()
        total_dist_km = sum(r.total_distance_km for r in user_rides)
        total_hours = sum(r.moving_time_seconds for r in user_rides) / 3600.0

        profile, _ = RiderProfile.objects.get_or_create(user=request.user, defaults={'display_name': request.user.username})

        pr_updates = []
        ride_km = ride.total_distance_km
        if ride_km > profile.longest_ride_km:
            profile.longest_ride_km = round(ride_km, 2)
            pr_updates.append(f"Longest Ride: {profile.longest_ride_km} km")

        moving_sec = ride.moving_time_seconds
        if moving_sec > profile.longest_duration_seconds:
            profile.longest_duration_seconds = moving_sec
            hrs = moving_sec // 3600
            mins = (moving_sec % 3600) // 60
            dur_str = f"{hrs}h {mins}m" if hrs > 0 else f"{mins}m"
            pr_updates.append(f"Longest Duration: {dur_str}")

        now = timezone.now()
        start_of_month = now.replace(day=1, hour=0, minute=0, second=0, microsecond=0)
        monthly_rides = Ride.objects.filter(rider=request.user, status=RideStatus.COMPLETED, start_time__gte=start_of_month)
        monthly_dist_km = sum(r.total_distance_km for r in monthly_rides)
        monthly_count = monthly_rides.count()

        if monthly_dist_km > profile.highest_monthly_distance_km:
            profile.highest_monthly_distance_km = round(monthly_dist_km, 2)
            pr_updates.append(f"Highest Monthly Distance: {profile.highest_monthly_distance_km} km")

        if monthly_count > profile.most_rides_monthly:
            profile.most_rides_monthly = monthly_count
            pr_updates.append(f"Most Monthly Rides: {profile.most_rides_monthly} rides")

        profile.total_rides = total_rides
        profile.total_distance_km = round(total_dist_km, 2)
        profile.total_time_hours = round(total_hours, 2)
        profile.save()

        # Trigger event-driven achievement evaluation
        try:
            from apps.achievements.services import evaluate_user_achievements
            evaluate_user_achievements(request.user)
        except Exception:
            pass

        serialized_ride = RideSerializer(ride).data
        response_data = dict(serialized_ride)
        response_data['message'] = 'Ride marked as complete and verified.'
        response_data['ride'] = serialized_ride
        response_data['calculated_stats'] = stats
        response_data['new_personal_records'] = pr_updates

        return Response(response_data)

class RideSummaryView(APIView):
    permission_classes = [permissions.IsAuthenticated]

    def get(self, request, pk):
        ride = get_object_or_404(Ride, pk=pk, rider=request.user)
        points_count = GpsPoint.objects.filter(ride=ride).count()
        return Response({
            'ride_id': ride.id,
            'title': ride.title,
            'status': ride.status,
            'total_distance_km': ride.total_distance_km,
            'moving_time_seconds': ride.moving_time_seconds,
            'elapsed_time_seconds': ride.elapsed_time_seconds,
            'max_speed_kmh': ride.max_speed_kmh,
            'avg_speed_kmh': ride.avg_speed_kmh,
            'points_count': points_count
        })

class RidePhotoUploadView(APIView):
    permission_classes = [permissions.IsAuthenticated]
    parser_classes = [MultiPartParser, FormParser, JSONParser]

    def post(self, request, pk):
        ride = get_object_or_404(Ride, pk=pk, rider=request.user)
        photo_file = request.FILES.get('photo') or request.FILES.get('image')
        photo_url = request.data.get('photo_url') or request.data.get('image_url')

        if photo_file:
            ext = os.path.splitext(photo_file.name)[1] or '.jpg'
            file_name = f"rides/{ride.id}_{uuid.uuid4().hex[:8]}{ext}"
            saved_path = default_storage.save(file_name, photo_file)
            full_url = f"/media/{saved_path}"
            ride.image_url = full_url
            ride.save(update_fields=['image_url'])
            return Response({
                'ride_id': str(ride.id),
                'image_url': ride.image_url,
                'message': 'Ride photo uploaded successfully.'
            }, status=status.HTTP_200_OK)
        elif photo_url:
            ride.image_url = photo_url
            ride.save(update_fields=['image_url'])
            return Response({
                'ride_id': str(ride.id),
                'image_url': ride.image_url,
                'message': 'Ride photo URL updated successfully.'
            }, status=status.HTTP_200_OK)
        else:
            return Response(
                {'error': 'No photo file or photo_url provided.'},
                status=status.HTTP_400_BAD_REQUEST
            )

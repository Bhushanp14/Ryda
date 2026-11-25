import uuid
from django.test import TestCase
from django.contrib.auth.models import User
from django.utils import timezone
from rest_framework.test import APIClient
from rest_framework import status
from apps.rides.models import Ride, GpsPoint, RideStatus, RideUploadBatch
from apps.garage.models import Motorcycle
from apps.profiles.models import RiderProfile

class RydaApiIntegrationTests(TestCase):

    def setUp(self):
        self.client = APIClient()
        self.user = User.objects.create_user(
            username='test_rider',
            email='rider@example.com',
            password='testpassword123'
        )
        self.profile = RiderProfile.objects.create(
            user=self.user,
            display_name='Test Rider',
            city='Bengaluru'
        )
        self.bike = Motorcycle.objects.create(
            owner=self.user,
            brand='Royal Enfield',
            model='Himalayan 450',
            year=2024,
            engine_cc=452,
            is_primary=True
        )
        self.client.force_authenticate(user=self.user)

    def _create_batch_payload(self, batch_id, batch_index, seq_start, count):
        points = []
        now = timezone.now()
        for i in range(count):
            seq = seq_start + i
            points.append({
                'sequence_number': seq,
                'timestamp': (now + timezone.timedelta(seconds=seq)).isoformat(),
                'latitude': 13.3700 + (seq * 0.0001),
                'longitude': 77.6800 + (seq * 0.0001),
                'altitude': 900.0,
                'accuracy': 5.0,
                'speed_kmh': 40.0
            })
        return {
            'batch_id': batch_id,
            'batch_index': batch_index,
            'sequence_start': seq_start,
            'sequence_end': seq_start + count - 1,
            'points': points
        }

    def test_create_ride_with_client_uuid(self):
        client_uuid = str(uuid.uuid4())
        payload = {
            'id': client_uuid,
            'title': 'Sunday Morning Ride',
            'start_time': timezone.now().isoformat(),
            'motorcycle': str(self.bike.id)
        }
        response = self.client.post('/api/v1/rides/', payload, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertEqual(response.data['id'], client_uuid)
        self.assertEqual(Ride.objects.count(), 1)

    def test_gps_point_batch_upload_and_idempotency(self):
        client_uuid = str(uuid.uuid4())
        ride = Ride.objects.create(
            id=client_uuid,
            rider=self.user,
            motorcycle=self.bike,
            title='Ghat Road Test',
            start_time=timezone.now()
        )

        batch_id = "batch-unique-001"
        points_payload = {
            'batch_id': batch_id,
            'batch_index': 0,
            'sequence_start': 1,
            'sequence_end': 2,
            'points': [
                {
                    'sequence_number': 1,
                    'timestamp': timezone.now().isoformat(),
                    'latitude': 13.3702,
                    'longitude': 77.6835,
                    'altitude': 920.0,
                    'accuracy': 5.0,
                    'speed_kmh': 45.0
                },
                {
                    'sequence_number': 2,
                    'timestamp': (timezone.now() + timezone.timedelta(seconds=2)).isoformat(),
                    'latitude': 13.3710,
                    'longitude': 77.6840,
                    'altitude': 925.0,
                    'accuracy': 4.0,
                    'speed_kmh': 50.0
                }
            ]
        }

        # First upload
        response1 = self.client.post(f'/api/v1/rides/{client_uuid}/points/batch/', points_payload, format='json')
        self.assertEqual(response1.status_code, status.HTTP_201_CREATED)
        self.assertEqual(GpsPoint.objects.filter(ride=ride).count(), 2)

        # Second upload (idempotent retry with same batch_id and batch_index)
        response2 = self.client.post(f'/api/v1/rides/{client_uuid}/points/batch/', points_payload, format='json')
        self.assertEqual(response2.status_code, status.HTTP_200_OK)
        self.assertIn('Batch already processed', response2.data['message'])
        # Ensure points were NOT duplicated
        self.assertEqual(GpsPoint.objects.filter(ride=ride).count(), 2)

    def test_complete_ride_triggers_recalculation(self):
        client_uuid = str(uuid.uuid4())
        now = timezone.now()
        ride = Ride.objects.create(
            id=client_uuid,
            rider=self.user,
            motorcycle=self.bike,
            title='Completed Trip',
            start_time=now
        )
        # Add points
        GpsPoint.objects.create(
            ride=ride, sequence_number=0, timestamp=now,
            latitude=13.3700, longitude=77.6800, altitude=900.0, accuracy=5.0, speed_kmh=60.0
        )
        GpsPoint.objects.create(
            ride=ride, sequence_number=1, timestamp=now + timezone.timedelta(seconds=10),
            latitude=13.3710, longitude=77.6810, altitude=910.0, accuracy=5.0, speed_kmh=60.0
        )
        RideUploadBatch.objects.create(batch_id='b-0', ride=ride, batch_index=0, sequence_start=0, sequence_end=1)

        response = self.client.post(f'/api/v1/rides/{client_uuid}/complete/', {'expected_points_count': 2, 'expected_batches_count': 1}, format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)

        ride.refresh_from_db()
        self.assertEqual(ride.status, RideStatus.COMPLETED)
        self.assertGreater(ride.total_distance_meters, 0)
        self.assertGreater(ride.moving_time_seconds, 0)

        # Profile aggregates should be updated
        self.profile.refresh_from_db()
        self.assertEqual(self.profile.total_rides, 1)
        self.assertGreater(self.profile.total_distance_km, 0)

    # ── 10 Specified Test Scenarios ──────────────────────────────────────────

    def test_scenario_1_five_correct_batches(self):
        ride_id = str(uuid.uuid4())
        Ride.objects.create(id=ride_id, rider=self.user, title="5 Batches", start_time=timezone.now())
        for idx in range(5):
            payload = self._create_batch_payload(f"b-{idx}", idx, idx * 10, 10)
            res = self.client.post(f'/api/v1/rides/{ride_id}/points/batch/', payload, format='json')
            self.assertEqual(res.status_code, status.HTTP_201_CREATED)
        complete_res = self.client.post(f'/api/v1/rides/{ride_id}/complete/', {
            'expected_points_count': 50,
            'expected_batches_count': 5
        }, format='json')
        self.assertEqual(complete_res.status_code, status.HTTP_200_OK)

    def test_scenario_2_missing_batch_3(self):
        ride_id = str(uuid.uuid4())
        Ride.objects.create(id=ride_id, rider=self.user, title="Missing Batch 3", start_time=timezone.now())
        for idx in [0, 1, 2, 4]:
            payload = self._create_batch_payload(f"b-{idx}", idx, idx * 10, 10)
            self.client.post(f'/api/v1/rides/{ride_id}/points/batch/', payload, format='json')
        complete_res = self.client.post(f'/api/v1/rides/{ride_id}/complete/', {
            'expected_points_count': 40,
            'expected_batches_count': 5
        }, format='json')
        self.assertEqual(complete_res.status_code, status.HTTP_400_BAD_REQUEST)

    def test_scenario_3_duplicate_batch_index_conflict(self):
        ride_id = str(uuid.uuid4())
        Ride.objects.create(id=ride_id, rider=self.user, title="Dup Batch Index", start_time=timezone.now())
        payload1 = self._create_batch_payload("b-2-a", 2, 20, 10)
        res1 = self.client.post(f'/api/v1/rides/{ride_id}/points/batch/', payload1, format='json')
        self.assertEqual(res1.status_code, status.HTTP_201_CREATED)
        payload2 = self._create_batch_payload("b-2-b", 2, 20, 10)
        res2 = self.client.post(f'/api/v1/rides/{ride_id}/points/batch/', payload2, format='json')
        self.assertEqual(res2.status_code, status.HTTP_400_BAD_REQUEST)

    def test_scenario_4_overlapping_ranges(self):
        ride_id = str(uuid.uuid4())
        Ride.objects.create(id=ride_id, rider=self.user, title="Overlapping", start_time=timezone.now())
        p0 = self._create_batch_payload("b-0", 0, 0, 15)
        self.client.post(f'/api/v1/rides/{ride_id}/points/batch/', p0, format='json')
        p1 = self._create_batch_payload("b-1", 1, 10, 15)
        self.client.post(f'/api/v1/rides/{ride_id}/points/batch/', p1, format='json')
        complete_res = self.client.post(f'/api/v1/rides/{ride_id}/complete/', {
            'expected_points_count': 30,
            'expected_batches_count': 2
        }, format='json')
        self.assertEqual(complete_res.status_code, status.HTTP_400_BAD_REQUEST)

    def test_scenario_5_sequence_gaps(self):
        ride_id = str(uuid.uuid4())
        Ride.objects.create(id=ride_id, rider=self.user, title="Seq Gaps", start_time=timezone.now())
        p0 = self._create_batch_payload("b-0", 0, 0, 10)
        self.client.post(f'/api/v1/rides/{ride_id}/points/batch/', p0, format='json')
        p1 = self._create_batch_payload("b-1", 1, 15, 10)
        self.client.post(f'/api/v1/rides/{ride_id}/points/batch/', p1, format='json')
        complete_res = self.client.post(f'/api/v1/rides/{ride_id}/complete/', {
            'expected_points_count': 20,
            'expected_batches_count': 2
        }, format='json')
        self.assertEqual(complete_res.status_code, status.HTTP_400_BAD_REQUEST)

    def test_scenario_6_duplicate_identical_batch_retry(self):
        ride_id = str(uuid.uuid4())
        Ride.objects.create(id=ride_id, rider=self.user, title="Identical Retry", start_time=timezone.now())
        payload = self._create_batch_payload("b-0", 0, 0, 10)
        res1 = self.client.post(f'/api/v1/rides/{ride_id}/points/batch/', payload, format='json')
        self.assertEqual(res1.status_code, status.HTTP_201_CREATED)
        res2 = self.client.post(f'/api/v1/rides/{ride_id}/points/batch/', payload, format='json')
        self.assertEqual(res2.status_code, status.HTTP_200_OK)

    def test_scenario_7_batch_index_reused_with_different_content(self):
        ride_id = str(uuid.uuid4())
        Ride.objects.create(id=ride_id, rider=self.user, title="Index Reused Diff Content", start_time=timezone.now())
        payload1 = self._create_batch_payload("b-0", 0, 0, 10)
        self.client.post(f'/api/v1/rides/{ride_id}/points/batch/', payload1, format='json')
        payload2 = self._create_batch_payload("b-0-diff", 0, 5, 10)
        res = self.client.post(f'/api/v1/rides/{ride_id}/points/batch/', payload2, format='json')
        self.assertEqual(res.status_code, status.HTTP_400_BAD_REQUEST)

    def test_scenario_8_incorrect_point_count(self):
        ride_id = str(uuid.uuid4())
        Ride.objects.create(id=ride_id, rider=self.user, title="Incorrect Pt Count", start_time=timezone.now())
        payload = self._create_batch_payload("b-0", 0, 0, 10)
        self.client.post(f'/api/v1/rides/{ride_id}/points/batch/', payload, format='json')
        complete_res = self.client.post(f'/api/v1/rides/{ride_id}/complete/', {
            'expected_points_count': 15,
            'expected_batches_count': 1
        }, format='json')
        self.assertEqual(complete_res.status_code, status.HTTP_400_BAD_REQUEST)

    def test_scenario_9_correct_completion(self):
        ride_id = str(uuid.uuid4())
        Ride.objects.create(id=ride_id, rider=self.user, title="Correct Completion", start_time=timezone.now())
        payload = self._create_batch_payload("b-0", 0, 0, 10)
        self.client.post(f'/api/v1/rides/{ride_id}/points/batch/', payload, format='json')
        complete_res = self.client.post(f'/api/v1/rides/{ride_id}/complete/', {
            'expected_points_count': 10,
            'expected_batches_count': 1
        }, format='json')
        self.assertEqual(complete_res.status_code, status.HTTP_200_OK)
        self.assertEqual(complete_res.data['ride']['status'], 'COMPLETED')

    def test_scenario_10_repeated_completion(self):
        ride_id = str(uuid.uuid4())
        Ride.objects.create(id=ride_id, rider=self.user, title="Repeated Completion", start_time=timezone.now())
        payload = self._create_batch_payload("b-0", 0, 0, 10)
        self.client.post(f'/api/v1/rides/{ride_id}/points/batch/', payload, format='json')
        complete_data = {
            'expected_points_count': 10,
            'expected_batches_count': 1
        }
        res1 = self.client.post(f'/api/v1/rides/{ride_id}/complete/', complete_data, format='json')
        self.assertEqual(res1.status_code, status.HTTP_200_OK)

        res2 = self.client.post(f'/api/v1/rides/{ride_id}/complete/', complete_data, format='json')
        self.assertEqual(res2.status_code, status.HTTP_200_OK)
        self.assertEqual(res2.data['status'], 'ALREADY_COMPLETED')

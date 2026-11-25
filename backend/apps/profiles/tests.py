from django.test import TestCase
from django.contrib.auth.models import User
from rest_framework.test import APIClient
from rest_framework import status
from apps.profiles.models import RiderProfile
from apps.garage.models import Motorcycle
from apps.rides.models import Ride, GpsPoint
from django.utils import timezone

class ProfileAndExportTests(TestCase):
    def setUp(self):
        self.client = APIClient()
        self.user = User.objects.create_user(
            username='export_rider',
            email='export@example.com',
            password='Password123!'
        )
        self.profile = RiderProfile.objects.create(
            user=self.user,
            display_name='Explorer',
            city='Pune',
            bio='Touring enthusiast'
        )
        self.bike = Motorcycle.objects.create(
            owner=self.user,
            brand='Triumph',
            model='Scrambler 400X',
            year=2024,
            engine_cc=398
        )
        self.ride = Ride.objects.create(
            rider=self.user,
            motorcycle=self.bike,
            title='Tamhini Ghat Cruise',
            start_time=timezone.now()
        )
        GpsPoint.objects.create(
            ride=self.ride,
            sequence_number=1,
            timestamp=timezone.now(),
            latitude=18.4715,
            longitude=73.4215,
            altitude=650.0,
            accuracy=4.0,
            speed_kmh=55.0
        )
        self.client.force_authenticate(user=self.user)

    def test_profile_update(self):
        payload = {
            'display_name': 'Explorer Updated',
            'city': 'Mumbai',
            'bio': 'Updated rider bio',
            'avatar_url': 'https://example.com/avatar.jpg'
        }
        response = self.client.patch('/api/v1/profile/me/', payload, format='json')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.profile.refresh_from_db()
        self.assertEqual(self.profile.display_name, 'Explorer Updated')
        self.assertEqual(self.profile.city, 'Mumbai')
        self.assertEqual(self.profile.avatar_url, 'https://example.com/avatar.jpg')

    def test_profile_data_export(self):
        response = self.client.get('/api/v1/profile/export/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        data = response.data
        self.assertIn('exported_at', data)
        self.assertEqual(data['user']['username'], 'export_rider')
        self.assertEqual(data['profile']['display_name'], 'Explorer')
        self.assertEqual(len(data['garage']), 1)
        self.assertEqual(data['garage'][0]['brand'], 'Triumph')
        self.assertEqual(len(data['rides']), 1)
        self.assertEqual(data['rides'][0]['title'], 'Tamhini Ghat Cruise')
        self.assertEqual(len(data['rides'][0]['points']), 1)

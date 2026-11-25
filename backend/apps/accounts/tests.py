from django.test import TestCase
from django.contrib.auth.models import User
from django.utils import timezone
from rest_framework.test import APIClient
from rest_framework import status
from apps.profiles.models import RiderProfile
from apps.garage.models import Motorcycle
from apps.rides.models import Ride

class AccountDeletionTests(TestCase):
    def setUp(self):
        self.client = APIClient()
        self.user = User.objects.create_user(
            username='delete_me',
            email='delete_me@example.com',
            password='Password123!'
        )
        self.user_id = self.user.id
        self.profile = RiderProfile.objects.create(
            user=self.user,
            display_name='Delete Candidate'
        )
        self.bike = Motorcycle.objects.create(
            owner=self.user,
            brand='KTM',
            model='Duke 390',
            year=2023,
            engine_cc=373
        )
        self.ride = Ride.objects.create(
            rider=self.user,
            motorcycle=self.bike,
            title='Last Ride',
            start_time=timezone.now()
        )
        self.client.force_authenticate(user=self.user)

    def test_in_app_account_deletion_cascades(self):
        response = self.client.delete('/api/v1/auth/delete-account/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(User.objects.filter(id=self.user_id).count(), 0)
        self.assertEqual(RiderProfile.objects.filter(display_name='Delete Candidate').count(), 0)
        self.assertEqual(Motorcycle.objects.filter(owner_id=self.user_id).count(), 0)
        self.assertEqual(Ride.objects.filter(rider_id=self.user_id).count(), 0)

    def test_web_deletion_request_get(self):
        response = self.client.get('/privacy/delete-account/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertContains(response, 'Account & Data Deletion Request')
        self.assertContains(response, 'DPDP')

    def test_web_deletion_request_post_existing_user(self):
        response = self.client.post('/privacy/delete-account/', {
            'username': 'delete_me@example.com',
            'reason': 'Moving to another platform'
        })
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertContains(response, 'Request Received')

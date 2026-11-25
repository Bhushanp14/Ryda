from django.test import TestCase
from django.contrib.auth.models import User
from django.utils import timezone
from datetime import timedelta
from rest_framework.test import APIClient
from rest_framework import status
from apps.clubs.models import Club, ClubMember, RidingEvent, EventRsvp

class ClubsApiTests(TestCase):
    def setUp(self):
        self.client = APIClient()
        self.user = User.objects.create_user(username='club_rider', email='cr@test.com', password='Password123!')
        self.club = Club.objects.create(name='Test Club', slug='test-club', city='Mumbai')
        self.event = RidingEvent.objects.create(
            club=self.club,
            title='Morning Rally',
            meeting_point='Toll Plaza',
            destination='Scenic Hill',
            start_time=timezone.now() + timedelta(days=2)
        )
        self.client.force_authenticate(user=self.user)

    def test_club_list_and_detail(self):
        res1 = self.client.get('/api/v1/clubs/')
        self.assertEqual(res1.status_code, status.HTTP_200_OK)
        self.assertGreaterEqual(len(res1.data), 1)

        res2 = self.client.get(f'/api/v1/clubs/{self.club.id}/')
        self.assertEqual(res2.status_code, status.HTTP_200_OK)
        self.assertEqual(res2.data['name'], 'Test Club')

    def test_club_join_toggle(self):
        res1 = self.client.post(f'/api/v1/clubs/{self.club.id}/join/')
        self.assertEqual(res1.status_code, status.HTTP_200_OK)
        self.assertTrue(res1.data['is_member'])

        res2 = self.client.post(f'/api/v1/clubs/{self.club.id}/join/')
        self.assertEqual(res2.status_code, status.HTTP_200_OK)
        self.assertFalse(res2.data['is_member'])

    def test_event_rsvp(self):
        res = self.client.post(f'/api/v1/clubs/events/{self.event.id}/rsvp/', {'status': 'GOING'}, format='json')
        self.assertEqual(res.status_code, status.HTTP_200_OK)
        self.assertEqual(res.data['my_rsvp'], 'GOING')
        self.assertEqual(res.data['confirmed_count'], 1)

    def test_create_club_without_slug(self):
        from apps.rides.models import Ride, RideStatus
        from apps.community.models import Post
        for i in range(10):
            r = Ride.objects.create(rider=self.user, title=f"Ride {i}", start_time=timezone.now(), status=RideStatus.COMPLETED, is_private=False)
        Post.objects.create(author=self.user, post_type='RIDE', ride=r, caption="Public Ride Post")

        res = self.client.post('/api/v1/clubs/', {
            'name': 'Western Ghats MC',
            'city': 'Pune',
            'description': 'Endurance touring club',
            'policy': 'OPEN'
        }, format='json')
        self.assertEqual(res.status_code, status.HTTP_201_CREATED)
        self.assertEqual(res.data['name'], 'Western Ghats MC')
        self.assertEqual(res.data['slug'], 'western-ghats-mc')
        self.assertEqual(res.data['city'], 'Pune')

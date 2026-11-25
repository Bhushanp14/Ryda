from django.test import TestCase
from django.contrib.auth.models import User
from rest_framework.test import APIClient
from rest_framework import status
from apps.notifications.models import Notification

class AchievementsAndNotificationsTests(TestCase):
    def setUp(self):
        self.client = APIClient()
        self.user = User.objects.create_user(username='badge_rider', email='br@test.com', password='Password123!')
        Notification.objects.create(user=self.user, title='Test Alert', message='You unlocked a badge', is_read=False)
        self.client.force_authenticate(user=self.user)

    def test_achievements_evaluation_and_list(self):
        res = self.client.get('/api/v1/achievements/')
        self.assertEqual(res.status_code, status.HTTP_200_OK)
        self.assertIn('achievements', res.data)
        self.assertGreaterEqual(len(res.data['achievements']), 1)

    def test_notifications_list_and_mark_read(self):
        res1 = self.client.get('/api/v1/notifications/')
        self.assertEqual(res1.status_code, status.HTTP_200_OK)
        self.assertEqual(res1.data['unread_count'], 1)

        res2 = self.client.post('/api/v1/notifications/mark-all-read/')
        self.assertEqual(res2.status_code, status.HTTP_200_OK)
        self.assertEqual(res2.data['unread_count'], 0)

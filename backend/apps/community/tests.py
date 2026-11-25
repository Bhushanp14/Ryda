from django.test import TestCase
from django.contrib.auth.models import User
from rest_framework.test import APIClient
from rest_framework import status
from apps.community.models import Post, PostLike, RiderFollow

class CommunityApiTests(TestCase):
    def setUp(self):
        self.client = APIClient()
        self.user1 = User.objects.create_user(username='rider1', email='r1@test.com', password='Password123!')
        self.user2 = User.objects.create_user(username='rider2', email='r2@test.com', password='Password123!')
        self.post1 = Post.objects.create(author=self.user2, caption='Ghat blast test')
        self.client.force_authenticate(user=self.user1)

    def test_feed_listing(self):
        response = self.client.get('/api/v1/community/feed/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertGreaterEqual(len(response.data), 1)

    def test_like_toggle(self):
        # Like
        res1 = self.client.post(f'/api/v1/community/posts/{self.post1.id}/like/')
        self.assertEqual(res1.status_code, status.HTTP_200_OK)
        self.assertTrue(res1.data['is_liked'])
        self.assertEqual(res1.data['likes_count'], 1)

        # Unlike
        res2 = self.client.post(f'/api/v1/community/posts/{self.post1.id}/like/')
        self.assertEqual(res2.status_code, status.HTTP_200_OK)
        self.assertFalse(res2.data['is_liked'])
        self.assertEqual(res2.data['likes_count'], 0)

    def test_follow_toggle(self):
        res1 = self.client.post(f'/api/v1/community/riders/{self.user2.id}/follow/')
        self.assertEqual(res1.status_code, status.HTTP_200_OK)
        self.assertTrue(res1.data['is_following'])

        res2 = self.client.post(f'/api/v1/community/riders/{self.user2.id}/follow/')
        self.assertEqual(res2.status_code, status.HTTP_200_OK)
        self.assertFalse(res2.data['is_following'])

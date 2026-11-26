from django.test import TestCase
from django.contrib.auth.models import User
from django.utils import timezone
from datetime import timedelta
from rest_framework.test import APIClient
from rest_framework import status
from apps.clubs.models import (
    Club, ClubMember, ClubRole, RidingEvent, EventRsvp,
    ClubRideSession, ClubRideParticipant, SessionStatus, ParticipantStatus,
)
from apps.clubs.management.commands.expire_club_rides import expire_overdue_sessions
from apps.rides.models import Ride, RideStatus

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


class ClubRideSessionApiTests(TestCase):
    def setUp(self):
        self.client = APIClient()
        self.leader = User.objects.create_user(username='leader_user', email='l@test.com', password='Password123!')
        self.member1 = User.objects.create_user(username='member1_user', email='m1@test.com', password='Password123!')
        self.member2 = User.objects.create_user(username='member2_user', email='m2@test.com', password='Password123!')
        self.outsider = User.objects.create_user(username='outsider_user', email='o@test.com', password='Password123!')

        self.club = Club.objects.create(name='Apex Riders', slug='apex-riders', city='Pune', created_by=self.leader)
        ClubMember.objects.create(club=self.club, user=self.leader, role=ClubRole.LEADER)
        ClubMember.objects.create(club=self.club, user=self.member1, role=ClubRole.MEMBER)
        ClubMember.objects.create(club=self.club, user=self.member2, role=ClubRole.MEMBER)

        self.future_start = timezone.now() + timedelta(hours=3)
        self.payload = {
            'title': 'Lonavala Dawn Run',
            'description': 'Morning ghat ride',
            'meeting_point': 'Chandni Chowk, Pune',
            'destination': 'Lonavala',
            'scheduled_start': self.future_start.isoformat(),
            'max_participants': 15,
            'waypoints': [
                {'lat': 18.7554, 'lng': 73.3762, 'label': 'Khandala Point'},
            ],
        }

    def _create_session(self):
        self.client.force_authenticate(user=self.leader)
        res = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/', self.payload, format='json')
        self.assertEqual(res.status_code, status.HTTP_201_CREATED)
        return res.data['id']

    def test_club_detail_exposes_my_role(self):
        self.client.force_authenticate(user=self.leader)
        res_leader = self.client.get(f'/api/v1/clubs/{self.club.id}/')
        self.assertEqual(res_leader.status_code, status.HTTP_200_OK)
        self.assertEqual(res_leader.data['my_role'], 'LEADER')

        self.client.force_authenticate(user=self.member1)
        res_member = self.client.get(f'/api/v1/clubs/{self.club.id}/')
        self.assertEqual(res_member.data['my_role'], 'MEMBER')

        self.client.force_authenticate(user=self.outsider)
        res_outsider = self.client.get(f'/api/v1/clubs/{self.club.id}/')
        self.assertIsNone(res_outsider.data['my_role'])

    def test_only_leader_can_create_and_edit_session(self):
        # Member cannot create
        self.client.force_authenticate(user=self.member1)
        res = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/', self.payload, format='json')
        self.assertEqual(res.status_code, status.HTTP_403_FORBIDDEN)

        # Leader creates
        session_id = self._create_session()
        session = ClubRideSession.objects.get(id=session_id)
        # 12-hour expiry rule enforced on save
        self.assertEqual(session.expires_at, session.scheduled_start + timedelta(hours=12))

        # Member cannot edit
        self.client.force_authenticate(user=self.member1)
        res_patch_m = self.client.patch(
            f'/api/v1/clubs/{self.club.id}/rides/{session_id}/',
            {'title': 'Hacked Title'},
            format='json',
        )
        self.assertEqual(res_patch_m.status_code, status.HTTP_403_FORBIDDEN)

        # Leader can edit while SCHEDULED
        self.client.force_authenticate(user=self.leader)
        res_patch_l = self.client.patch(
            f'/api/v1/clubs/{self.club.id}/rides/{session_id}/',
            {'title': 'Updated Dawn Run'},
            format='json',
        )
        self.assertEqual(res_patch_l.status_code, status.HTTP_200_OK)
        self.assertEqual(res_patch_l.data['title'], 'Updated Dawn Run')

    def test_full_lifecycle_join_ready_verify_start_complete(self):
        session_id = self._create_session()

        # Outsider cannot join
        self.client.force_authenticate(user=self.outsider)
        res_out = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/join/')
        self.assertEqual(res_out.status_code, status.HTTP_403_FORBIDDEN)

        # Member 1 and Member 2 join
        self.client.force_authenticate(user=self.member1)
        res_j1 = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/join/')
        self.assertEqual(res_j1.status_code, status.HTTP_200_OK)
        self.assertEqual(res_j1.data['my_status'], 'JOINED')

        # Duplicate join is idempotent (no duplicate participant)
        res_j1_dup = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/join/')
        self.assertEqual(res_j1_dup.status_code, status.HTTP_200_OK)
        self.assertEqual(ClubRideParticipant.objects.filter(session_id=session_id, user=self.member1).count(), 1)

        self.client.force_authenticate(user=self.member2)
        self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/join/')

        # Leader cannot verify member1 before member1 taps I'm Ready
        self.client.force_authenticate(user=self.leader)
        res_early_v = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/verify/{self.member1.id}/')
        self.assertEqual(res_early_v.status_code, status.HTTP_409_CONFLICT)

        # Cannot start session with 0 verified participants
        res_early_start = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/start/')
        self.assertEqual(res_early_start.status_code, status.HTTP_422_UNPROCESSABLE_ENTITY)

        # Member 1 taps I'm Ready
        self.client.force_authenticate(user=self.member1)
        res_r1 = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/ready/')
        self.assertEqual(res_r1.status_code, status.HTTP_200_OK)
        self.assertEqual(res_r1.data['my_status'], 'READY')

        # Non-leader cannot verify
        res_m_verify = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/verify/{self.member1.id}/')
        self.assertEqual(res_m_verify.status_code, status.HTTP_403_FORBIDDEN)

        # Leader verifies Member 1 (Member 2 remains JOINED, not verified)
        self.client.force_authenticate(user=self.leader)
        res_v1 = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/verify/{self.member1.id}/')
        self.assertEqual(res_v1.status_code, status.HTTP_200_OK)
        self.assertEqual(res_v1.data['status'], 'VERIFIED')

        # Verified member cannot leave
        self.client.force_authenticate(user=self.member1)
        res_leave_v = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/leave/')
        self.assertEqual(res_leave_v.status_code, status.HTTP_403_FORBIDDEN)

        # Member cannot see full participant list
        res_plist_m = self.client.get(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/participants/')
        self.assertEqual(res_plist_m.status_code, status.HTTP_403_FORBIDDEN)

        # Leader starts session
        self.client.force_authenticate(user=self.leader)
        res_start = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/start/')
        self.assertEqual(res_start.status_code, status.HTTP_200_OK)
        self.assertEqual(res_start.data['status'], 'ACTIVE')
        self.assertEqual(len(res_start.data['roster']), 1)
        self.assertEqual(res_start.data['roster'][0]['user_id'], str(self.member1.id))

        # Verify individual Ride created ONLY for verified member1, not unverified member2
        self.assertEqual(Ride.objects.filter(club_ride_session_id=session_id).count(), 1)
        m1_ride = Ride.objects.get(rider=self.member1, club_ride_session_id=session_id)
        self.assertEqual(m1_ride.title, 'Lonavala Dawn Run')
        self.assertEqual(m1_ride.status, RideStatus.RECORDING)
        self.assertFalse(Ride.objects.filter(rider=self.member2, club_ride_session_id=session_id).exists())

        # Prevent duplicate start
        res_dup_start = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/start/')
        self.assertEqual(res_dup_start.status_code, status.HTTP_409_CONFLICT)

        # Midway joining blocked once ACTIVE
        self.client.force_authenticate(user=self.leader)
        res_late_join = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/join/')
        self.assertEqual(res_late_join.status_code, status.HTTP_409_CONFLICT)

        # Non-leader cannot complete session
        self.client.force_authenticate(user=self.member1)
        res_member_complete = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/complete/')
        self.assertEqual(res_member_complete.status_code, status.HTTP_403_FORBIDDEN)

        # Complete session (leader)
        self.client.force_authenticate(user=self.leader)
        res_complete = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/complete/')
        self.assertEqual(res_complete.status_code, status.HTTP_200_OK)
        self.assertEqual(res_complete.data['status'], 'COMPLETED')

        # Completing an already-COMPLETED session is idempotent
        res_complete_again = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/complete/')
        self.assertEqual(res_complete_again.status_code, status.HTTP_200_OK)
        self.assertTrue(res_complete_again.data.get('already_completed'))
        # Participant's individual Ride record remains preserved
        self.assertTrue(Ride.objects.filter(rider=self.member1, club_ride_session_id=session_id).exists())

        # Cannot cancel after COMPLETED
        res_cancel_after = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/cancel/')
        self.assertEqual(res_cancel_after.status_code, status.HTTP_409_CONFLICT)

    def test_12_hour_expiry_enforcement(self):
        past_start = timezone.now() - timedelta(hours=13)
        session = ClubRideSession.objects.create(
            club=self.club,
            title='Expired Morning Ride',
            meeting_point='Gate 1',
            destination='Dam',
            scheduled_start=past_start,
            created_by=self.leader,
        )
        self.assertEqual(session.status, SessionStatus.SCHEDULED)
        self.assertTrue(session.is_expired_now)

        # Upcoming feed (?status=SCHEDULED) triggers expiry and excludes it
        self.client.force_authenticate(user=self.member1)
        res_list = self.client.get(f'/api/v1/clubs/{self.club.id}/rides/?status=SCHEDULED')
        self.assertEqual(res_list.status_code, status.HTTP_200_OK)
        self.assertEqual(len(res_list.data), 0)

        session.refresh_from_db()
        self.assertEqual(session.status, SessionStatus.EXPIRED)

    def test_management_command_expire_overdue_sessions(self):
        past_start = timezone.now() - timedelta(hours=14)
        session = ClubRideSession.objects.create(
            club=self.club,
            title='Overdue Ride',
            meeting_point='A',
            destination='B',
            scheduled_start=past_start,
            created_by=self.leader,
        )
        expired_count = expire_overdue_sessions()
        self.assertEqual(expired_count, 1)
        session.refresh_from_db()
        self.assertEqual(session.status, SessionStatus.EXPIRED)

    def test_cancel_scheduled_session(self):
        session_id = self._create_session()
        self.client.force_authenticate(user=self.leader)
        res = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/cancel/')
        self.assertEqual(res.status_code, status.HTTP_200_OK)
        self.assertEqual(res.data['status'], 'CANCELLED')

        # Join rejected on cancelled session
        self.client.force_authenticate(user=self.member1)
        res_j = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/join/')
        self.assertEqual(res_j.status_code, status.HTTP_409_CONFLICT)


from django.test import TransactionTestCase
from asgiref.sync import async_to_sync
from channels.testing import WebsocketCommunicator
from rest_framework.authtoken.models import Token
from config.asgi import application as asgi_application


class ClubRideWebSocketTests(TransactionTestCase):
    def setUp(self):
        self.leader = User.objects.create_user(username='ws_leader', password='Password123!')
        self.member1 = User.objects.create_user(username='ws_m1', password='Password123!')
        self.unverified = User.objects.create_user(username='ws_unverified', password='Password123!')

        self.leader_token = Token.objects.create(user=self.leader)
        self.m1_token = Token.objects.create(user=self.member1)
        self.unverified_token = Token.objects.create(user=self.unverified)

        self.club = Club.objects.create(name='WS Club', slug='ws-club', city='Mumbai', created_by=self.leader)
        ClubMember.objects.create(club=self.club, user=self.leader, role=ClubRole.LEADER)
        ClubMember.objects.create(club=self.club, user=self.member1, role=ClubRole.MEMBER)
        ClubMember.objects.create(club=self.club, user=self.unverified, role=ClubRole.MEMBER)

        self.session = ClubRideSession.objects.create(
            club=self.club,
            title='WS Active Ride',
            meeting_point='Start',
            destination='End',
            scheduled_start=timezone.now() + timedelta(hours=1),
            status=SessionStatus.ACTIVE,
            actual_start=timezone.now(),
            created_by=self.leader,
        )
        ClubRideParticipant.objects.create(session=self.session, user=self.leader, status=ParticipantStatus.VERIFIED)
        ClubRideParticipant.objects.create(session=self.session, user=self.member1, status=ParticipantStatus.VERIFIED)
        ClubRideParticipant.objects.create(session=self.session, user=self.unverified, status=ParticipantStatus.JOINED)

    def test_websocket_auth_and_location_fanout(self):
        async def run():
            # 1. Missing token rejected
            comm_no_auth = WebsocketCommunicator(
                asgi_application,
                f"/ws/club-rides/{self.session.id}/location/",
            )
            connected, _ = await comm_no_auth.connect()
            self.assertFalse(connected)

            # 2. Unverified participant rejected
            comm_unverified = WebsocketCommunicator(
                asgi_application,
                f"/ws/club-rides/{self.session.id}/location/?token={self.unverified_token.key}",
            )
            connected, _ = await comm_unverified.connect()
            self.assertFalse(connected)

            # 3. Two verified participants connect and exchange location
            comm_leader = WebsocketCommunicator(
                asgi_application,
                f"/ws/club-rides/{self.session.id}/location/?token={self.leader_token.key}",
            )
            comm_m1 = WebsocketCommunicator(
                asgi_application,
                f"/ws/club-rides/{self.session.id}/location/?token={self.m1_token.key}",
            )
            ok1, _ = await comm_leader.connect()
            ok2, _ = await comm_m1.connect()
            self.assertTrue(ok1)
            self.assertTrue(ok2)

            await comm_leader.send_json_to({
                'type': 'location_update',
                'lat': 18.7620,
                'lng': 73.3650,
                'speed_kmh': 68.5,
                'heading': 210.0,
            })

            msg = await comm_m1.receive_json_from(timeout=3)
            self.assertEqual(msg['type'], 'participant_location')
            self.assertEqual(msg['username'], 'ws_leader')
            self.assertAlmostEqual(msg['lat'], 18.7620)

            # Drain leader's own echo
            await comm_leader.receive_json_from(timeout=3)

            # 4. Sharing visibility pause notifies group
            await comm_leader.send_json_to({
                'type': 'sharing_visibility',
                'is_sharing': False,
            })
            pause_msg = await comm_m1.receive_json_from(timeout=3)
            self.assertEqual(pause_msg['type'], 'participant_sharing_paused')
            self.assertEqual(pause_msg['username'], 'ws_leader')

            await comm_leader.disconnect()
            await comm_m1.disconnect()

            # 5. Completed session rejects new WebSocket connections
            self.session.status = SessionStatus.COMPLETED
            await database_sync_to_async(self.session.save)()
            comm_completed = WebsocketCommunicator(
                asgi_application,
                f"/ws/club-rides/{self.session.id}/location/?token={self.leader_token.key}",
            )
            connected, _ = await comm_completed.connect()
            self.assertFalse(connected)

        from channels.db import database_sync_to_async
        async_to_sync(run)()


class ClubRideRoutePlannerTests(TestCase):
    def setUp(self):
        self.client = APIClient()
        self.leader = User.objects.create_user(username='route_leader', password='Password123!')
        self.member = User.objects.create_user(username='route_member', password='Password123!')
        self.club = Club.objects.create(name='Route Club', slug='route-club', city='Pune', created_by=self.leader)
        ClubMember.objects.create(club=self.club, user=self.leader, role=ClubRole.LEADER)
        ClubMember.objects.create(club=self.club, user=self.member, role=ClubRole.MEMBER)

    def test_route_compute_permissions_and_validation(self):
        # Member cannot compute route
        self.client.force_authenticate(user=self.member)
        res = self.client.post('/api/v1/clubs/routes/compute/', {
            'club_id': str(self.club.id),
            'origin': {'lat': 18.9894, 'lng': 73.1175},
            'destination': {'lat': 18.7502, 'lng': 73.4065},
        }, format='json')
        self.assertEqual(res.status_code, status.HTTP_403_FORBIDDEN)

        # Invalid coordinates rejected
        self.client.force_authenticate(user=self.leader)
        res_bad = self.client.post('/api/v1/clubs/routes/compute/', {
            'club_id': str(self.club.id),
            'origin': {'lat': 120.0, 'lng': 73.1175},
            'destination': {'lat': 18.7502, 'lng': 73.4065},
        }, format='json')
        self.assertEqual(res_bad.status_code, status.HTTP_400_BAD_REQUEST)

        # >20 intermediates rejected
        res_too_many = self.client.post('/api/v1/clubs/routes/compute/', {
            'club_id': str(self.club.id),
            'origin': {'lat': 18.9894, 'lng': 73.1175},
            'destination': {'lat': 18.7502, 'lng': 73.4065},
            'intermediates': [{'lat': 18.8, 'lng': 73.2}] * 21,
        }, format='json')
        self.assertEqual(res_too_many.status_code, status.HTTP_400_BAD_REQUEST)

    def test_route_compute_alternatives_and_ordered_pit_stops(self):
        from unittest.mock import patch
        from django.test import override_settings

        self.client.force_authenticate(user=self.leader)
        fake_google_resp = {
            'routes': [
                {
                    'description': 'NH 48 Express',
                    'distanceMeters': 45200,
                    'duration': '3420s',
                    'polyline': {'encodedPolyline': 'abc_poly_1'},
                    'routeLabels': ['DEFAULT_ROUTE'],
                },
                {
                    'description': 'Old Mumbai-Pune Hwy',
                    'distanceMeters': 48900,
                    'duration': '3900s',
                    'polyline': {'encodedPolyline': 'abc_poly_2'},
                    'routeLabels': ['DEFAULT_ROUTE_ALTERNATE'],
                },
            ]
        }

        with override_settings(GOOGLE_MAPS_API_KEY='test-key'):
            with patch('apps.clubs.route_views._call_google_routes_api', return_value=fake_google_resp) as mock_call:
                res = self.client.post('/api/v1/clubs/routes/compute/', {
                    'club_id': str(self.club.id),
                    'origin': {'lat': 18.9894, 'lng': 73.1175},
                    'destination': {'lat': 18.7502, 'lng': 73.4065},
                    'compute_alternatives': True,
                }, format='json')
                self.assertEqual(res.status_code, status.HTTP_200_OK)
                self.assertEqual(res.data['travel_mode'], 'TWO_WHEELER')
                self.assertIn('beta', res.data['beta_notice'].lower())
                self.assertEqual(len(res.data['routes']), 2)
                self.assertEqual(res.data['routes'][0]['encoded_polyline'], 'abc_poly_1')
                self.assertEqual(mock_call.call_args[0][1]['travelMode'], 'TWO_WHEELER')
                self.assertTrue(mock_call.call_args[0][1]['computeAlternativeRoutes'])

                # Reject DRIVE or BICYCLE — no car-route fallback
                res_drive = self.client.post('/api/v1/clubs/routes/compute/', {
                    'club_id': str(self.club.id),
                    'origin': {'lat': 18.9894, 'lng': 73.1175},
                    'destination': {'lat': 18.7502, 'lng': 73.4065},
                    'travel_mode': 'DRIVE',
                }, format='json')
                self.assertEqual(res_drive.status_code, status.HTTP_400_BAD_REQUEST)

                # With pit stops, order is preserved, TWO_WHEELER is preserved, and computeAlternativeRoutes is disabled
                res_stops = self.client.post('/api/v1/clubs/routes/compute/', {
                    'club_id': str(self.club.id),
                    'origin': {'lat': 18.9894, 'lng': 73.1175},
                    'destination': {'lat': 18.7502, 'lng': 73.4065},
                    'intermediates': [
                        {'lat': 18.9054, 'lng': 73.2355, 'label': 'Stop 1'},
                        {'lat': 18.8352, 'lng': 73.2981, 'label': 'Stop 2'},
                    ],
                }, format='json')
                self.assertEqual(res_stops.status_code, status.HTTP_200_OK)
                sent_payload = mock_call.call_args[0][1]
                self.assertEqual(sent_payload['travelMode'], 'TWO_WHEELER')
                self.assertFalse(sent_payload['computeAlternativeRoutes'])
                self.assertEqual(len(sent_payload['intermediates']), 2)
                self.assertAlmostEqual(
                    sent_payload['intermediates'][0]['location']['latLng']['latitude'], 18.9054
                )
                self.assertAlmostEqual(
                    sent_payload['intermediates'][1]['location']['latLng']['latitude'], 18.8352
                )

            # No motorcycle route available -> 404, no fallback to DRIVE
            with patch('apps.clubs.route_views._call_google_routes_api', return_value={'routes': []}) as mock_empty:
                res_empty = self.client.post('/api/v1/clubs/routes/compute/', {
                    'club_id': str(self.club.id),
                    'origin': {'lat': 18.9894, 'lng': 73.1175},
                    'destination': {'lat': 18.7502, 'lng': 73.4065},
                }, format='json')
                self.assertEqual(res_empty.status_code, status.HTTP_404_NOT_FOUND)
                self.assertIn('motorcycle', res_empty.data['detail'].lower())
                self.assertEqual(mock_empty.call_count, 1)

    def test_route_persistence_and_active_immutability(self):
        self.client.force_authenticate(user=self.leader)
        res = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/', {
            'title': 'Fixed Route Ghat Ride',
            'meeting_point': 'Panvel',
            'destination': 'Lonavala',
            'start_lat': 18.9894,
            'start_lng': 73.1175,
            'dest_lat': 18.7502,
            'dest_lng': 73.4065,
            'encoded_polyline': 'fixed_poly_xyz',
            'route_distance_meters': 45200,
            'route_duration_seconds': 3420,
            'route_summary': 'NH 48',
            'scheduled_start': (timezone.now() + timedelta(hours=2)).isoformat(),
            'waypoints': [
                {'lat': 18.9054, 'lng': 73.2355, 'label': 'Pit Stop 1'},
                {'lat': 18.8352, 'lng': 73.2981, 'label': 'Pit Stop 2'},
            ],
        }, format='json')
        self.assertEqual(res.status_code, status.HTTP_201_CREATED)
        session_id = res.data['id']
        self.assertEqual(res.data['encoded_polyline'], 'fixed_poly_xyz')
        self.assertEqual(res.data['route_distance_meters'], 45200)
        self.assertEqual(len(res.data['waypoints']), 2)

        # Join, ready, verify, start
        self.client.force_authenticate(user=self.member)
        self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/join/')
        self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/ready/')
        self.client.force_authenticate(user=self.leader)
        self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/verify/{self.member.id}/')
        res_start = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/start/')
        self.assertEqual(res_start.status_code, status.HTTP_200_OK)

        # Once ACTIVE, route fields cannot be modified
        res_patch = self.client.patch(
            f'/api/v1/clubs/{self.club.id}/rides/{session_id}/',
            {
                'encoded_polyline': 'tampered_poly',
                'dest_lat': 19.5,
                'waypoints': [],
            },
            format='json',
        )
        self.assertEqual(res_patch.status_code, status.HTTP_409_CONFLICT)
        session = ClubRideSession.objects.get(id=session_id)
        self.assertEqual(session.encoded_polyline, 'fixed_poly_xyz')
        self.assertEqual(len(session.waypoints), 2)

    def test_upcoming_and_previous_club_rides_sections(self):
        now = timezone.now()
        s_upcoming_later = ClubRideSession.objects.create(
            club=self.club, title='Later Ride', meeting_point='A', destination='B',
            scheduled_start=now + timedelta(hours=5), status=SessionStatus.SCHEDULED, created_by=self.leader
        )
        s_upcoming_soon = ClubRideSession.objects.create(
            club=self.club, title='Soon Ride', meeting_point='A', destination='B',
            scheduled_start=now + timedelta(hours=2), status=SessionStatus.SCHEDULED, created_by=self.leader
        )
        s_completed_older = ClubRideSession.objects.create(
            club=self.club, title='Old Completed', meeting_point='A', destination='B',
            scheduled_start=now - timedelta(days=3), status=SessionStatus.COMPLETED, created_by=self.leader
        )
        s_expired_newer = ClubRideSession.objects.create(
            club=self.club, title='Recent Expired', meeting_point='A', destination='B',
            scheduled_start=now - timedelta(days=1), status=SessionStatus.EXPIRED, created_by=self.leader
        )
        # CANCELLED sessions must NOT appear in upcoming and MUST appear in previous
        s_cancelled = ClubRideSession.objects.create(
            club=self.club, title='Cancelled Ride', meeting_point='A', destination='B',
            scheduled_start=now + timedelta(hours=3), status=SessionStatus.CANCELLED, created_by=self.leader
        )

        self.client.force_authenticate(user=self.member)
        res_up = self.client.get(f'/api/v1/clubs/{self.club.id}/rides/?section=upcoming')
        self.assertEqual(res_up.status_code, status.HTTP_200_OK)
        # Upcoming: SCHEDULED only, ascending; CANCELLED must not appear
        upcoming_ids = [r['id'] for r in res_up.data]
        self.assertEqual(upcoming_ids, [str(s_upcoming_soon.id), str(s_upcoming_later.id)])
        self.assertNotIn(str(s_cancelled.id), upcoming_ids)

        res_prev = self.client.get(f'/api/v1/clubs/{self.club.id}/rides/?section=previous')
        self.assertEqual(res_prev.status_code, status.HTTP_200_OK)
        # Previous: COMPLETED + EXPIRED + CANCELLED, descending by scheduled_start.
        # s_cancelled scheduled_start is 3 hours in the future (most recent), then
        # s_expired_newer (-1 day), then s_completed_older (-3 days).
        prev_ids = [r['id'] for r in res_prev.data]
        self.assertIn(str(s_expired_newer.id), prev_ids)
        self.assertIn(str(s_completed_older.id), prev_ids)
        self.assertIn(str(s_cancelled.id), prev_ids)
        # Ordering: cancelled (future start) > expired (-1d) > completed (-3d)
        self.assertEqual(prev_ids, [
            str(s_cancelled.id),
            str(s_expired_newer.id),
            str(s_completed_older.id),
        ])

        # Historical rides cannot be joined or started
        res_join_exp = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{s_expired_newer.id}/join/')
        self.assertEqual(res_join_exp.status_code, status.HTTP_409_CONFLICT)

        self.client.force_authenticate(user=self.leader)
        res_start_comp = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{s_completed_older.id}/start/')
        self.assertEqual(res_start_comp.status_code, status.HTTP_409_CONFLICT)

    def test_session_completed_preserves_individual_rides(self):
        """
        After a ClubRide session is completed, Ride objects linked to it via
        club_ride_session must remain intact (backend does NOT force-complete them —
        that is the Android device's responsibility).
        """
        from apps.rides.models import Ride, RideStatus
        now = timezone.now()

        # Create a SCHEDULED session
        self.client.force_authenticate(user=self.leader)
        res = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/', {
            'title': 'Preservation Test Ride',
            'meeting_point': 'Panvel',
            'destination': 'Lonavala',
            'origin_lat': 18.9894,
            'origin_lng': 73.1175,
            'dest_lat': 18.7502,
            'dest_lng': 73.4065,
            'encoded_polyline': 'poly_preserve',
            'route_distance_meters': 45000,
            'route_duration_seconds': 3000,
            'route_summary': 'NH 48',
            'scheduled_start': (now + timedelta(hours=1)).isoformat(),
        }, format='json')
        self.assertEqual(res.status_code, status.HTTP_201_CREATED)
        session_id = res.data['id']

        # Join + ready + verify + start
        self.client.force_authenticate(user=self.member)
        self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/join/')
        self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/ready/')
        self.client.force_authenticate(user=self.leader)
        self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/verify/{self.member.id}/')
        self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/start/')

        # Retrieve the actual session object
        session_obj = ClubRideSession.objects.get(id=session_id)

        # Simulate a linked individual Ride (as the Android device would create it)
        linked_ride = Ride.objects.create(
            rider=self.member,
            club_ride_session=session_obj,
            status=RideStatus.RECORDING,
            title='My Club Ride Recording',
            start_time=now,
        )

        # Leader completes the session
        res_complete = self.client.post(f'/api/v1/clubs/{self.club.id}/rides/{session_id}/complete/')
        self.assertEqual(res_complete.status_code, status.HTTP_200_OK)

        # The session is now COMPLETED
        session_obj.refresh_from_db()
        self.assertEqual(session_obj.status, SessionStatus.COMPLETED)

        # The individual Ride is NOT force-completed by the backend —
        # it remains RECORDING until the Android device finalizes it.
        linked_ride.refresh_from_db()
        self.assertEqual(linked_ride.status, RideStatus.RECORDING)
        self.assertEqual(linked_ride.club_ride_session_id, session_obj.id)

    def test_cancelled_sessions_in_previous_rides(self):
        """
        CANCELLED sessions must appear in ?section=previous and NOT in ?section=upcoming.
        """
        now = timezone.now()
        s_cancelled = ClubRideSession.objects.create(
            club=self.club, title='Cancelled Ride Only', meeting_point='A', destination='B',
            scheduled_start=now - timedelta(hours=1), status=SessionStatus.CANCELLED, created_by=self.leader
        )
        s_scheduled = ClubRideSession.objects.create(
            club=self.club, title='Still Upcoming', meeting_point='A', destination='B',
            scheduled_start=now + timedelta(hours=2), status=SessionStatus.SCHEDULED, created_by=self.leader
        )

        self.client.force_authenticate(user=self.member)

        res_up = self.client.get(f'/api/v1/clubs/{self.club.id}/rides/?section=upcoming')
        self.assertEqual(res_up.status_code, status.HTTP_200_OK)
        upcoming_ids = [r['id'] for r in res_up.data]
        self.assertIn(str(s_scheduled.id), upcoming_ids)
        self.assertNotIn(str(s_cancelled.id), upcoming_ids)

        res_prev = self.client.get(f'/api/v1/clubs/{self.club.id}/rides/?section=previous')
        self.assertEqual(res_prev.status_code, status.HTTP_200_OK)
        prev_ids = [r['id'] for r in res_prev.data]
        self.assertIn(str(s_cancelled.id), prev_ids)
        self.assertNotIn(str(s_scheduled.id), prev_ids)

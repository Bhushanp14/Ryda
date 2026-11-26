"""
Django Channels WebSocket consumer for Club Ride live location sharing.

Endpoint: ws://<host>/ws/club-rides/<session_id>/location/?token=<drf_token>

Authorization rules:
  - Valid DRF Token required (4001 if missing/invalid).
  - ClubRideSession must exist (4004) and be ACTIVE (4009).
  - User must be a VERIFIED ClubRideParticipant of the session (4003).

Storage:
  - Uses Redis (when REDIS_URL is configured) to store latest participant
    positions with TTL, falling back to an in-process store for local dev.
"""
import json
import time
from urllib.parse import parse_qs

from channels.db import database_sync_to_async
from channels.generic.websocket import AsyncWebsocketConsumer
from django.conf import settings
from django.utils import timezone
from rest_framework.authtoken.models import Token

from .models import ClubRideParticipant, ClubRideSession, ParticipantStatus, SessionStatus

# In-process fallback store when REDIS_URL is not configured:
# { session_id: { user_id_str: payload_dict } }
_LOCAL_POSITIONS = {}
_POSITION_TTL_SECONDS = 60


def _get_redis_client():
    redis_url = getattr(settings, 'REDIS_URL', '')
    if not redis_url:
        return None
    try:
        import redis.asyncio as aioredis
        return aioredis.from_url(redis_url, decode_responses=True)
    except Exception:
        return None


async def _store_position(session_id: str, user_id: int, payload: dict):
    client = _get_redis_client()
    if client is not None:
        try:
            key = f"club_ride:{session_id}:positions"
            await client.hset(key, str(user_id), json.dumps(payload))
            await client.expire(key, _POSITION_TTL_SECONDS * 10)
            await client.aclose()
            return
        except Exception:
            pass
    session_map = _LOCAL_POSITIONS.setdefault(session_id, {})
    session_map[str(user_id)] = {**payload, '_stored_at': time.time()}


async def _remove_position(session_id: str, user_id: int):
    client = _get_redis_client()
    if client is not None:
        try:
            await client.hdel(f"club_ride:{session_id}:positions", str(user_id))
            await client.aclose()
            return
        except Exception:
            pass
    session_map = _LOCAL_POSITIONS.get(session_id)
    if session_map:
        session_map.pop(str(user_id), None)


async def _get_all_positions(session_id: str) -> list:
    client = _get_redis_client()
    if client is not None:
        try:
            raw = await client.hgetall(f"club_ride:{session_id}:positions")
            await client.aclose()
            return [json.loads(v) for v in raw.values()]
        except Exception:
            pass
    now = time.time()
    session_map = _LOCAL_POSITIONS.get(session_id, {})
    fresh = []
    for uid, entry in list(session_map.items()):
        if now - entry.get('_stored_at', 0) <= _POSITION_TTL_SECONDS:
            clean = {k: v for k, v in entry.items() if k != '_stored_at'}
            fresh.append(clean)
        else:
            session_map.pop(uid, None)
    return fresh


class ClubRideLocationConsumer(AsyncWebsocketConsumer):

    async def connect(self):
        self.session_id = str(self.scope['url_route']['kwargs']['session_id'])
        self.group_name = f"club_ride_{self.session_id}"
        self.user = await self._authenticate()

        if self.user is None:
            await self.close(code=4001)
            return

        auth_check = await self._authorize_participant(self.session_id, self.user.id)
        if auth_check == 'NOT_FOUND':
            await self.close(code=4004)
            return
        if auth_check == 'NOT_ACTIVE':
            await self.close(code=4009)
            return
        if auth_check == 'NOT_VERIFIED':
            await self.close(code=4003)
            return

        await self.channel_layer.group_add(self.group_name, self.channel_name)
        await self.accept()

        # Send initial snapshot of active positions
        positions = await _get_all_positions(self.session_id)
        if positions:
            await self.send(text_data=json.dumps({
                'type': 'location_snapshot',
                'participants': positions,
            }))

    async def disconnect(self, close_code):
        if hasattr(self, 'group_name'):
            await self.channel_layer.group_discard(self.group_name, self.channel_name)

    async def receive(self, text_data=None, bytes_data=None):
        if not text_data or not getattr(self, 'user', None):
            return
        try:
            data = json.loads(text_data)
        except (ValueError, TypeError):
            return

        msg_type = data.get('type')
        if msg_type == 'location_update':
            is_sharing = bool(data.get('is_sharing', True))
            if not is_sharing:
                await _remove_position(self.session_id, self.user.id)
                await self.channel_layer.group_send(
                    self.group_name,
                    {
                        'type': 'broadcast_message',
                        'payload': {
                            'type': 'participant_sharing_paused',
                            'user_id': self.user.id,
                            'username': self.user.username,
                            'timestamp': timezone.now().isoformat(),
                        },
                    },
                )
                return

            try:
                lat = float(data['lat'])
                lng = float(data['lng'])
            except (KeyError, TypeError, ValueError):
                return

            payload = {
                'type': 'participant_location',
                'user_id': self.user.id,
                'username': self.user.username,
                'lat': lat,
                'lng': lng,
                'speed_kmh': float(data.get('speed_kmh', 0.0)),
                'heading': float(data.get('heading', 0.0)),
                'timestamp': data.get('timestamp') or timezone.now().isoformat(),
            }
            await _store_position(self.session_id, self.user.id, payload)
            await self.channel_layer.group_send(
                self.group_name,
                {
                    'type': 'broadcast_message',
                    'payload': payload,
                },
            )

        elif msg_type == 'sharing_visibility':
            is_sharing = bool(data.get('is_sharing', True))
            if not is_sharing:
                await _remove_position(self.session_id, self.user.id)
                await self.channel_layer.group_send(
                    self.group_name,
                    {
                        'type': 'broadcast_message',
                        'payload': {
                            'type': 'participant_sharing_paused',
                            'user_id': self.user.id,
                            'username': self.user.username,
                            'timestamp': timezone.now().isoformat(),
                        },
                    },
                )

    async def broadcast_message(self, event):
        await self.send(text_data=json.dumps(event['payload']))

    # ── Auth & DB helpers ──────────────────────────────────────────────────────

    @database_sync_to_async
    def _authenticate(self):
        query_string = self.scope.get('query_string', b'').decode('utf-8')
        params = parse_qs(query_string)
        token_key = params.get('token', [None])[0]

        if not token_key:
            headers = dict(self.scope.get('headers', []))
            auth_header = headers.get(b'authorization', b'').decode('utf-8')
            if auth_header.startswith('Token '):
                token_key = auth_header.split(' ', 1)[1].strip()

        if not token_key:
            return None
        try:
            return Token.objects.select_related('user').get(key=token_key).user
        except Token.DoesNotExist:
            return None

    @database_sync_to_async
    def _authorize_participant(self, session_id: str, user_id: int) -> str:
        try:
            session = ClubRideSession.objects.get(id=session_id)
        except ClubRideSession.DoesNotExist:
            return 'NOT_FOUND'
        if session.status != SessionStatus.ACTIVE:
            return 'NOT_ACTIVE'
        is_verified = ClubRideParticipant.objects.filter(
            session=session,
            user_id=user_id,
            status=ParticipantStatus.VERIFIED,
        ).exists()
        return 'OK' if is_verified else 'NOT_VERIFIED'

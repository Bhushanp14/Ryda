import os
import django

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'config.settings')
django.setup()

from django.contrib.auth.models import User
from django.utils import timezone
from datetime import timedelta
from apps.profiles.models import RiderProfile
from apps.garage.models import Motorcycle
from apps.clubs.models import Club, ClubMember, RidingEvent, EventRsvp
from apps.community.models import Post, PostLike, PostComment, RiderFollow
from apps.achievements.services import ensure_default_achievements
from apps.notifications.models import Notification, NotificationType

# Ensure default achievements exist
ensure_default_achievements()

# 1. Create or retrieve demo riders
aarav, _ = User.objects.get_or_create(username='aarav_sen', defaults={'email': 'aarav@example.com'})
aarav.set_password('Rider123!')
aarav.save()
aarav_prof, _ = RiderProfile.objects.get_or_create(user=aarav, defaults={'display_name': 'Aarav Sen', 'city': 'Pune', 'bio': 'Duke 390 corner carver'})
aarav_prof.avatar_url = 'https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150'
aarav_prof.save()

priya, _ = User.objects.get_or_create(username='priya_sharma', defaults={'email': 'priya@example.com'})
priya.set_password('Rider123!')
priya.save()
priya_prof, _ = RiderProfile.objects.get_or_create(user=priya, defaults={'display_name': 'Priya Sharma', 'city': 'Goa', 'bio': 'Coastal wanderer • CB350'})
priya_prof.avatar_url = 'https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150'
priya_prof.save()

kabir, _ = User.objects.get_or_create(username='kabir_rides', defaults={'email': 'kabir@example.com'})
kabir.set_password('Rider123!')
kabir.save()
kabir_prof, _ = RiderProfile.objects.get_or_create(user=kabir, defaults={'display_name': 'Kabir Mehta', 'city': 'Mumbai', 'bio': 'Himalayan 450 rider • Mumbai Chapter Leader'})
kabir_prof.avatar_url = 'https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150'
kabir_prof.save()

# Follows
RiderFollow.objects.get_or_create(follower=kabir, following=aarav)
RiderFollow.objects.get_or_create(follower=kabir, following=priya)

# 2. Seed Clubs
club_mumbai, _ = Club.objects.get_or_create(
    slug='mumbai-weekend-riders',
    defaults={
        'name': 'Mumbai Weekend Riders',
        'city': 'Mumbai',
        'description': 'Premier touring community covering Western Ghats, coastal Maharashtra, and monsoon expeditions.',
        'banner_url': 'https://images.unsplash.com/photo-1558981403-c5f9899a28bc?w=800',
        'logo_url': 'https://images.unsplash.com/photo-1558981403-c5f9899a28bc?w=200',
        'is_verified': True,
        'policy': 'OPEN',
        'total_mileage_km': 142000.0,
        'created_by': kabir
    }
)
ClubMember.objects.get_or_create(club=club_mumbai, user=kabir, defaults={'role': 'LEADER'})
ClubMember.objects.get_or_create(club=club_mumbai, user=aarav, defaults={'role': 'MEMBER'})

club_pune, _ = Club.objects.get_or_create(
    slug='pune-piston-heads',
    defaults={
        'name': 'Pune Piston Heads',
        'city': 'Pune',
        'description': 'Ghat road carvers, Tamhini dawn runs, and track day enthusiasts.',
        'banner_url': 'https://images.unsplash.com/photo-1568772585407-9361f9bf3a87?w=800',
        'logo_url': 'https://images.unsplash.com/photo-1568772585407-9361f9bf3a87?w=200',
        'is_verified': True,
        'policy': 'OPEN',
        'total_mileage_km': 98500.0,
        'created_by': aarav
    }
)
ClubMember.objects.get_or_create(club=club_pune, user=aarav, defaults={'role': 'LEADER'})

club_goa, _ = Club.objects.get_or_create(
    slug='goa-coastal-cruisers',
    defaults={
        'name': 'Goa Coastal Cruisers',
        'city': 'Goa',
        'description': 'Scenic beach highways, Konkan coastal passes, and relaxed weekend breakfast meets.',
        'banner_url': 'https://images.unsplash.com/photo-1558981806-ec527fa84c39?w=800',
        'logo_url': 'https://images.unsplash.com/photo-1558981806-ec527fa84c39?w=200',
        'is_verified': True,
        'policy': 'OPEN',
        'total_mileage_km': 64200.0,
        'created_by': priya
    }
)
ClubMember.objects.get_or_create(club=club_goa, user=priya, defaults={'role': 'LEADER'})

# 3. Seed Events
now = timezone.now()
event1, _ = RidingEvent.objects.get_or_create(
    club=club_mumbai,
    title='Dawn Patrol to Igatpuri Fog Valley',
    defaults={
        'description': 'Scenic group ride through Kasara Ghat morning mist. Intermediate touring pace, safety gear mandatory.',
        'meeting_point': 'Fountain Hotel, Ghodbunder Rd, Thane',
        'destination': 'Fog City Viewpoint, Igatpuri',
        'start_time': now + timedelta(days=4, hours=6),
        'rider_limit': 35,
        'pace': 'Intermediate',
        'distance_km': 135.0,
        'is_official': True,
        'created_by': kabir
    }
)
EventRsvp.objects.get_or_create(event=event1, user=kabir, defaults={'status': 'GOING'})
EventRsvp.objects.get_or_create(event=event1, user=aarav, defaults={'status': 'GOING'})

event2, _ = RidingEvent.objects.get_or_create(
    club=club_mumbai,
    title='Malshej Ghat Waterfall Odyssey',
    defaults={
        'description': 'Technical mountain cornering & high-altitude breakfast meet.',
        'meeting_point': 'Kalyan Bypass Food Hub',
        'destination': 'Malshej Ghat Top',
        'start_time': now + timedelta(days=18, hours=6),
        'rider_limit': 25,
        'pace': 'Technical',
        'distance_km': 240.0,
        'is_official': True,
        'created_by': kabir
    }
)
EventRsvp.objects.get_or_create(event=event2, user=priya, defaults={'status': 'INTERESTED'})

# 4. Seed Posts
post1, _ = Post.objects.get_or_create(
    author=aarav,
    caption='Early morning ghat blast with 4 companions. The new tarmac past Wai was exceptional! KTM Duke 390 had endless mid-range punch out of hairpins.',
    defaults={
        'post_type': 'RIDE',
        'image_url': 'https://images.unsplash.com/photo-1558981403-c5f9899a28bc?w=600'
    }
)
PostLike.objects.get_or_create(post=post1, user=kabir)
PostLike.objects.get_or_create(post=post1, user=priya)
PostComment.objects.get_or_create(post=post1, user=priya, defaults={'text': 'Road surface past Wai looks incredible! Need to hit this next weekend.'})

post2, _ = Post.objects.get_or_create(
    author=priya,
    caption='Sunrise coastal cruise along south Goa backwaters. Nothing clears the head like cool morning salt spray and open tarmac. 🌊🏍️',
    defaults={
        'post_type': 'THOUGHT',
        'image_url': 'https://images.unsplash.com/photo-1558981806-ec527fa84c39?w=600'
    }
)
PostLike.objects.get_or_create(post=post2, user=aarav)

# 5. Seed Notifications for Kabir
Notification.objects.get_or_create(
    user=kabir,
    title='New Achievement Unlocked!',
    defaults={
        'notification_type': NotificationType.ACHIEVEMENT,
        'message': "You earned 'State Explorer' for riding across 4 Maharashtra districts.",
        'is_read': False
    }
)
Notification.objects.get_or_create(
    user=kabir,
    title='Club Invite: Yezdi Riders Club',
    defaults={
        'notification_type': NotificationType.CLUB_INVITE,
        'message': 'Aarav Sen invited you to join the Western Chapter.',
        'is_read': False
    }
)
Notification.objects.get_or_create(
    user=kabir,
    title='Priya Sharma liked your ride',
    defaults={
        'notification_type': NotificationType.SOCIAL_LIKE,
        'message': '"Nashik to Igatpuri Morning Mist Run"',
        'is_read': True
    }
)

print("Successfully seeded Phase 5, 6, 7 community, club, event, and achievement data!")

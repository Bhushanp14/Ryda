from django.utils import timezone
from .models import Achievement, UserAchievement
from apps.rides.models import Ride, RideStatus
from apps.clubs.models import ClubMember
from apps.notifications.models import Notification, NotificationType

DEFAULT_ACHIEVEMENTS = [
    # ── Category A: Riding Milestones (6) ─────────────────────────────────
    {
        'key': 'ride_first',
        'category': 'Riding Milestones',
        'title': 'First Ride',
        'description': 'Complete 1 valid ride',
        'target_value': 1.0,
        'unit': 'ride',
        'icon_name': '01_ignition.svg',
        'order': 1
    },
    {
        'key': 'ride_5',
        'category': 'Riding Milestones',
        'title': 'Getting Started',
        'description': 'Complete 5 valid rides',
        'target_value': 5.0,
        'unit': 'rides',
        'icon_name': '02_finding_your_rhythm.svg',
        'order': 2
    },
    {
        'key': 'ride_25',
        'category': 'Riding Milestones',
        'title': 'Regular Rider',
        'description': 'Complete 25 valid rides',
        'target_value': 25.0,
        'unit': 'rides',
        'icon_name': '03_the_regular.svg',
        'order': 3
    },
    {
        'key': 'ride_50',
        'category': 'Riding Milestones',
        'title': 'Dedicated Rider',
        'description': 'Complete 50 valid rides',
        'target_value': 50.0,
        'unit': 'rides',
        'icon_name': '04_ride_devotee.svg',
        'order': 4
    },
    {
        'key': 'ride_100',
        'category': 'Riding Milestones',
        'title': 'Century Rider',
        'description': 'Complete 100 valid rides',
        'target_value': 100.0,
        'unit': 'rides',
        'icon_name': '05_the_centurion.svg',
        'order': 5
    },
    {
        'key': 'ride_250',
        'category': 'Riding Milestones',
        'title': 'Ride Collector',
        'description': 'Complete 250 valid rides',
        'target_value': 250.0,
        'unit': 'rides',
        'icon_name': '06_two_wheel_legend.svg',
        'order': 6
    },

    # ── Category B: Distance Milestones (6) ────────────────────────────────
    {
        'key': 'distance_50',
        'category': 'Distance Milestones',
        'title': 'First 50',
        'description': 'Reach 50 km cumulative distance',
        'target_value': 50.0,
        'unit': 'km',
        'icon_name': '07_first_fifty.svg',
        'order': 7
    },
    {
        'key': 'distance_100',
        'category': 'Distance Milestones',
        'title': '100 KM Club',
        'description': 'Reach 100 km cumulative distance',
        'target_value': 100.0,
        'unit': 'km',
        'icon_name': '08_the_100_club.svg',
        'order': 8
    },
    {
        'key': 'distance_500',
        'category': 'Distance Milestones',
        'title': 'Half a Thousand',
        'description': 'Reach 500 km cumulative distance',
        'target_value': 500.0,
        'unit': 'km',
        'icon_name': '09_five_hundred_strong.svg',
        'order': 9
    },
    {
        'key': 'distance_1000',
        'category': 'Distance Milestones',
        'title': 'Thousand Kilometres',
        'description': 'Reach 1,000 km cumulative distance',
        'target_value': 1000.0,
        'unit': 'km',
        'icon_name': '10_the_grand_thousand.svg',
        'order': 10
    },
    {
        'key': 'distance_5000',
        'category': 'Distance Milestones',
        'title': 'Five Thousand',
        'description': 'Reach 5,000 km cumulative distance',
        'target_value': 5000.0,
        'unit': 'km',
        'icon_name': '11_five_thousand_strong.svg',
        'order': 11
    },
    {
        'key': 'distance_10000',
        'category': 'Distance Milestones',
        'title': 'Ten Thousand',
        'description': 'Reach 10,000 km cumulative distance',
        'target_value': 10000.0,
        'unit': 'km',
        'icon_name': '12_ten_thousand_strong.svg',
        'order': 12
    },

    # ── Category C: Riding Experiences (6) ────────────────────────────────
    {
        'key': 'weekend_regular',
        'category': 'Riding Experiences',
        'title': 'Weekend Regular',
        'description': 'Complete rides on 5 distinct weekends',
        'target_value': 5.0,
        'unit': 'weekends',
        'icon_name': '13_weekend_regular.svg',
        'order': 13
    },
    {
        'key': 'season_rider',
        'category': 'Riding Experiences',
        'title': 'Season Rider',
        'description': 'Complete rides in 3 different calendar months',
        'target_value': 3.0,
        'unit': 'months',
        'icon_name': '14_season_rider.svg',
        'order': 14
    },
    {
        'key': 'back_on_road',
        'category': 'Riding Experiences',
        'title': 'Back on the Road',
        'description': 'Complete a ride after 30+ days inactive',
        'target_value': 1.0,
        'unit': 'milestone',
        'icon_name': '15_back_on_the_road.svg',
        'order': 15
    },
    {
        'key': 'two_wheel_stable',
        'category': 'Riding Experiences',
        'title': 'Two-Wheel Stable',
        'description': 'Complete rides using 2 registered bikes',
        'target_value': 2.0,
        'unit': 'bikes',
        'icon_name': '16_two_wheel_stable.svg',
        'order': 16
    },
    {
        'key': 'garage_explorer',
        'category': 'Riding Experiences',
        'title': 'Garage Explorer',
        'description': 'Complete rides using 3 registered bikes',
        'target_value': 3.0,
        'unit': 'bikes',
        'icon_name': '17_garage_explorer.svg',
        'order': 17
    },
    {
        'key': 'long_haul',
        'category': 'Riding Experiences',
        'title': 'Long Haul',
        'description': 'Complete one valid ride of at least 200 km',
        'target_value': 200.0,
        'unit': 'km',
        'icon_name': '18_long_haul.svg',
        'order': 18
    },

    # ── Category D: Community (6) ─────────────────────────────────────────
    {
        'key': 'community_first_post',
        'category': 'Community',
        'title': 'First Impression',
        'description': 'Publish your first community post',
        'target_value': 1.0,
        'unit': 'post',
        'icon_name': '19_first_impression.svg',
        'order': 19
    },
    {
        'key': 'community_storyteller',
        'category': 'Community',
        'title': 'Ride Storyteller',
        'description': 'Publish 10 community posts',
        'target_value': 10.0,
        'unit': 'posts',
        'icon_name': '20_ride_storyteller.svg',
        'order': 20
    },
    {
        'key': 'community_first_follow',
        'category': 'Community',
        'title': 'First Connection',
        'description': 'Follow another rider',
        'target_value': 1.0,
        'unit': 'follow',
        'icon_name': '21_first_connection.svg',
        'order': 21
    },
    {
        'key': 'community_comments',
        'category': 'Community',
        'title': 'Conversation Starter',
        'description': 'Publish 10 comments on other riders\' posts',
        'target_value': 10.0,
        'unit': 'comments',
        'icon_name': '22_conversation_starter.svg',
        'order': 22
    },
    {
        'key': 'community_voice',
        'category': 'Community',
        'title': 'Community Voice',
        'description': 'Receive appreciations from 25 distinct riders',
        'target_value': 25.0,
        'unit': 'riders',
        'icon_name': '23_community_voice.svg',
        'order': 23
    },
    {
        'key': 'community_ride_posts',
        'category': 'Community',
        'title': 'Shared Experience',
        'description': 'Publish 5 ride-linked posts',
        'target_value': 5.0,
        'unit': 'posts',
        'icon_name': '24_shared_experience.svg',
        'order': 24
    },

    # ── Category E: Clubs & Rallies (6) ───────────────────────────────────
    {
        'key': 'club_member',
        'category': 'Clubs & Rallies',
        'title': 'Club Member',
        'description': 'Join your first club',
        'target_value': 1.0,
        'unit': 'club',
        'icon_name': '25_club_member.svg',
        'order': 25
    },
    {
        'key': 'club_contributor',
        'category': 'Clubs & Rallies',
        'title': 'Club Contributor',
        'description': 'Participate in 3 verified club activities',
        'target_value': 3.0,
        'unit': 'activities',
        'icon_name': '26_club_contributor.svg',
        'order': 26
    },
    {
        'key': 'rally_debut',
        'category': 'Clubs & Rallies',
        'title': 'Rally Debut',
        'description': 'Attend your first rally',
        'target_value': 1.0,
        'unit': 'rally',
        'icon_name': '27_rally_debut.svg',
        'order': 27
    },
    {
        'key': 'rally_regular',
        'category': 'Clubs & Rallies',
        'title': 'Rally Regular',
        'description': 'Attend 5 verified rallies',
        'target_value': 5.0,
        'unit': 'rallies',
        'icon_name': '28_rally_regular.svg',
        'order': 28
    },
    {
        'key': 'club_founder',
        'category': 'Clubs & Rallies',
        'title': 'Club Founder',
        'description': 'Successfully create an approved club',
        'target_value': 1.0,
        'unit': 'club',
        'icon_name': '29_club_founder.svg',
        'order': 29
    },
    {
        'key': 'rally_host',
        'category': 'Clubs & Rallies',
        'title': 'Rally Host',
        'description': 'Host a completed rally with >=2 attendees',
        'target_value': 1.0,
        'unit': 'rally',
        'icon_name': '30_rally_host.svg',
        'order': 30
    }
]

def ensure_default_achievements():
    # Remove obsolete placeholder keys if they exist
    obsolete_keys = {'FIRST_MILESTONE', 'DISTANCE_5000KM', 'STATE_EXPLORER', 'RIDES_50', 'CLUB_CONTRIBUTOR', 'ODYSSEY_10000KM'}
    Achievement.objects.filter(key__in=obsolete_keys).delete()

    for item in DEFAULT_ACHIEVEMENTS:
        Achievement.objects.update_or_create(
            key=item['key'],
            defaults={
                'category': item['category'],
                'title': item['title'],
                'description': item['description'],
                'target_value': item['target_value'],
                'unit': item['unit'],
                'icon_name': item['icon_name'],
                'order': item['order']
            }
        )

def evaluate_user_achievements(user):
    ensure_default_achievements()
    achievements = Achievement.objects.all().order_by('order')

    completed_rides = list(Ride.objects.filter(rider=user, status=RideStatus.COMPLETED).order_by('start_time'))
    total_distance_m = sum(r.total_distance_meters for r in completed_rides)
    total_distance_km = total_distance_m / 1000.0
    total_rides_count = len(completed_rides)

    # Date-based calculations
    weekend_set = set()
    month_set = set()
    has_gap_30 = False
    motorcycles_used = set()
    max_single_distance_km = 0.0

    prev_time = None
    for ride in completed_rides:
        st = ride.start_time
        month_set.add((st.year, st.month))
        # ISO weekday: Monday is 1, Sunday is 7. Saturday=6, Sunday=7
        if st.isoweekday() in (6, 7):
            weekend_set.add((st.year, st.isocalendar()[1]))

        if prev_time is not None:
            diff_days = (st - prev_time).total_seconds() / 86400.0
            if diff_days >= 30:
                has_gap_30 = True
        prev_time = st

        if ride.motorcycle_id:
            motorcycles_used.add(ride.motorcycle_id)

        single_km = ride.total_distance_meters / 1000.0
        if single_km > max_single_distance_km:
            max_single_distance_km = single_km

    # Community metrics
    from apps.community.models import Post, PostComment, PostLike, RiderFollow
    posts = Post.objects.filter(author=user)
    post_count = posts.count()
    ride_posts_count = posts.filter(ride__isnull=False).count()
    comments_count = PostComment.objects.filter(user=user).count()
    following_count = RiderFollow.objects.filter(follower=user).count()
    appreciations_count = PostLike.objects.filter(post__author=user).count()

    # Clubs & rallies metrics
    from apps.clubs.models import Club, ClubMember, EventRsvp, RidingEvent, RsvpStatus
    joined_clubs = ClubMember.objects.filter(user=user).count()
    created_clubs = Club.objects.filter(created_by=user).count()
    rallies_going = EventRsvp.objects.filter(user=user, status=RsvpStatus.GOING).count()
    rallies_hosted = RidingEvent.objects.filter(created_by=user).count()

    metrics = {
        # Riding Milestones
        'ride_first': float(total_rides_count),
        'ride_5': float(total_rides_count),
        'ride_25': float(total_rides_count),
        'ride_50': float(total_rides_count),
        'ride_100': float(total_rides_count),
        'ride_250': float(total_rides_count),

        # Distance Milestones
        'distance_50': total_distance_km,
        'distance_100': total_distance_km,
        'distance_500': total_distance_km,
        'distance_1000': total_distance_km,
        'distance_5000': total_distance_km,
        'distance_10000': total_distance_km,

        # Riding Experiences
        'weekend_regular': float(len(weekend_set)),
        'season_rider': float(len(month_set)),
        'back_on_road': 1.0 if has_gap_30 else 0.0,
        'two_wheel_stable': float(len(motorcycles_used)),
        'garage_explorer': float(len(motorcycles_used)),
        'long_haul': max_single_distance_km,

        # Community
        'community_first_post': float(post_count),
        'community_storyteller': float(post_count),
        'community_first_follow': float(following_count),
        'community_comments': float(comments_count),
        'community_voice': float(appreciations_count),
        'community_ride_posts': float(ride_posts_count),

        # Clubs & Rallies
        'club_member': float(joined_clubs),
        'club_contributor': float(joined_clubs),
        'rally_debut': float(rallies_going),
        'rally_regular': float(rallies_going),
        'club_founder': float(created_clubs),
        'rally_host': float(rallies_hosted),
    }

    results = []
    for ach in achievements:
        user_ach, _ = UserAchievement.objects.get_or_create(user=user, achievement=ach)
        current_val = metrics.get(ach.key, 0.0)
        user_ach.progress_value = round(min(current_val, ach.target_value), 1)

        was_unlocked = user_ach.is_unlocked
        if current_val >= ach.target_value and not was_unlocked:
            user_ach.is_unlocked = True
            user_ach.unlocked_at = timezone.now()
            Notification.objects.create(
                user=user,
                notification_type=NotificationType.ACHIEVEMENT,
                title=f"New Achievement Unlocked: {ach.title}!",
                message=f"You earned '{ach.title}'! {ach.description}",
                related_id=ach.key
            )
        user_ach.save()
        results.append(user_ach)

    return results

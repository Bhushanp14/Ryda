"""
Management command to expire overdue SCHEDULED ClubRideSessions.
Intended to be run periodically (e.g., every 5 minutes via cron / systemd timer / task scheduler).

Usage:
    python manage.py expire_club_rides
"""
from django.core.management.base import BaseCommand
from django.utils import timezone
from apps.clubs.models import ClubRideSession, SessionStatus


def expire_overdue_sessions():
    """
    Finds all SCHEDULED ClubRideSession records whose expires_at <= now()
    and transitions them to EXPIRED.
    Returns the count of expired sessions.
    """
    now = timezone.now()
    count = ClubRideSession.objects.filter(
        status=SessionStatus.SCHEDULED,
        expires_at__lte=now,
    ).update(
        status=SessionStatus.EXPIRED,
        actual_end=now,
        updated_at=now,
    )
    return count


class Command(BaseCommand):
    help = "Expire SCHEDULED club ride sessions that are past their 12-hour expiry window."

    def handle(self, *args, **options):
        expired_count = expire_overdue_sessions()
        self.stdout.write(
            self.style.SUCCESS(f"Expired {expired_count} overdue club ride session(s).")
        )

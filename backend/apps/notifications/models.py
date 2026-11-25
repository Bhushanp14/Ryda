import uuid
from django.db import models
from django.contrib.auth.models import User

class NotificationType(models.TextChoices):
    ACHIEVEMENT = 'ACHIEVEMENT', 'Achievement'
    CLUB_INVITE = 'CLUB_INVITE', 'Club Invite'
    SOCIAL_LIKE = 'SOCIAL_LIKE', 'Social Like'
    EVENT = 'EVENT', 'Event'

class Notification(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='notifications')
    notification_type = models.CharField(max_length=20, choices=NotificationType.choices, default=NotificationType.ACHIEVEMENT)
    title = models.CharField(max_length=150)
    message = models.TextField()
    related_id = models.CharField(max_length=100, blank=True, default='')
    is_read = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)

    class Meta:
        app_label = 'notifications'
        ordering = ['-created_at']

    def __str__(self):
        return f"Notification for {self.user.username}: {self.title}"

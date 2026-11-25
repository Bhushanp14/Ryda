import uuid
from django.db import models
from django.contrib.auth.models import User

class AchievementCategory(models.TextChoices):
    RIDING_MILESTONES = 'Riding Milestones', 'Riding Milestones'
    DISTANCE_MILESTONES = 'Distance Milestones', 'Distance Milestones'
    RIDING_EXPERIENCES = 'Riding Experiences', 'Riding Experiences'
    COMMUNITY = 'Community', 'Community'
    CLUBS_RALLIES = 'Clubs & Rallies', 'Clubs & Rallies'

class Achievement(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    key = models.CharField(max_length=60, unique=True)
    category = models.CharField(max_length=50, default='Riding Milestones')
    title = models.CharField(max_length=120)
    description = models.TextField(blank=True, default='')
    target_value = models.FloatField(default=100.0)
    unit = models.CharField(max_length=30, default='KM')
    icon_name = models.CharField(max_length=50, default='military_tech')
    order = models.PositiveIntegerField(default=0)

    class Meta:
        app_label = 'achievements'
        ordering = ['order', 'title']

    def __str__(self):
        return f"{self.title} ({self.category})"

class UserAchievement(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(User, on_delete=models.CASCADE, related_name='user_achievements')
    achievement = models.ForeignKey(Achievement, on_delete=models.CASCADE, related_name='user_progress')
    progress_value = models.FloatField(default=0.0)
    is_unlocked = models.BooleanField(default=False)
    unlocked_at = models.DateTimeField(null=True, blank=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        app_label = 'achievements'
        unique_together = ('user', 'achievement')
        ordering = ['-is_unlocked', 'achievement__order']

    def __str__(self):
        return f"{self.user.username} - {self.achievement.title} ({'Unlocked' if self.is_unlocked else 'Locked'})"

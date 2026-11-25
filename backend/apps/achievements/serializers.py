from rest_framework import serializers
from .models import UserAchievement

class UserAchievementSerializer(serializers.ModelSerializer):
    key = serializers.CharField(source='achievement.key', read_only=True)
    category = serializers.CharField(source='achievement.category', read_only=True)
    title = serializers.CharField(source='achievement.title', read_only=True)
    description = serializers.CharField(source='achievement.description', read_only=True)
    target_value = serializers.FloatField(source='achievement.target_value', read_only=True)
    unit = serializers.CharField(source='achievement.unit', read_only=True)
    icon_name = serializers.CharField(source='achievement.icon_name', read_only=True)
    progress_percent = serializers.SerializerMethodField()

    class Meta:
        model = UserAchievement
        fields = [
            'id', 'key', 'category', 'title', 'description', 'target_value', 'unit', 'icon_name',
            'progress_value', 'is_unlocked', 'unlocked_at', 'progress_percent'
        ]

    def get_progress_percent(self, obj):
        if obj.is_unlocked:
            return 100
        if obj.achievement.target_value <= 0:
            return 0
        ratio = (obj.progress_value / obj.achievement.target_value) * 100.0
        return min(int(ratio), 100)

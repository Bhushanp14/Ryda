from django.contrib import admin
from .models import Achievement, UserAchievement

@admin.register(Achievement)
class AchievementAdmin(admin.ModelAdmin):
    list_display = ('order', 'title', 'key', 'category', 'target_value', 'unit', 'icon_name')
    list_filter = ('category',)
    search_fields = ('title', 'key', 'description')
    ordering = ('order', 'title')

@admin.register(UserAchievement)
class UserAchievementAdmin(admin.ModelAdmin):
    list_display = ('user', 'achievement', 'progress_value', 'is_unlocked', 'unlocked_at', 'updated_at')
    list_filter = ('is_unlocked', 'achievement__category', 'unlocked_at')
    search_fields = ('user__username', 'achievement__title', 'achievement__key')
    readonly_fields = ('updated_at',)

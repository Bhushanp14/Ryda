from django.contrib import admin
from .models import Club, ClubMember, RidingEvent, EventRsvp

class ClubMemberInline(admin.TabularInline):
    model = ClubMember
    extra = 0
    fields = ('user', 'role', 'joined_at')
    readonly_fields = ('joined_at',)

class EventRsvpInline(admin.TabularInline):
    model = EventRsvp
    extra = 0
    fields = ('user', 'status', 'created_at')
    readonly_fields = ('created_at',)

@admin.register(Club)
class ClubAdmin(admin.ModelAdmin):
    list_display = ('name', 'city', 'is_verified', 'policy', 'total_mileage_km', 'members_count', 'created_by', 'created_at')
    list_filter = ('is_verified', 'policy', 'city', 'created_at')
    search_fields = ('name', 'city', 'description', 'created_by__username')
    prepopulated_fields = {'slug': ('name',)}
    readonly_fields = ('created_at',)
    inlines = [ClubMemberInline]

    @admin.display(description='Members')
    def members_count(self, obj):
        return obj.members.count()

@admin.register(ClubMember)
class ClubMemberAdmin(admin.ModelAdmin):
    list_display = ('club', 'user', 'role', 'joined_at')
    list_filter = ('role', 'joined_at')
    search_fields = ('club__name', 'user__username', 'user__email')

@admin.register(RidingEvent)
class RidingEventAdmin(admin.ModelAdmin):
    list_display = (
        'title', 'club', 'start_time', 'meeting_point',
        'destination', 'pace', 'distance_km', 'rider_limit',
        'is_official', 'created_by', 'created_at'
    )
    list_filter = ('is_official', 'pace', 'start_time')
    search_fields = ('title', 'club__name', 'meeting_point', 'destination', 'created_by__username')
    readonly_fields = ('created_at',)
    inlines = [EventRsvpInline]

@admin.register(EventRsvp)
class EventRsvpAdmin(admin.ModelAdmin):
    list_display = ('event', 'user', 'status', 'created_at', 'updated_at')
    list_filter = ('status', 'created_at')
    search_fields = ('event__title', 'user__username')

from django.contrib import admin
from .models import Motorcycle

@admin.register(Motorcycle)
class MotorcycleAdmin(admin.ModelAdmin):
    list_display = (
        'nickname_or_model', 'owner', 'brand', 'model', 'variant',
        'year', 'engine_cc', 'is_primary', 'created_at'
    )
    list_filter = ('brand', 'is_primary', 'year', 'created_at')
    search_fields = ('owner__username', 'brand', 'model', 'nickname')
    readonly_fields = ('id', 'created_at', 'updated_at')

    @admin.display(description='Bike Name')
    def nickname_or_model(self, obj):
        return obj.nickname if obj.nickname else f"{obj.brand} {obj.model}"

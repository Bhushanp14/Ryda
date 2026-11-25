import uuid
from django.db import models
from django.contrib.auth.models import User

class Motorcycle(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    owner = models.ForeignKey(User, on_delete=models.CASCADE, related_name='motorcycles')
    brand = models.CharField(max_length=80)
    model = models.CharField(max_length=80)
    variant = models.CharField(max_length=80, blank=True, null=True)
    year = models.PositiveIntegerField(default=2024)
    engine_cc = models.PositiveIntegerField(default=450)
    nickname = models.CharField(max_length=80, blank=True, null=True)
    is_primary = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        ordering = ['-is_primary', 'brand', 'model']

    def save(self, *args, **kwargs):
        if self.is_primary:
            # Unset any other primary bike for this user
            Motorcycle.objects.filter(owner=self.owner, is_primary=True).exclude(id=self.id).update(is_primary=False)
        super().save(*args, **kwargs)

    def __str__(self):
        return f"{self.brand} {self.model} ({self.owner.username})"

    @property
    def displayName(self):
        return self.nickname or f"{self.brand} {self.model}".strip()

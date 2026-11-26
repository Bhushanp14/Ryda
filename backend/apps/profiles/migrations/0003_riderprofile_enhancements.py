# Generated for RYDA V1.1 Profile Enhancements

from django.db import migrations, models


class Migration(migrations.Migration):

    dependencies = [
        ('profiles', '0002_riderprofile_avatar_url'),
    ]

    operations = [
        migrations.AddField(
            model_name='riderprofile',
            name='longest_ride_km',
            field=models.FloatField(default=0.0),
        ),
        migrations.AddField(
            model_name='riderprofile',
            name='highest_monthly_distance_km',
            field=models.FloatField(default=0.0),
        ),
        migrations.AddField(
            model_name='riderprofile',
            name='most_rides_monthly',
            field=models.PositiveIntegerField(default=0),
        ),
        migrations.AddField(
            model_name='riderprofile',
            name='longest_duration_seconds',
            field=models.PositiveIntegerField(default=0),
        ),
        migrations.AddField(
            model_name='riderprofile',
            name='active_tag_ids',
            field=models.JSONField(blank=True, default=list),
        ),
        migrations.AddField(
            model_name='riderprofile',
            name='showcased_achievement_ids',
            field=models.JSONField(blank=True, default=list),
        ),
    ]

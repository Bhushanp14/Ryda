from rest_framework import serializers
from .models import Motorcycle

class MotorcycleSerializer(serializers.ModelSerializer):
    make = serializers.CharField(source='brand', required=False)

    class Meta:
        model = Motorcycle
        fields = (
            'id', 'brand', 'make', 'model', 'variant', 'year', 'engine_cc',
            'nickname', 'is_primary', 'created_at', 'updated_at'
        )
        read_only_fields = ('id', 'created_at', 'updated_at')
        extra_kwargs = {
            'brand': {'required': False}
        }

    def validate(self, attrs):
        if 'brand' not in attrs:
            make_val = self.initial_data.get('make') or self.initial_data.get('brand')
            if make_val:
                attrs['brand'] = make_val
            else:
                raise serializers.ValidationError({'brand': 'Brand or make is required.'})
        return attrs

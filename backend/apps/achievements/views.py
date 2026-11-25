from rest_framework import views, permissions, status
from rest_framework.response import Response
from .services import evaluate_user_achievements
from .serializers import UserAchievementSerializer

class UserAchievementsView(views.APIView):
    permission_classes = [permissions.IsAuthenticated]

    def get(self, request):
        user_achievements = evaluate_user_achievements(request.user)
        serializer = UserAchievementSerializer(user_achievements, many=True)
        unlocked_count = sum(1 for ua in user_achievements if ua.is_unlocked)
        total_count = len(user_achievements)
        return Response({
            'unlocked_count': unlocked_count,
            'total_count': total_count,
            'completion_rate_percent': int((unlocked_count / total_count * 100)) if total_count > 0 else 0,
            'achievements': serializer.data
        }, status=status.HTTP_200_OK)

from django.shortcuts import get_object_or_404
from django.contrib.auth.models import User
from rest_framework import views, generics, permissions, status
from rest_framework.response import Response
from .models import Post, PostLike, PostComment, RiderFollow
from .serializers import PostSerializer, PostCreateSerializer, PostCommentSerializer

class PostFeedView(views.APIView):
    permission_classes = [permissions.IsAuthenticatedOrReadOnly]

    def get(self, request):
        tab = request.query_params.get('tab', 'following').lower()
        if tab == 'following' and request.user.is_authenticated:
            following_user_ids = RiderFollow.objects.filter(follower=request.user).values_list('following_id', flat=True)
            # Include user's own posts and those they follow
            allowed_ids = list(following_user_ids) + [request.user.id]
            posts = Post.objects.filter(author_id__in=allowed_ids).select_related('author', 'ride', 'ride__motorcycle')
            # If following feed is empty, fall back to recent discover posts so feed is never blank
            if not posts.exists():
                posts = Post.objects.all().select_related('author', 'ride', 'ride__motorcycle')
        else:
            posts = Post.objects.all().select_related('author', 'ride', 'ride__motorcycle')

        serializer = PostSerializer(posts[:50], many=True, context={'request': request})
        return Response(serializer.data, status=status.HTTP_200_OK)

    def post(self, request):
        if not request.user.is_authenticated:
            return Response({'error': 'Authentication required'}, status=status.HTTP_401_UNAUTHORIZED)
        serializer = PostCreateSerializer(data=request.data)
        if serializer.is_valid():
            post = serializer.save(author=request.user)
            out = PostSerializer(post, context={'request': request}).data
            return Response(out, status=status.HTTP_201_CREATED)
        return Response(serializer.errors, status=status.HTTP_400_BAD_REQUEST)

class PostLikeToggleView(views.APIView):
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request, post_id):
        post = get_object_or_404(Post, id=post_id)
        like = PostLike.objects.filter(user=request.user, post=post).first()
        if like:
            like.delete()
            is_liked = False
        else:
            PostLike.objects.create(user=request.user, post=post)
            is_liked = True
        return Response({
            'post_id': str(post.id),
            'is_liked': is_liked,
            'likes_count': post.likes.count()
        }, status=status.HTTP_200_OK)

class PostCommentsView(views.APIView):
    permission_classes = [permissions.IsAuthenticatedOrReadOnly]

    def get(self, request, post_id):
        post = get_object_or_404(Post, id=post_id)
        comments = post.comments.all().select_related('user', 'user__rider_profile')
        serializer = PostCommentSerializer(comments, many=True)
        return Response(serializer.data, status=status.HTTP_200_OK)

    def post(self, request, post_id):
        if not request.user.is_authenticated:
            return Response({'error': 'Authentication required'}, status=status.HTTP_401_UNAUTHORIZED)
        post = get_object_or_404(Post, id=post_id)
        text = request.data.get('text', '').strip()
        if not text:
            return Response({'error': 'Comment text cannot be empty'}, status=status.HTTP_400_BAD_REQUEST)
        comment = PostComment.objects.create(user=request.user, post=post, text=text)
        serializer = PostCommentSerializer(comment)
        return Response(serializer.data, status=status.HTTP_201_CREATED)

class RiderFollowToggleView(views.APIView):
    permission_classes = [permissions.IsAuthenticated]

    def post(self, request, user_id):
        target_user = get_object_or_404(User, id=user_id)
        if target_user == request.user:
            return Response({'error': 'Cannot follow yourself'}, status=status.HTTP_400_BAD_REQUEST)

        follow = RiderFollow.objects.filter(follower=request.user, following=target_user).first()
        if follow:
            follow.delete()
            is_following = False
        else:
            RiderFollow.objects.create(follower=request.user, following=target_user)
            is_following = True

        return Response({
            'target_user_id': target_user.id,
            'is_following': is_following,
            'followers_count': target_user.followers.count()
        }, status=status.HTTP_200_OK)

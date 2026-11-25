from django.contrib import admin
from .models import Post, PostLike, PostComment, RiderFollow

class PostCommentInline(admin.TabularInline):
    model = PostComment
    extra = 0
    fields = ('user', 'text', 'created_at')
    readonly_fields = ('created_at',)

class PostLikeInline(admin.TabularInline):
    model = PostLike
    extra = 0
    fields = ('user', 'created_at')
    readonly_fields = ('created_at',)

@admin.register(Post)
class PostAdmin(admin.ModelAdmin):
    list_display = ('id', 'author', 'post_type', 'caption_preview', 'ride', 'likes_count', 'comments_count', 'created_at')
    list_filter = ('post_type', 'created_at')
    search_fields = ('author__username', 'caption')
    readonly_fields = ('created_at', 'updated_at')
    inlines = [PostLikeInline, PostCommentInline]

    @admin.display(description='Caption')
    def caption_preview(self, obj):
        return (obj.caption[:60] + '...') if len(obj.caption) > 60 else obj.caption

    @admin.display(description='Likes')
    def likes_count(self, obj):
        return obj.likes.count()

    @admin.display(description='Comments')
    def comments_count(self, obj):
        return obj.comments.count()

@admin.register(PostLike)
class PostLikeAdmin(admin.ModelAdmin):
    list_display = ('user', 'post', 'created_at')
    search_fields = ('user__username', 'post__id')

@admin.register(PostComment)
class PostCommentAdmin(admin.ModelAdmin):
    list_display = ('user', 'post', 'text_preview', 'created_at')
    search_fields = ('user__username', 'text', 'post__id')

    @admin.display(description='Comment')
    def text_preview(self, obj):
        return (obj.text[:60] + '...') if len(obj.text) > 60 else obj.text

@admin.register(RiderFollow)
class RiderFollowAdmin(admin.ModelAdmin):
    list_display = ('follower', 'following', 'created_at')
    search_fields = ('follower__username', 'following__username')

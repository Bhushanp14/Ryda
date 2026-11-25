package com.ryda.app.core.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NotificationItem(
    val id: String,
    @SerialName("notification_type") val notificationType: String,
    val title: String,
    val message: String,
    @SerialName("related_id") val relatedId: String = "",
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class NotificationsResponse(
    @SerialName("unread_count") val unreadCount: Int = 0,
    val notifications: List<NotificationItem> = emptyList()
)

package com.ryda.app.core.models

import java.util.UUID

enum class OutboxOperationType {
    CREATE,
    UPDATE,
    DELETE
}

enum class OutboxStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED
}

data class OutboxOperation(
    val id: String = UUID.randomUUID().toString(),
    val entityType: String, // RIDE, MOTORCYCLE, PROFILE, COMMUNITY_POST, CLUB_MEMBERSHIP, EVENT_RSVP
    val entityId: String,   // Local UUID
    val serverId: String? = null,
    val operationType: OutboxOperationType,
    val payloadJson: String = "{}",
    val timestamp: Long = System.currentTimeMillis(),
    val attemptCount: Int = 0,
    val lastError: String? = null,
    val status: OutboxStatus = OutboxStatus.PENDING
)

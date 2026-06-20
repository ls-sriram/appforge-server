package com.appforge.server.services.sharing.models

import com.appforge.server.infrastructure.time.AppTimestamp
import com.appforge.server.services.reviews.models.EntityCategory

data class ReviewerEntityShare(
    val id: String,
    val ownerId: String,
    val entityCategory: EntityCategory,
    val entityId: String,
    val reviewerEmail: String,
    val reviewerEmailNormalized: String,
    val createdBy: String,
    val expiresAt: AppTimestamp?,
    val createdAt: AppTimestamp,
    val revokedAt: AppTimestamp? = null,
    val revokedBy: String? = null,
)

enum class ReviewerShareStatus(val wire: String) {
    ACTIVE("active"),
    REVOKED("revoked"),
    EXPIRED("expired"),
}

data class ReviewerIdentity(
    val userId: String,
    val email: String,
    val displayName: String?,
)

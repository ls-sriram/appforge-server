package com.appforge.server.api.sharing

import com.appforge.server.api.ProtoTimestamp
import com.appforge.server.api.reviews.ReviewAnswerRequest
import kotlinx.serialization.Serializable

@Serializable
data class CreateReviewerShareRequest(
    val reviewerEmail: String,
)

@Serializable
data class ReviewerShareResponse(
    val id: String,
    val entityType: String,
    val entityId: String,
    val reviewerEmail: String,
    val status: String,
    val createdAt: ProtoTimestamp,
    val expiresAt: ProtoTimestamp? = null,
    val revokedAt: ProtoTimestamp? = null,
    val ownerUid: String? = null,
    val ownerName: String? = null,
    val ownerEmail: String? = null,
)

@Serializable
data class ReviewerShareEntityResponse(
    val share: ReviewerShareResponse,
    val entity: PublicEntity,
)

@Serializable
data class SubmitReviewerShareReviewRequest(
    val reviewFormId: String,
    val reviewFormVersion: Int,
    val answers: List<ReviewAnswerRequest> = emptyList(),
)

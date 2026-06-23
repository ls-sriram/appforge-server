package com.appforge.server.api.sharing

import com.appforge.server.api.ProtoTimestamp
import com.appforge.server.api.reviews.ReviewAnswerRequest
import kotlinx.serialization.Serializable

@Serializable
data class CreateCollaboratorShareRequest(
    val collaboratorEmail: String,
)

@Serializable
data class CollaboratorShareResponse(
    val id: String,
    val entityType: String,
    val entityId: String,
    val collaboratorEmail: String,
    val status: String,
    val createdAt: ProtoTimestamp,
    val expiresAt: ProtoTimestamp? = null,
    val revokedAt: ProtoTimestamp? = null,
    val ownerUid: String? = null,
    val ownerName: String? = null,
    val ownerEmail: String? = null,
)

@Serializable
data class CollaboratorShareEntityResponse(
    val share: CollaboratorShareResponse,
    val entity: PublicEntity,
)

@Serializable
data class SubmitCollaboratorReviewRequest(
    val reviewFormId: String,
    val reviewFormVersion: Int,
    val answers: List<ReviewAnswerRequest> = emptyList(),
)

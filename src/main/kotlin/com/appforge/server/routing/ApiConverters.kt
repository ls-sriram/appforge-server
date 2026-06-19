package com.appforge.server.routing

import com.appforge.server.api.reviews.ReviewResponse
import com.appforge.server.api.sharing.ShareResponse
import com.appforge.server.api.sharing.ShareSummaryResponse
import com.appforge.server.infrastructure.time.instantToProtoTimestamp
import com.appforge.server.services.reviews.models.Review
import com.appforge.server.services.sharing.models.Share

object ApiConverters {
    private const val WEB_PUBLIC_PREFIX = "/web"

    fun toReviewResponse(review: Review): ReviewResponse =
        ReviewResponse(
            id = review.id,
            authorRole = review.authorRole.wire,
            authorId = review.authorId,
            authorName = review.authorName,
            authorEmail = review.authorEmail,
            content = review.content.filterValues { it != null }.mapValues { it.value.toString() },
            createdAtTimestamp = review.createdAt.toEpochMilli()
        )

    fun toShareResponse(share: Share, baseUrl: String): ShareResponse =
        ShareResponse(
            id = share.id,
            entityType = share.entityCategory.value,
            entityId = share.entityId,
            shareUrl = "$baseUrl$WEB_PUBLIC_PREFIX/shares/${share.token}",
            expiresAt = instantToProtoTimestamp(share.expiresAt) ?: error("share.expiresAt is required"),
        )

    fun toShareSummaryResponse(share: Share, baseUrl: String): ShareSummaryResponse =
        ShareSummaryResponse(
            id = share.id,
            entityType = share.entityCategory.value,
            entityId = share.entityId,
            shareUrl = "$baseUrl$WEB_PUBLIC_PREFIX/shares/${share.token}",
            expiresAt = instantToProtoTimestamp(share.expiresAt) ?: error("share.expiresAt is required"),
            revokedAt = instantToProtoTimestamp(share.revokedAt),
        )
}

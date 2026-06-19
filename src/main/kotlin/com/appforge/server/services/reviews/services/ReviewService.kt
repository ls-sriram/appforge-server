package com.appforge.server.services.reviews.services

import com.appforge.server.infrastructure.Resource
import com.appforge.server.services.reviews.ai.AIReviewWorker
import com.appforge.server.services.reviews.models.EntityCategory
import com.appforge.server.services.reviews.models.Review
import com.appforge.server.services.reviews.models.ReviewAuthorRole
import com.appforge.server.services.reviews.repository.ProfileRepository
import com.appforge.server.services.reviews.repository.ProfileRepositoryApi
import com.appforge.server.services.reviews.repository.ReviewRepository
import com.appforge.server.services.reviews.repository.ReviewRepositoryApi
import com.appforge.server.providers.identifier.IdentifierProvider
import java.time.Clock
import com.appforge.server.infrastructure.time.*

class ReviewService(
    private val reviewRepository: ReviewRepositoryApi,
    private val profileRepository: ProfileRepositoryApi,
    private val aiReviewWorker: AIReviewWorker,
    private val clock: Clock = Clock.systemUTC(),
) {
    suspend fun getReviews(
        userId: String,
        category: EntityCategory,
        entityId: String
    ): Resource<List<Review>> {
        return reviewRepository.getReviewsForEntity(userId, category.value, entityId)
    }

    suspend fun listAllReviews(userId: String): Resource<List<Review>> {
        return reviewRepository.listAllReviews(userId)
    }

    fun requestAIReview(
        userId: String,
        category: EntityCategory,
        entityId: String,
        versionId: String? = null
    ) {
        aiReviewWorker.enqueueAIReview(userId, category, entityId, versionId)
    }

    suspend fun submitExternalReview(
        userId: String,
        entityCategory: EntityCategory,
        entityId: String,
        displayName: String,
        content: Map<String, Any?>
    ): Resource<Review> {
        val reviewId = IdentifierProvider.newUuid()
        val review = Review(
            id = reviewId,
            entityId = entityId,
            entityCategory = entityCategory,
            authorRole = ReviewAuthorRole.EXTERNAL,
            authorId = null,
            authorName = displayName,
            authorEmail = null,
            content = content,
            createdAt = clock.nowTimestamp()
        )

        return when (val res = reviewRepository.create(userId, entityCategory.value, entityId, review)) {
            is Resource.Success -> Resource.Success(review)
            is Resource.Error -> Resource.Error(res.exception)
            Resource.Loading -> Resource.Loading
        }
    }
}

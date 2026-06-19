package com.appforge.server.services.reviews

import com.appforge.server.api.reviews.ReviewResponse
import com.appforge.server.infrastructure.Resource
import com.appforge.server.routing.ApiConverters
import com.appforge.server.services.reviews.models.EntityCategory
import com.appforge.server.services.reviews.services.ReviewService

interface ReviewUseCases {
    suspend fun listAllReviews(userId: String): List<ReviewResponse>
    suspend fun getReviews(userId: String, type: String, entityId: String): List<ReviewResponse>
    suspend fun requestAiReview(userId: String, type: String, entityId: String, versionId: String?)
}

class ReviewUseCasesImpl(
    private val reviewService: ReviewService,
) : ReviewUseCases {
    override suspend fun listAllReviews(userId: String): List<ReviewResponse> {
        return when (val result = reviewService.listAllReviews(userId)) {
            is Resource.Success -> result.data.map { ApiConverters.toReviewResponse(it) }
            is Resource.Error -> throw IllegalStateException(result.exception.message ?: "Error")
            else -> emptyList()
        }
    }

    override suspend fun getReviews(userId: String, type: String, entityId: String): List<ReviewResponse> {
        val category = EntityCategory(type)
        return when (val result = reviewService.getReviews(userId, category, entityId)) {
            is Resource.Success -> result.data.map { ApiConverters.toReviewResponse(it) }
            is Resource.Error -> throw IllegalStateException(result.exception.message ?: "Error")
            else -> emptyList()
        }
    }

    override suspend fun requestAiReview(userId: String, type: String, entityId: String, versionId: String?) {
        val category = EntityCategory(type.takeIf { it.isNotBlank() }
            ?: throw IllegalArgumentException("Category type must not be empty"))
        reviewService.requestAIReview(userId, category, entityId, versionId)
    }
}

package com.appforge.server.services.reviews.ai

import com.appforge.server.infrastructure.Resource
import com.appforge.server.services.reviews.models.EntityCategory
import com.appforge.server.services.reviews.models.Review
import com.appforge.server.services.reviews.models.ReviewAuthorRole
import com.appforge.server.services.reviews.repository.ReviewRepository
import com.appforge.server.services.reviews.repository.ReviewRepositoryApi
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Clock
import com.appforge.server.infrastructure.time.*

class AIReviewWorker(
    private val reviewRepository: ReviewRepositoryApi,
    private val pipelineFactory: ReviewPipelineFactory,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val scope = CoroutineScope(dispatcher + SupervisorJob())

    fun enqueueAIReview(
        userId: String,
        category: EntityCategory,
        entityId: String,
        versionId: String? = null
    ) {
        scope.launch {
            try {
                val pipeline = pipelineFactory.getPipeline(category)
                val content = pipeline.generateReview(userId, entityId, versionId)

                val aiReviewId = com.appforge.server.providers.identifier.IdentifierProvider.newUuid()
                val aiReview = Review(
                    id = aiReviewId,
                    entityId = entityId,
                    entityCategory = category,
                    authorRole = ReviewAuthorRole.AI,
                    authorId = "ai",
                    authorName = "AI Review Assistant",
                    authorEmail = null,
                    content = content,
                    createdAt = clock.nowTimestamp()
                )

                reviewRepository.create(userId, category.value, entityId, aiReview)
            } catch (e: Exception) {
                println("Error generating AI review for $entityId: ${e.message}")
            }
        }
    }
}

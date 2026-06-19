package com.appforge.server.services.reviews.ai

import com.appforge.server.infrastructure.Resource
import com.appforge.server.services.reviews.models.EntityCategory
import com.appforge.server.services.reviews.models.ReviewAuthorRole
import com.appforge.server.services.reviews.repository.ReviewRepositoryApi
import io.mockk.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class AIReviewWorkerTest {

    @Test
    fun `enqueueAIReview selects correct pipeline and creates review`() = runBlocking {
        val reviewRepo = mockk<ReviewRepositoryApi>()
        val pipelineFactory = mockk<ReviewPipelineFactory>()
        val pipeline = mockk<ReviewPipeline>()
        
        // Use Unconfined dispatcher to execute launch block immediately in the same thread
        val worker = AIReviewWorker(reviewRepo, pipelineFactory, Dispatchers.Unconfined)

        val entityId = "e123"
        val userId = "u456"
        val category = EntityCategory("document")
        val mockContent = mapOf("ai_feedback" to "Excellent Statement")

        every { pipelineFactory.getPipeline(category) } returns pipeline
        coEvery { pipeline.generateReview(userId, entityId) } returns mockContent
        coEvery { reviewRepo.create(any(), any(), any(), any()) } returns Resource.Success("ai_statement_e123")

        worker.enqueueAIReview(userId, category, entityId)

        coVerify {
            pipelineFactory.getPipeline(category)
            pipeline.generateReview(userId, entityId)
            reviewRepo.create(
                userId,
                "document",
                entityId,
                match {
                    it.entityId == entityId &&
                    it.authorRole == ReviewAuthorRole.AI &&
                    it.authorName == "AI Review Assistant" &&
                    it.content == mockContent
                }
            )
        }
    }
}

package com.appforge.server.services.reviews.services

import com.appforge.server.infrastructure.Resource
import com.appforge.server.services.reviews.ai.AIReviewWorker
import com.appforge.server.services.reviews.models.EntityCategory
import com.appforge.server.services.reviews.models.Review
import com.appforge.server.services.reviews.models.ReviewAuthorRole
import com.appforge.server.services.reviews.repository.ProfileRepositoryApi
import com.appforge.server.services.reviews.repository.ReviewRepositoryApi
import io.mockk.*
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ReviewServiceTest {

    @Test
    fun `getReviews calls repository with resolved collection`() = runBlocking {
        val reviewRepo = mockk<ReviewRepositoryApi>()
        val profileRepo = mockk<ProfileRepositoryApi>()
        val aiWorker = mockk<AIReviewWorker>()
        val service = ReviewService(reviewRepo, profileRepo, aiWorker)

        val mockReviews = listOf(
            Review("r1", "e1", EntityCategory("document"), ReviewAuthorRole.AI, "ai", "AI Assistant", null, emptyMap(), Instant.now())
        )

        coEvery {
            reviewRepo.getReviewsForEntity("user1", "document", "e1")
        } returns Resource.Success(mockReviews)

        val result = service.getReviews("user1", EntityCategory("document"), "e1")

        assertTrue(result is Resource.Success)
        assertEquals(mockReviews, result.data)
        coVerify { reviewRepo.getReviewsForEntity("user1", "document", "e1") }
    }

    @Test
    fun `submitExternalReview creates external review without email`() = runBlocking {
        val reviewRepo = mockk<ReviewRepositoryApi>()
        val profileRepo = mockk<ProfileRepositoryApi>()
        val aiWorker = mockk<AIReviewWorker>()
        val service = ReviewService(reviewRepo, profileRepo, aiWorker)

        coEvery { reviewRepo.create(any(), any(), any(), any()) } returns Resource.Success("review123")

        val result = service.submitExternalReview(
            userId = "user1",
            entityCategory = EntityCategory("document"),
            entityId = "e1",
            displayName = "John Doe",
            content = mapOf("feedback" to "Great job")
        )

        assertTrue(result is Resource.Success)
        val review = result.data
        assertEquals("e1", review.entityId)
        assertEquals(ReviewAuthorRole.EXTERNAL, review.authorRole)
        assertEquals(null, review.authorId)
        assertEquals(null, review.authorEmail)

        coVerify {
            reviewRepo.create("user1", "document", "e1", match { it.authorRole == ReviewAuthorRole.EXTERNAL })
        }
        coVerify(exactly = 0) { profileRepo.upsert(any()) }
    }

    @Test
    fun `requestAIReview enqueues task in worker`() {
        val reviewRepo = mockk<ReviewRepositoryApi>()
        val profileRepo = mockk<ProfileRepositoryApi>()
        val aiWorker = mockk<AIReviewWorker>()
        val service = ReviewService(reviewRepo, profileRepo, aiWorker)

        every { aiWorker.enqueueAIReview(any(), any(), any()) } just runs

        service.requestAIReview("user1", EntityCategory("document"), "e1")

        verify { aiWorker.enqueueAIReview("user1", EntityCategory("document"), "e1") }
    }
}

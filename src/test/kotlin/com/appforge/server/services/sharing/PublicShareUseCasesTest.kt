package com.appforge.server.services.sharing

import com.appforge.server.infrastructure.Resource
import com.appforge.server.services.forms.repository.FormRepositoryApi
import com.appforge.server.services.recordings.repository.RecordingRepository
import com.appforge.server.services.reviews.ai.ReviewContentResolver
import com.appforge.server.services.reviews.models.EntityCategory
import com.appforge.server.services.reviews.services.ReviewService
import com.appforge.server.services.sharing.models.Share
import com.appforge.server.services.sharing.models.ShareAccessMode
import com.appforge.server.services.sharing.repository.ShareEntityRepositoryApi
import com.appforge.server.services.sharing.services.ShareService
import com.appforge.server.services.uploads.SignedGetUrlIssuer
import com.appforge.server.services.uploads.UploadMetadataRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PublicShareUseCasesTest {
    @Test
    fun `getPublicShare falls back to descriptor content when resolver fails`() = runBlocking {
        val shareService = mockk<ShareService>()
        val reviewService = mockk<ReviewService>(relaxed = true)
        val contentResolver = mockk<ReviewContentResolver>()
        val shareEntityRepository = mockk<ShareEntityRepositoryApi>()
        val formRepository = mockk<FormRepositoryApi>(relaxed = true)
        val uploadMetadataRepository = mockk<UploadMetadataRepository>(relaxed = true)
        val signedGetUrlIssuer = mockk<SignedGetUrlIssuer>(relaxed = true)
        val recordingRepository = mockk<RecordingRepository>(relaxed = true)
        val useCases = PublicShareUseCasesImpl(
            shareService = shareService,
            reviewService = reviewService,
            contentResolver = contentResolver,
            shareEntityRepository = shareEntityRepository,
            formRepository = formRepository,
            uploadMetadataRepository = uploadMetadataRepository,
            signedGetUrlIssuer = signedGetUrlIssuer,
            uploadExpirySeconds = 60,
            recordingRepository = recordingRepository,
        )

        val share = Share(
            id = "token",
            token = "token",
            entityId = "entity-1",
            entityCategory = EntityCategory("statement"),
            accessMode = ShareAccessMode.PUBLIC_LINK,
            ownerId = "user-1",
            tokenHash = "hash",
            expiresAt = Instant.parse("2026-07-01T00:00:00Z"),
            createdAt = Instant.parse("2026-06-01T00:00:00Z"),
            createdBy = "user-1",
        )

        coEvery { shareService.getAndValidateShare("token") } returns Resource.Success(share)
        coEvery {
            shareEntityRepository.getEntityDoc("user-1", EntityCategory("statement"), "entity-1")
        } returns Resource.Success(
            mapOf(
                "title" to "Shared entity",
                "content" to "Descriptor content",
            )
        )
        coEvery {
            contentResolver.resolveText("user-1", EntityCategory("statement"), "entity-1", null)
        } throws IllegalArgumentException("No text content resolver registered")

        val response = useCases.getPublicShare("token")

        assertEquals("Shared entity", response.entity.title)
        assertEquals("Descriptor content", response.entity.content)
        assertNull(response.entity.assetUrl)
    }
}

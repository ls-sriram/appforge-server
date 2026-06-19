package com.appforge.server.services.recordings

import com.appforge.server.api.RecordingCreateRequest
import com.appforge.server.services.auth.AuthResponse
import com.appforge.server.services.recordings.repository.RecordingRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RecordingServiceTest {
    @Test
    fun `create rejects invalid content type`() = runBlocking {
        val repo = mockk<RecordingRepository>(relaxed = true)
        val service = RecordingServiceImpl(repo)
        val result = service.create(
            userId = "user-1",
            request = RecordingCreateRequest(
                audioBase64 = "YQ==",
                contentType = "text/plain",
                durationSeconds = 1,
            ),
        )
        assertTrue(result is AuthResponse.BadRequest)
    }

    @Test
    fun `create rejects empty payload`() = runBlocking {
        val repo = mockk<RecordingRepository>(relaxed = true)
        val service = RecordingServiceImpl(repo)
        val result = service.create(
            userId = "user-1",
            request = RecordingCreateRequest(
                audioBase64 = "",
                contentType = "audio/webm",
                durationSeconds = 1,
            ),
        )
        assertTrue(result is AuthResponse.BadRequest)
    }

    @Test
    fun `content returns forbidden for non-owner or missing`() = runBlocking {
        val repo = mockk<RecordingRepository>()
        coEvery { repo.getByIdAndUser("rec-1", "user-2") } returns null
        val service = RecordingServiceImpl(repo)
        val result = service.content("user-2", "rec-1")
        assertTrue(result is AuthResponse.Forbidden)
    }

    @Test
    fun `list clamps limit and returns metadata`() = runBlocking {
        val repo = mockk<RecordingRepository>()
        coEvery { repo.listByUser("user-1", 100) } returns listOf(
            RecordingMetadata(
                id = "rec-1",
                uid = "user-1",
                contentType = "audio/webm",
                sizeBytes = 12,
                durationSeconds = 4,
                createdAt = Instant.parse("2026-05-22T00:00:00Z"),
            )
        )
        val service = RecordingServiceImpl(repo)
        val result = service.list("user-1", limit = 500)
        assertTrue(result is AuthResponse.Ok)
        result as AuthResponse.Ok
        assertEquals(1, result.data.recordings.size)
        assertEquals("rec-1", result.data.recordings.first().id)
    }
}

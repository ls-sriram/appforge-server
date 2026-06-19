package com.appforge.server.services.documents

import com.appforge.server.api.DocumentSaveRequest
import com.appforge.server.services.auth.AuthResponse
import com.appforge.server.services.documents.repository.DocumentRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentServiceTest {
    @Test
    fun `save rejects content above configured max`() = runBlocking {
        val repo = mockk<DocumentRepository>(relaxed = true)
        val service = DocumentServiceImpl(repository = repo, maxContentChars = 10)

        val result = service.save(
            userId = "u1",
            request = DocumentSaveRequest(
                title = "Title",
                tag = "Tag",
                version = "v1",
                content = "01234567890",
            ),
        )

        assertTrue(result is AuthResponse.BadRequest)
    }

    @Test
    fun `save trims fields and returns created response`() = runBlocking {
        val repo = mockk<DocumentRepository>()
        coEvery {
            repo.upsert(
                id = "doc-1",
                ownerUid = "u1",
                title = "My Title",
                tag = "notes",
                version = "v2",
                content = "hello",
            )
        } returns DocumentModel(
            id = "doc-1",
            ownerUid = "u1",
            title = "My Title",
            tag = "notes",
            version = "v2",
            content = "hello",
            contentLength = 5,
            createdAt = Instant.parse("2026-05-24T00:00:00Z"),
            updatedAt = Instant.parse("2026-05-24T00:00:00Z"),
        )

        val service = DocumentServiceImpl(repository = repo, maxContentChars = 20_000)
        val result = service.save(
            userId = "u1",
            request = DocumentSaveRequest(
                id = "doc-1",
                title = "  My Title  ",
                tag = " notes ",
                version = " v2 ",
                content = "hello",
            ),
        )

        assertTrue(result is AuthResponse.Ok)
        val ok = result.data
        assertEquals("doc-1", ok.id)
        assertEquals("My Title", ok.title)
        assertEquals("notes", ok.tag)
        assertEquals("v2", ok.version)
        assertEquals(5, ok.contentLength)
    }
}

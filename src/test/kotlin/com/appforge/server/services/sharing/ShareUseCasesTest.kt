package com.appforge.server.services.sharing

import com.appforge.server.api.sharing.CreateShareRequest
import com.appforge.server.services.email.EmailService
import com.appforge.server.services.sharing.services.ShareService
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class ShareUseCasesTest {
    @Test
    fun `createShare rejects mismatched entity type in request body`() = runBlocking {
        val useCases = ShareUseCasesImpl(
            shareService = mockk<ShareService>(relaxed = true),
            emailService = mockk<EmailService>(relaxed = true),
            publicBaseUrl = "https://example.com",
        )

        val error = assertThrows<IllegalArgumentException> {
            runBlocking {
                useCases.createShare(
                    userId = "user-1",
                    type = "document",
                    entityId = "entity-1",
                    request = CreateShareRequest(entityType = "recording"),
                )
            }
        }

        assertEquals("Entity type mismatch.", error.message)
    }
}

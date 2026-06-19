package com.appforge.server.routing

import com.appforge.server.services.reviews.models.EntityCategory
import com.appforge.server.services.sharing.models.Share
import com.appforge.server.services.sharing.models.ShareAccessMode
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ApiConvertersTest {

    @Test
    fun `toShareResponse uses base url`() {
        val share = Share(
            id = "token123",
            token = "token123",
            entityId = "s1",
            entityCategory = EntityCategory("document"),
            accessMode = ShareAccessMode.PUBLIC_LINK,
            ownerId = "user1",
            tokenHash = "hash-token123",
            expiresAt = Instant.parse("2026-01-01T00:00:00Z"),
            createdAt = Instant.parse("2025-01-01T00:00:00Z"),
            createdBy = "user1",
            revokedAt = null,
            revokedBy = null,
        )

        val response = ApiConverters.toShareResponse(share, "http://localhost:3000")
        assertEquals("http://localhost:3000/web/shares/token123", response.shareUrl)
    }
}

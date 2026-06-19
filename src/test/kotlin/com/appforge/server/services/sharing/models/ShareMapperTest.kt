package com.appforge.server.services.sharing.models

import com.appforge.server.services.reviews.models.EntityCategory
import com.appforge.server.services.sharing.models.ShareAccessMode
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShareMapperTest {

    @Test
    fun `share mapper round trips canonical fields`() {
        val share = Share(
            id = "token1",
            token = "token1",
            entityId = "rec1",
            entityCategory = EntityCategory("audio"),
            accessMode = ShareAccessMode.PUBLIC_LINK,
            ownerId = "user1",
            tokenHash = "hash-token1",
            expiresAt = Instant.parse("2026-01-01T00:00:00Z"),
            createdAt = Instant.parse("2025-01-01T00:00:00Z"),
            createdBy = "user1",
            revokedAt = null,
            revokedBy = null,
        )

        val doc = ShareMapper.toDoc(share)
        val roundTrip = ShareMapper.fromDoc("token1", doc)

        assertEquals(share.id, roundTrip.id)
        assertEquals(share.entityId, roundTrip.entityId)
        assertEquals(share.ownerId, roundTrip.ownerId)
        assertEquals(share.entityCategory, roundTrip.entityCategory)
        assertEquals(share.accessMode, roundTrip.accessMode)
    }

    @Test
    fun `share mapper fails when tokenHash is missing`() {
        val doc = mapOf(
            "id" to "token1",
            ShareMapper.Fields.AccessMode to ShareAccessMode.PUBLIC_LINK.wire,
            ShareMapper.Fields.EntityId to "rec1",
            ShareMapper.Fields.EntityCategory to "audio",
            ShareMapper.Fields.OwnerId to "user1",
            ShareMapper.Fields.CreatedAtTimestamp to Instant.parse("2025-01-01T00:00:00Z").toEpochMilli(),
        )

        assertFailsWith<IllegalStateException> {
            ShareMapper.fromDoc("token1", doc)
        }
    }
}

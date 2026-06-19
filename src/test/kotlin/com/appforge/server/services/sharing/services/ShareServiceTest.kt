package com.appforge.server.services.sharing.services

import com.appforge.server.infrastructure.Resource
import com.appforge.server.providers.time.TimestampProvider
import com.appforge.server.services.reviews.models.EntityCategory
import com.appforge.server.services.sharing.models.Share
import com.appforge.server.services.sharing.models.ShareAccessMode
import com.appforge.server.services.sharing.repository.ShareRepositoryApi
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.security.MessageDigest
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShareServiceTest {
    private class MockShareRepository : ShareRepositoryApi {
        private val byHash = mutableMapOf<String, Share>()
        private val byEntity = mutableMapOf<String, Share>()

        override suspend fun create(share: Share): Resource<Share> {
            byHash[share.tokenHash] = share
            byEntity["${share.ownerId}:${share.entityCategory.value}:${share.entityId}"] = share
            return Resource.Success(share)
        }

        override suspend fun getByTokenHash(tokenHash: String): Resource<Share> {
            return byHash[tokenHash]?.let { Resource.Success(it) } ?: Resource.Error(Exception("Share not found"))
        }

        override suspend fun findActivePublicByEntity(ownerId: String, entityCategory: EntityCategory, entityId: String): Resource<Share?> {
            return Resource.Success(byEntity["$ownerId:${entityCategory.value}:$entityId"])
        }

        override suspend fun listActiveByOwner(ownerId: String, limit: Int): Resource<List<Share>> {
            return Resource.Success(byEntity.values.filter { it.ownerId == ownerId }.take(limit))
        }

        override suspend fun listActiveByOwnerEntity(ownerId: String, entityCategory: EntityCategory, entityId: String, limit: Int): Resource<List<Share>> {
            val item = byEntity["$ownerId:${entityCategory.value}:$entityId"]
            return Resource.Success(if (item == null) emptyList() else listOf(item))
        }

        override suspend fun revokeByTokenHash(ownerId: String, tokenHash: String, revokedAt: Instant, revokedBy: String): Resource<Unit> {
            val existing = byHash[tokenHash] ?: return Resource.Error(Exception("Unauthorized"))
            if (existing.ownerId != ownerId) return Resource.Error(Exception("Unauthorized"))
            val revoked = existing.copy(revokedAt = revokedAt, revokedBy = revokedBy)
            byHash[tokenHash] = revoked
            byEntity.remove("${existing.ownerId}:${existing.entityCategory.value}:${existing.entityId}")
            return Resource.Success(Unit)
        }
    }

    @Test
    fun `createShare creates a public link share`() = runBlocking {
        val now = Instant.parse("2026-05-24T12:00:00Z")
        val timestampProvider = object : TimestampProvider {
            override fun now(): Instant = now
        }
        val service = ShareService(MockShareRepository(), timestampProvider)
        val result = service.createShare(
            ownerId = "user-1",
            entityId = "entity-1",
            entityCategory = EntityCategory("recording"),
        )

        assertTrue(result is Resource.Success)
        val share = result.data
        assertEquals("user-1", share.ownerId)
        assertEquals("recording", share.entityCategory.value)
        assertEquals(ShareAccessMode.PUBLIC_LINK, share.accessMode)
        assertTrue(share.token.isNotBlank())
        assertEquals(share.id, share.token)
        assertEquals(sha256(share.token), share.tokenHash)
        assertEquals("user-1", share.createdBy)
        assertTrue(share.expiresAt!!.isAfter(now))
    }

    @Test
    fun `createShare returns existing active link for same owner and entity`() = runBlocking {
        val service = ShareService(MockShareRepository())
        val first = service.createShare("user-1", "entity-1", EntityCategory("recording"))
        val second = service.createShare("user-1", "entity-1", EntityCategory("recording"))

        assertTrue(first is Resource.Success)
        assertTrue(second is Resource.Success)
        assertEquals(first.data.id, second.data.id)
    }

    @Test
    fun `revokeShare revokes owned token`() = runBlocking {
        val service = ShareService(MockShareRepository())
        val created = service.createShare("user-1", "entity-1", EntityCategory("recording"))
        assertTrue(created is Resource.Success)

        val revoked = service.revokeShare("user-1", created.data.token)
        assertTrue(revoked is Resource.Success)
    }

    @Test
    fun `getAndValidateShare rejects revoked token`() = runBlocking {
        val service = ShareService(MockShareRepository())
        val created = service.createShare("user-1", "entity-1", EntityCategory("recording"))
        assertTrue(created is Resource.Success)
        service.revokeShare("user-1", created.data.token)

        val result = service.getAndValidateShare(created.data.token)
        assertTrue(result is Resource.Error)
        assertEquals("Share has been revoked", result.exception.message)
    }
}

private fun sha256(value: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(value.toByteArray(Charsets.UTF_8))
    return bytes.joinToString("") { b -> "%02x".format(b) }
}

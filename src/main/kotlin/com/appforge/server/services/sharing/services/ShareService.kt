package com.appforge.server.services.sharing.services

import com.appforge.server.infrastructure.Resource
import com.appforge.server.providers.identifier.IdentifierProvider
import com.appforge.server.providers.time.TimestampProvider
import com.appforge.server.providers.time.UtcTimestampProvider
import com.appforge.server.services.reviews.models.EntityCategory
import com.appforge.server.services.sharing.models.Share
import com.appforge.server.services.sharing.models.ShareAccessMode
import com.appforge.server.services.sharing.repository.ShareRepositoryApi
import java.security.MessageDigest
import java.time.temporal.ChronoUnit
import java.util.Locale

class ShareService(
    private val shareRepository: ShareRepositoryApi,
    private val timestampProvider: TimestampProvider = UtcTimestampProvider,
) {
    suspend fun createShare(
        ownerId: String,
        entityId: String,
        entityCategory: EntityCategory,
    ): Resource<Share> {
        val existing = shareRepository.findActivePublicByEntity(ownerId, entityCategory, entityId)
        if (existing is Resource.Success && existing.data != null) return Resource.Success(existing.data)
        if (existing is Resource.Error) return Resource.Error(existing.exception)

        val now = timestampProvider.now()
        val token = IdentifierProvider.newShareToken()
        val share = Share(
            id = token,
            token = token,
            entityId = entityId,
            entityCategory = entityCategory,
            accessMode = ShareAccessMode.PUBLIC_LINK,
            ownerId = ownerId,
            tokenHash = token.sha256Hex(),
            expiresAt = now.plus(21, ChronoUnit.DAYS),
            createdAt = now,
            createdBy = ownerId,
            revokedAt = null,
            revokedBy = null,
        )
        return shareRepository.create(share)
    }

    suspend fun getAndValidateShare(token: String): Resource<Share> {
        val tokenHash = token.sha256Hex()
        return when (val res = shareRepository.getByTokenHash(tokenHash)) {
            is Resource.Success -> {
                val share = res.data
                val now = timestampProvider.now()
                if (share.revokedAt != null) {
                    Resource.Error(Exception("Share has been revoked"))
                } else if (share.expiresAt != null && now.isAfter(share.expiresAt)) {
                    Resource.Error(Exception("Share has expired"))
                } else {
                    Resource.Success(share)
                }
            }
            is Resource.Error -> Resource.Error(res.exception)
            Resource.Loading -> Resource.Loading
        }
    }

    suspend fun getShare(token: String): Resource<Share> = shareRepository.getByTokenHash(token.sha256Hex())

    suspend fun listSharesForEntity(
        ownerId: String,
        entityCategory: EntityCategory,
        entityId: String,
    ): Resource<List<Share>> {
        return shareRepository.listActiveByOwnerEntity(ownerId, entityCategory, entityId)
    }

    suspend fun listSharesForOwner(ownerId: String): Resource<List<Share>> {
        return shareRepository.listActiveByOwner(ownerId)
    }

    suspend fun revokeShare(ownerId: String, token: String): Resource<Unit> {
        val tokenHash = token.sha256Hex()
        val now = timestampProvider.now()
        return shareRepository.revokeByTokenHash(ownerId, tokenHash, now, ownerId)
    }
}

private fun String.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(this.toByteArray(Charsets.UTF_8))
    val chars = CharArray(bytes.size * 2)
    var i = 0
    for (b in bytes) {
        val v = b.toInt() and 0xFF
        chars[i++] = HEX[(v ushr 4) and 0x0F]
        chars[i++] = HEX[v and 0x0F]
    }
    return String(chars).lowercase(Locale.US)
}

private val HEX = "0123456789abcdef".toCharArray()

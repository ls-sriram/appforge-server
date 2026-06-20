package com.appforge.server.services.sharing

import com.appforge.server.api.sharing.PublicEntity
import com.appforge.server.infrastructure.Resource
import com.appforge.server.services.recordings.RecordingContent
import com.appforge.server.services.sharing.models.Share
import com.appforge.server.services.sharing.repository.ShareEntityRepositoryApi
import com.appforge.server.services.uploads.SignedGetUrlIssuer
import com.appforge.server.services.uploads.UploadMetadataRepository
import com.appforge.server.services.reviews.ai.ReviewContentResolver

suspend fun resolveSharedEntity(
    contentResolver: ReviewContentResolver,
    ownerId: String,
    entityCategory: String,
    entityId: String,
    shareEntityRepository: ShareEntityRepositoryApi,
    uploadMetadataRepository: UploadMetadataRepository,
    signedGetUrlIssuer: SignedGetUrlIssuer,
    uploadExpirySeconds: Long,
): PublicEntity {
    val doc = when (val res = shareEntityRepository.getEntityDoc(ownerId, com.appforge.server.services.reviews.models.EntityCategory(entityCategory), entityId)) {
        is Resource.Success -> res.data
        else -> null
    }

    val content = runCatching {
        contentResolver.resolveText(ownerId, com.appforge.server.services.reviews.models.EntityCategory(entityCategory), entityId, null)
    }.getOrNull()?.takeIf { it.isNotBlank() }
        ?: (doc?.get("content") as? String)?.takeIf { it.isNotBlank() }

    val title = doc?.get("title") as? String ?: doc?.get("name") as? String ?: "Entity"
    val subtitle = doc?.get("subtitle") as? String
    val assetId = doc?.get("assetId") as? String

    val assetUrl = if (!assetId.isNullOrBlank()) {
        resolveShareAssetUrl(
            uploadMetadataRepository = uploadMetadataRepository,
            signedGetUrlIssuer = signedGetUrlIssuer,
            uploadExpirySeconds = uploadExpirySeconds,
            ownerId = ownerId,
            assetId = assetId,
        )
    } else {
        null
    }

    return PublicEntity(
        id = entityId,
        category = entityCategory,
        title = title,
        subtitle = subtitle,
        content = content,
        question = doc?.get("question") as? String,
        assetUrl = assetUrl,
    )
}

private suspend fun resolveShareAssetUrl(
    uploadMetadataRepository: UploadMetadataRepository,
    signedGetUrlIssuer: SignedGetUrlIssuer,
    uploadExpirySeconds: Long,
    ownerId: String,
    assetId: String?,
): String? {
    val resolvedAssetId = assetId?.takeIf { it.isNotBlank() } ?: return null
    val record = uploadMetadataRepository.getByAssetId(resolvedAssetId) ?: return null
    if (record.uid != ownerId) return null
    return try {
        signedGetUrlIssuer.issue(
            bucket = record.bucket,
            objectPath = record.objectName,
            expiresInSeconds = uploadExpirySeconds,
        )
    } catch (_: Exception) {
        null
    }
}

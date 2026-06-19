package com.appforge.server.services.reviews.ai

import com.appforge.server.services.reviews.models.EntityCategory
import com.appforge.server.services.uploads.UploadMetadataRepository
import com.google.cloud.storage.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves content for AI reviews. Works with any entity type — no domain assumptions.
 */
class ReviewContentResolver(
    private val storage: Storage,
    private val uploadMetadataRepository: UploadMetadataRepository,
    private val textContentResolverRegistry: EntityTextContentResolverRegistry,
) {
    suspend fun resolveText(userId: String, category: EntityCategory, entityId: String, versionId: String? = null): String {
        return textContentResolverRegistry.resolve(category.value, userId, entityId, versionId)
    }

    /**
     * Resolves raw bytes for an asset from GCS by assetId.
     */
    suspend fun resolveBytes(_userId: String, _category: EntityCategory, entityId: String): ByteArray? {
        val record = uploadMetadataRepository.getByAssetId(entityId) ?: return null

        return withContext(Dispatchers.IO) {
            try {
                val blob = storage.get(record.bucket, record.objectName)
                blob?.getContent()
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Resolves a transcript if available.
     * Placeholder — extend when your frontend stores transcripts separately.
     */
    suspend fun resolveTranscript(_userId: String, _category: EntityCategory, _entityId: String): String? {
        return null
    }
}

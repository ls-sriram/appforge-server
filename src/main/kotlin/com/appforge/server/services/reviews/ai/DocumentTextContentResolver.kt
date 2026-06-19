package com.appforge.server.services.reviews.ai

import com.appforge.server.services.documents.repository.DocumentRepository

class DocumentTextContentResolver(
    private val repository: DocumentRepository,
) : EntityTextContentResolver {
    override suspend fun resolve(userId: String, entityId: String, versionId: String?): String {
        val doc = repository.getByIdAndOwner(entityId, userId)
            ?: throw IllegalStateException("Document content not found for entityId=$entityId userId=$userId")
        return doc.content
    }
}

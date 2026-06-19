package com.appforge.server.services.uploads.repository

import com.appforge.server.services.documents.repository.DocumentRepository

class DocumentOwnershipResolver(
    private val documentRepository: DocumentRepository,
) : EntityOwnershipResolver {
    override suspend fun isOwner(uid: String, entityId: String): Boolean {
        return documentRepository.existsByIdAndOwner(entityId, uid)
    }
}

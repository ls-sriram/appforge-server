package com.appforge.server.services.sharing.repository

import com.appforge.server.services.documents.repository.DocumentRepository

class DocumentEntityDescriptorResolver(
    private val repository: DocumentRepository,
) : EntityDescriptorResolver {
    override suspend fun resolve(ownerId: String, entityId: String): Map<String, Any?>? {
        val doc = repository.getByIdAndOwner(entityId, ownerId) ?: return null
        return mapOf(
            "title" to doc.title,
            "subtitle" to "${doc.tag} · ${doc.version}",
            "content" to doc.content,
        )
    }
}

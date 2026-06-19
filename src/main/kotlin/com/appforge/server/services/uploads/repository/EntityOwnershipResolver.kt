package com.appforge.server.services.uploads.repository

import com.appforge.server.services.uploads.UploadType

fun interface EntityOwnershipResolver {
    suspend fun isOwner(uid: String, entityId: String): Boolean
}

class EntityOwnershipResolverRegistry(
    private val resolversByType: Map<UploadType, EntityOwnershipResolver>,
) {
    suspend fun isOwner(uid: String, type: UploadType, entityId: String): Boolean {
        val resolver = resolversByType[type] ?: return false
        return resolver.isOwner(uid, entityId)
    }
}

package com.appforge.server.services.sharing.repository

interface EntityDescriptorResolver {
    suspend fun resolve(ownerId: String, entityId: String): Map<String, Any?>?
}

class EntityDescriptorResolverRegistry(
    private val resolversByType: Map<String, EntityDescriptorResolver>,
) {
    suspend fun resolve(entityType: String, ownerId: String, entityId: String): Map<String, Any?>? {
        val resolver = resolversByType[entityType.trim().lowercase()] ?: return null
        return resolver.resolve(ownerId, entityId)
    }
}

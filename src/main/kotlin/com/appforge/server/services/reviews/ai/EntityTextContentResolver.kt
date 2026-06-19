package com.appforge.server.services.reviews.ai

fun interface EntityTextContentResolver {
    suspend fun resolve(userId: String, entityId: String, versionId: String?): String
}

class EntityTextContentResolverRegistry(
    private val resolversByType: Map<String, EntityTextContentResolver>,
) {
    suspend fun resolve(entityType: String, userId: String, entityId: String, versionId: String? = null): String {
        val normalizedType = entityType.trim().lowercase()
        val resolver = resolversByType[normalizedType]
            ?: throw IllegalArgumentException("No text content resolver registered for entity type: $normalizedType")
        return resolver.resolve(userId, entityId, versionId)
    }
}

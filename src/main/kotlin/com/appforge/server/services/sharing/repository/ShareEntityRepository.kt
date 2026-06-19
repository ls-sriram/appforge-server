package com.appforge.server.services.sharing.repository

import com.appforge.server.infrastructure.Resource
import com.appforge.server.services.reviews.models.EntityCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ShareEntityRepositoryApi {
    suspend fun getEntityDoc(
        ownerId: String,
        category: EntityCategory,
        entityId: String,
    ): Resource<Map<String, Any?>?>
}

class ShareEntityRepository(
    private val resolverRegistry: EntityDescriptorResolverRegistry,
) : ShareEntityRepositoryApi {
    override suspend fun getEntityDoc(
        ownerId: String,
        category: EntityCategory,
        entityId: String,
    ): Resource<Map<String, Any?>?> = withContext(Dispatchers.IO) {
        try {
            Resource.Success(
                resolverRegistry.resolve(
                    entityType = category.value,
                    ownerId = ownerId,
                    entityId = entityId,
                )
            )
        } catch (e: Exception) {
            Resource.Error(e)
        }
    }
}

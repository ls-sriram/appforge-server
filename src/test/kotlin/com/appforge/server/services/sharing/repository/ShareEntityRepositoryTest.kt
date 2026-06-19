package com.appforge.server.services.sharing.repository

import com.appforge.server.infrastructure.Resource
import com.appforge.server.services.reviews.models.EntityCategory
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShareEntityRepositoryTest {
    @Test
    fun `getEntityDoc resolves via typed resolver registry`() = runBlocking {
        val repository = ShareEntityRepository(
            resolverRegistry = EntityDescriptorResolverRegistry(
                resolversByType = mapOf(
                    "document" to object : EntityDescriptorResolver {
                        override suspend fun resolve(ownerId: String, entityId: String): Map<String, Any?> {
                            return mapOf("id" to entityId, "title" to "Statement")
                        }
                    },
                )
            )
        )

        val result = repository.getEntityDoc("u1", EntityCategory("document"), "d1")
        assertTrue(result is Resource.Success)
        assertEquals("Statement", result.data?.get("title"))
        assertEquals("d1", result.data?.get("id"))
    }
}

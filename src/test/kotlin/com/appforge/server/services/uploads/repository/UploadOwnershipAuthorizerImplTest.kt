package com.appforge.server.services.uploads.repository

import com.appforge.server.services.uploads.UploadType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UploadOwnershipAuthorizerImplTest {
    @Test
    fun `canUploadToEntity delegates to typed resolver`() = runBlocking {
        val authorizer = UploadOwnershipAuthorizerImpl(
            ownershipResolverRegistry = EntityOwnershipResolverRegistry(
                resolversByType = mapOf(
                    UploadType.AUDIO to EntityOwnershipResolver { uid, entityId ->
                        uid == "owner" && entityId == "rec-1"
                    }
                )
            )
        )

        assertTrue(authorizer.canUploadToEntity("owner", UploadType.AUDIO, "rec-1"))
        assertFalse(authorizer.canUploadToEntity("other", UploadType.AUDIO, "rec-1"))
        assertFalse(authorizer.canUploadToEntity("owner", UploadType.DOCUMENT, "doc-1"))
    }
}

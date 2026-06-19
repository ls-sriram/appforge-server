package com.appforge.server.services.uploads.repository

import com.appforge.server.services.uploads.UploadOwnershipAuthorizer
import com.appforge.server.services.uploads.UploadType

class UploadOwnershipAuthorizerImpl(
    private val ownershipResolverRegistry: EntityOwnershipResolverRegistry,
) : UploadOwnershipAuthorizer {
    override suspend fun canUploadToEntity(uid: String, type: UploadType, entityId: String): Boolean {
        return ownershipResolverRegistry.isOwner(uid = uid, type = type, entityId = entityId)
    }
}

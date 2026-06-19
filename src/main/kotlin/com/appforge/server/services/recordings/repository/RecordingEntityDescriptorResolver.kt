package com.appforge.server.services.recordings.repository

import com.appforge.server.services.sharing.repository.EntityDescriptorResolver

class RecordingEntityDescriptorResolver(
    private val repository: RecordingRepository,
) : EntityDescriptorResolver {
    override suspend fun resolve(ownerId: String, entityId: String): Map<String, Any?>? {
        val content = repository.getByIdAndUser(entityId, ownerId) ?: return null
        return mapOf(
            "title" to "Recording",
            "subtitle" to "${content.metadata.durationSeconds ?: 0}s",
        )
    }
}

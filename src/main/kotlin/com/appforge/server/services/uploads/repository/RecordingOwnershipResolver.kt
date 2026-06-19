package com.appforge.server.services.uploads.repository

import com.appforge.server.services.recordings.repository.RecordingRepository

class RecordingOwnershipResolver(
    private val recordingRepository: RecordingRepository,
) : EntityOwnershipResolver {
    override suspend fun isOwner(uid: String, entityId: String): Boolean {
        return recordingRepository.existsByIdAndUser(entityId, uid)
    }
}

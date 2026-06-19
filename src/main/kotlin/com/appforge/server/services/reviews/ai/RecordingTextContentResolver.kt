package com.appforge.server.services.reviews.ai

class RecordingTextContentResolver : EntityTextContentResolver {
    override suspend fun resolve(userId: String, entityId: String, versionId: String?): String = ""
}

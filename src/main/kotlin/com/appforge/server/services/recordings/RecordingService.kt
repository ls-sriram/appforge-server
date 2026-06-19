package com.appforge.server.services.recordings

import com.appforge.server.api.RecordingCreateRequest
import com.appforge.server.api.RecordingListResponse
import com.appforge.server.api.RecordingResponse
import com.appforge.server.infrastructure.time.instantToProtoTimestamp
import com.appforge.server.services.auth.AuthResponse
import com.appforge.server.services.recordings.repository.RecordingRepository
import java.util.Base64
import java.util.UUID

interface RecordingService {
    suspend fun create(userId: String, request: RecordingCreateRequest): AuthResponse<RecordingResponse>
    suspend fun list(userId: String, limit: Int = 20): AuthResponse<RecordingListResponse>
    suspend fun content(userId: String, recordingId: String): AuthResponse<RecordingContent>
}

class RecordingServiceImpl(
    private val repository: RecordingRepository,
) : RecordingService {
    override suspend fun create(userId: String, request: RecordingCreateRequest): AuthResponse<RecordingResponse> {
        val contentType = request.contentType.trim().lowercase()
        if (contentType.isBlank() || !contentType.startsWith("audio/")) {
            return AuthResponse.BadRequest("contentType must be an audio/* MIME type.")
        }
        val bytes = try {
            Base64.getDecoder().decode(request.audioBase64)
        } catch (_: IllegalArgumentException) {
            return AuthResponse.BadRequest("audioBase64 must be valid Base64.")
        }
        if (bytes.isEmpty()) {
            return AuthResponse.BadRequest("audio payload must not be empty.")
        }
        if (request.durationSeconds != null && request.durationSeconds < 0) {
            return AuthResponse.BadRequest("durationSeconds must be >= 0.")
        }
        val id = UUID.randomUUID().toString()
        val created = repository.create(
            id = id,
            uid = userId,
            audioBytes = bytes,
            contentType = contentType,
            durationSeconds = request.durationSeconds,
        )
        return AuthResponse.Ok(created.toApi())
    }

    override suspend fun list(userId: String, limit: Int): AuthResponse<RecordingListResponse> {
        val normalizedLimit = limit.coerceIn(1, 100)
        val items = repository.listByUser(userId, normalizedLimit).map { it.toApi() }
        return AuthResponse.Ok(RecordingListResponse(recordings = items))
    }

    override suspend fun content(userId: String, recordingId: String): AuthResponse<RecordingContent> {
        val id = recordingId.trim()
        if (id.isBlank()) return AuthResponse.BadRequest("recording id is required.")
        val content = repository.getByIdAndUser(id, userId) ?: return AuthResponse.Forbidden("Recording not found.")
        return AuthResponse.Ok(content)
    }
}

private fun RecordingMetadata.toApi(): RecordingResponse =
    RecordingResponse(
        id = id,
        createdAt = instantToProtoTimestamp(createdAt) ?: error("recordings.createdAt cannot be null"),
        durationSeconds = durationSeconds,
        contentType = contentType,
        sizeBytes = sizeBytes,
    )

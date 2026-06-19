package com.appforge.server.services.uploads

import java.util.Locale

/**
 * Supported upload types.
 */
enum class UploadType(val wire: String) {
    IMAGE("image"),
    AUDIO("audio"),
    VIDEO("video"),
    DOCUMENT("document");

    companion object {
        fun fromWire(raw: String): UploadType {
            val normalized = raw.trim().lowercase(Locale.getDefault())
            return entries.firstOrNull { it.wire == normalized }
                ?: throw IllegalArgumentException("Unsupported upload type: $raw")
        }
    }
}

enum class UploadStatus(val wire: String) {
    PENDING("pending"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    FAILED("failed");
}

data class UploadRecord(
        val uploadId: String,
        val assetId: String,
        val uid: String,
        val type: UploadType,
        val entityId: String,
        val bucket: String,
        val objectName: String,
        val contentType: String,
        val sizeBytes: Long,
        val status: UploadStatus,
        val createdAtTimestamp: Long, // epoch millis
        val expiresAtTimestamp: Long, // epoch millis
)

fun interface UploadOwnershipAuthorizer {
    suspend fun canUploadToEntity(uid: String, type: UploadType, entityId: String): Boolean
}

interface UploadMetadataRepository {
    suspend fun createPending(record: UploadRecord)
    suspend fun getByAssetId(assetId: String): UploadRecord?
    suspend fun getByObjectName(objectName: String): UploadRecord?
    suspend fun markCompleted(
        uploadId: String,
        generation: Long,
        sizeBytes: Long,
        contentType: String?,
        completedAtTimestamp: Long,
        eventTimeEpochSeconds: Long?,
    )
}

object UploadSchema {
    private val defaultAllowedContentTypes = setOf(
        "image/jpeg", "image/jpg", "image/png", "image/webp", "image/heic",
        "audio/webm", "audio/mp4", "audio/mpeg", "audio/wav",
        "video/webm", "video/mp4",
        "application/pdf",
    )

    fun buildObjectName(
            uid: String,
            type: UploadType,
            entityId: String,
            assetId: String,
            extension: String? = null
    ): String {
        val ext = extension ?: guessExtension(type.wire)
        return "users/$uid/entities/$entityId/uploads/$assetId.$ext"
    }

    private fun guessExtension(contentType: String): String =
            when {
                contentType.contains("image") -> "jpg"
                contentType.contains("audio") -> "webm"
                contentType.contains("video") -> "mp4"
                contentType.contains("pdf") -> "pdf"
                else -> "bin"
            }

    fun validateContentType(type: UploadType, contentType: String) {
        val normalized = contentType.lowercase(Locale.getDefault())
        require(defaultAllowedContentTypes.contains(normalized)) { "Unsupported content type: $contentType" }
    }

    fun extensionFor(type: UploadType, contentType: String): String {
        val normalized = contentType.lowercase(Locale.getDefault())
        return when {
            normalized.contains("jpeg") || normalized.contains("jpg") -> "jpg"
            normalized.contains("png") -> "png"
            normalized.contains("webp") -> "webp"
            normalized.contains("heic") -> "heic"
            normalized.contains("mp4") -> "mp4"
            normalized.contains("webm") -> "webm"
            normalized.contains("mpeg") || normalized.contains("mp3") -> "mp3"
            normalized.contains("wav") -> "wav"
            normalized.contains("pdf") -> "pdf"
            else -> "bin"
        }
    }
}

data class SignedUploadRequest(
        val uploadId: String,
        val objectPath: String,
        val uid: String,
        val type: UploadType,
        val entityId: String,
        val contentType: String,
        val maxSizeBytes: Long,
        val expiresAtTimestamp: Long, // epoch millis
)

data class SignedUploadResponse(
        val uploadId: String,
        val objectPath: String,
        val uploadUrl: String,
        val expiresAtTimestamp: Long, // epoch millis
)

interface SignedUploadUrlIssuer {
    suspend fun issue(request: SignedUploadRequest): SignedUploadResponse
}

interface SignedGetUrlIssuer {
    suspend fun issue(bucket: String, objectPath: String, expiresInSeconds: Long): String
}

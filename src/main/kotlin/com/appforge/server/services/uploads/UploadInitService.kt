package com.appforge.server.services.uploads

import com.appforge.server.config.AppEnv
import com.appforge.server.infrastructure.Repository
import com.appforge.server.infrastructure.Resource
import java.time.Clock

class UploadInitService(
    private val env: AppEnv,
    authorizer: UploadOwnershipAuthorizer,
    private val metadataRepository: UploadMetadataRepository,
    private val processedEventsRepository: Repository<Map<String, Any?>>,
    signedPutUrlIssuer: SignedUploadUrlIssuer,
    private val signedGetUrlIssuer: SignedGetUrlIssuer,
    clock: Clock = Clock.systemUTC(),
) {
    private val coordinator = UploadInitCoordinator(
        env = env,
        authorizer = authorizer,
        metadataRepository = metadataRepository,
        signedPutUrlIssuer = signedPutUrlIssuer,
        clock = clock,
    )

    suspend fun init(
        uid: String,
        type: UploadType,
        entityId: String,
        contentType: String,
        sizeBytes: Long,
        assetId: String,
    ): UploadInitResult =
        coordinator.init(
            uid = uid,
            type = type,
            entityId = entityId,
            contentType = contentType,
            sizeBytes = sizeBytes,
            assetId = assetId,
        )

    suspend fun getAccessUrl(uid: String, assetId: String): String? {
        val record = metadataRepository.getByAssetId(assetId) ?: return null

        // Enforce ownership: only the owner can generate this signed URL
        if (record.uid != uid) return null

        return signedGetUrlIssuer.issue(
            bucket = record.bucket,
            objectPath = record.objectName,
            expiresInSeconds = env.uploads.uploadUrlExpirySeconds.toLong()
        )
    }

    suspend fun completeUpload(request: UploadCompletionRequest): UploadCompletionResult {
        val eventId = "${request.bucket}:${request.objectName}:${request.generation}"
        val markedProcessed = processedEventsRepository.setIfAbsent(
            id = eventId,
            initial = mapOf(
                "bucket" to request.bucket,
                "objectName" to request.objectName,
                "generation" to request.generation,
                "sizeBytes" to request.sizeBytes,
                "contentType" to request.contentType,
                "eventTimeEpochSeconds" to request.eventTimeEpochSeconds,
                "processedAtTimestamp" to System.currentTimeMillis(),
            )
        )
        val isFirstEvent = when (markedProcessed) {
            is Resource.Success -> markedProcessed.data
            else -> false
        }
        if (!isFirstEvent) return UploadCompletionResult(processed = false)

        val record = metadataRepository.getByObjectName(request.objectName)
        if (record != null && record.bucket == request.bucket) {
            metadataRepository.markCompleted(
                uploadId = record.uploadId,
                generation = request.generation,
                sizeBytes = request.sizeBytes,
                contentType = request.contentType,
                completedAtTimestamp = System.currentTimeMillis(),
                eventTimeEpochSeconds = request.eventTimeEpochSeconds,
            )
        }
        return UploadCompletionResult(processed = true)
    }
}

class ForbiddenUploadException(message: String) : RuntimeException(message)

package com.appforge.server.services.uploads

import com.appforge.server.config.AppEnv
import java.time.Clock
import com.appforge.server.providers.identifier.IdentifierProvider
import com.appforge.server.services.uploads.UploadStatus

import org.slf4j.LoggerFactory

class UploadInitCoordinator(
    private val env: AppEnv,
    private val authorizer: UploadOwnershipAuthorizer,
    private val metadataRepository: UploadMetadataRepository,
    private val signedPutUrlIssuer: SignedUploadUrlIssuer,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val logger = LoggerFactory.getLogger(UploadInitCoordinator::class.java)

    suspend fun init(
        uid: String,
        type: UploadType,
        entityId: String,
        contentType: String,
        sizeBytes: Long,
        assetId: String,
    ): UploadInitResult {
        require(entityId.isNotBlank()) { "Missing entityId" }
        require(sizeBytes > 0) { "sizeBytes must be > 0" }

        val maxBytes = env.uploads.uploadMaxBytes
        require(sizeBytes <= maxBytes) { "File too large (maxBytes=$maxBytes)" }

        UploadSchema.validateContentType(type, contentType)

        val allowed = authorizer.canUploadToEntity(uid, type, entityId)
        if (!allowed) throw ForbiddenUploadException("Not authorized for entityId")

        val uploadId = IdentifierProvider.newId()
        val extension = UploadSchema.extensionFor(type, contentType)
        val objectName = UploadSchema.buildObjectName(uid, type, entityId, assetId, extension)

        val now = clock.instant()
        val nowMs = now.toEpochMilli()
        val expiresAtMs = now.plusSeconds(env.uploads.uploadUrlExpirySeconds.toLong()).toEpochMilli()

        val record = UploadRecord(
            uploadId = uploadId,
            assetId = assetId,
            uid = uid,
            type = type,
            entityId = entityId,
            bucket = env.uploads.uploadsBucket,
            objectName = objectName,
            contentType = contentType,
            sizeBytes = sizeBytes,
            status = UploadStatus.PENDING,
            createdAtTimestamp = nowMs,
            expiresAtTimestamp = expiresAtMs,
        )
        metadataRepository.createPending(record)

        val issued = signedPutUrlIssuer.issue(
            SignedUploadRequest(
                uploadId = uploadId,
                objectPath = objectName,
                uid = uid,
                type = type,
                entityId = entityId,
                contentType = contentType,
                maxSizeBytes = maxBytes,
                expiresAtTimestamp = expiresAtMs,
            )
        )

        return UploadInitResult(
            uploadId = issued.uploadId,
            assetId = assetId,
            uploadUrl = issued.uploadUrl,
            expiresAtTimestamp = issued.expiresAtTimestamp,
            accessUrl = "/api/v1/uploads/access/$assetId",
        )
    }
}

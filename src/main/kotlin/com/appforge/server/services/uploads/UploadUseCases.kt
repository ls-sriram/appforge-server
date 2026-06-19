package com.appforge.server.services.uploads

import com.appforge.server.api.UploadInitRequest
import com.appforge.server.api.UploadInitResponse
import com.appforge.server.api.UploadTypeDto

interface UploadUseCases {
    suspend fun initUpload(userId: String, request: UploadInitRequest): UploadInitResponse
    suspend fun getAccessUrl(userId: String, assetId: String): String?
    suspend fun completeUpload(request: UploadCompletionRequest): UploadCompletionResult
}

class UploadUseCasesImpl(
    private val uploadInitService: UploadInitService,
) : UploadUseCases {
    override suspend fun initUpload(userId: String, request: UploadInitRequest): UploadInitResponse {
        val type = when (request.type) {
            UploadTypeDto.IMAGE -> UploadType.IMAGE
            UploadTypeDto.AUDIO -> UploadType.AUDIO
            UploadTypeDto.VIDEO -> UploadType.VIDEO
            UploadTypeDto.DOCUMENT -> UploadType.DOCUMENT
        }

        val result = uploadInitService.init(
            uid = userId,
            type = type,
            entityId = request.entityId,
            contentType = request.contentType,
            sizeBytes = request.sizeBytes,
            assetId = request.assetId,
        )

        return UploadInitResponse(
            uploadId = result.uploadId,
            assetId = result.assetId,
            uploadUrl = result.uploadUrl,
            expiresAtTimestamp = result.expiresAtTimestamp,
            accessUrl = result.accessUrl,
        )
    }

    override suspend fun getAccessUrl(userId: String, assetId: String): String? {
        return uploadInitService.getAccessUrl(userId, assetId)
    }

    override suspend fun completeUpload(request: UploadCompletionRequest): UploadCompletionResult {
        return uploadInitService.completeUpload(request)
    }
}

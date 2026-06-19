package com.appforge.server.services.uploads.repository

import com.appforge.server.config.AppEnv
import com.appforge.server.services.uploads.SignedUploadRequest
import com.appforge.server.services.uploads.SignedUploadResponse
import com.appforge.server.services.uploads.SignedUploadUrlIssuer
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.HttpMethod
import com.google.cloud.storage.Storage
import com.google.cloud.storage.Storage.SignUrlOption
import java.time.Clock
import com.appforge.server.infrastructure.time.*
import java.util.concurrent.TimeUnit

class GcsSignedUploadUrlIssuer(
    private val storage: Storage,
    private val env: AppEnv,
    private val clock: Clock = Clock.systemUTC(),
) : SignedUploadUrlIssuer {
    override suspend fun issue(request: SignedUploadRequest): SignedUploadResponse {
        val expiresInSeconds = ((request.expiresAtTimestamp - clock.nowTimestamp().toEpochMilli()) / 1000)
            .coerceAtLeast(1)
        val blobInfo = BlobInfo.newBuilder(env.uploads.uploadsBucket, request.objectPath)
            .setContentType(request.contentType)
            .build()

        val url = storage.signUrl(
            blobInfo,
            expiresInSeconds,
            TimeUnit.SECONDS,
            SignUrlOption.httpMethod(HttpMethod.PUT),
            SignUrlOption.withContentType()
        )

        return SignedUploadResponse(
            uploadId = request.uploadId,
            objectPath = request.objectPath,
            uploadUrl = url.toString(),
            expiresAtTimestamp = request.expiresAtTimestamp,
        )
    }
}

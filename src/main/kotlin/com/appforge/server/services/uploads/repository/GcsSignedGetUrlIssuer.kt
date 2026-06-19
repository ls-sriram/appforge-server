package com.appforge.server.services.uploads.repository

import com.appforge.server.services.uploads.SignedGetUrlIssuer
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.HttpMethod
import com.google.cloud.storage.Storage
import com.google.cloud.storage.Storage.SignUrlOption
import java.util.concurrent.TimeUnit

class GcsSignedGetUrlIssuer(
    private val storage: Storage,
) : SignedGetUrlIssuer {
    override suspend fun issue(bucket: String, objectPath: String, expiresInSeconds: Long): String {
        val blobInfo = BlobInfo.newBuilder(bucket, objectPath).build()

        val url = storage.signUrl(
            blobInfo,
            expiresInSeconds,
            TimeUnit.SECONDS,
            SignUrlOption.httpMethod(HttpMethod.GET),
            SignUrlOption.withV4Signature()
        )

        return url.toString()
    }
}

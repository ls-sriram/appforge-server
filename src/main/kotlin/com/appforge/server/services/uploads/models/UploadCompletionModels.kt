package com.appforge.server.services.uploads

data class UploadCompletionRequest(
    val bucket: String,
    val objectName: String,
    val generation: Long,
    val sizeBytes: Long,
    val contentType: String?,
    val eventTimeEpochSeconds: Long?,
)

data class UploadCompletionResult(
    val processed: Boolean,
)

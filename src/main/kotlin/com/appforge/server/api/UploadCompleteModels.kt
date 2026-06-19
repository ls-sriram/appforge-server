package com.appforge.server.api

import kotlinx.serialization.Serializable

@Serializable
data class UploadCompleteRequest(
    val bucket: String,
    val objectName: String,
    val generation: Long,
    val sizeBytes: Long,
    val contentType: String? = null,
    val eventTimeEpochSeconds: Long? = null,
)

@Serializable
data class UploadCompleteResponse(
    val success: Boolean,
)

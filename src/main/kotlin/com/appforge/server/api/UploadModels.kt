package com.appforge.server.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class UploadTypeDto {
    @SerialName("image")
    IMAGE,
    @SerialName("audio")
    AUDIO,
    @SerialName("video")
    VIDEO,
    @SerialName("document")
    DOCUMENT,
}

@Serializable
data class UploadInitRequest(
    val type: UploadTypeDto,
    val entityId: String,
    val contentType: String,
    val sizeBytes: Long,
    val assetId: String,
)

@Serializable
data class UploadInitResponse(
    val uploadId: String,
    val assetId: String,
    val uploadUrl: String,
    val expiresAtTimestamp: Long, // epoch millis
    val accessUrl: String,
)

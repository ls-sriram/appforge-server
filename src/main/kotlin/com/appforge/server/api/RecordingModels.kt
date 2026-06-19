package com.appforge.server.api

import kotlinx.serialization.Serializable

@Serializable
data class RecordingCreateRequest(
    val audioBase64: String,
    val contentType: String,
    val durationSeconds: Int? = null,
)

@Serializable
data class RecordingResponse(
    val id: String,
    val createdAt: ProtoTimestamp,
    val durationSeconds: Int? = null,
    val contentType: String,
    val sizeBytes: Long,
)

@Serializable
data class RecordingListResponse(
    val recordings: List<RecordingResponse>,
)

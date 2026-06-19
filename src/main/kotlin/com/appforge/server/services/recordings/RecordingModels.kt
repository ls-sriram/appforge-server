package com.appforge.server.services.recordings

import com.appforge.server.infrastructure.time.AppTimestamp

data class RecordingMetadata(
    val id: String,
    val uid: String,
    val contentType: String,
    val sizeBytes: Long,
    val durationSeconds: Int?,
    val createdAt: AppTimestamp,
)

data class RecordingContent(
    val metadata: RecordingMetadata,
    val audioBytes: ByteArray,
)

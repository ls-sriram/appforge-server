package com.appforge.server.services.uploads

data class UploadInitResult(
    val uploadId: String,
    val assetId: String,
    val uploadUrl: String,
    val expiresAtTimestamp: Long, // epoch millis
    val accessUrl: String,
)

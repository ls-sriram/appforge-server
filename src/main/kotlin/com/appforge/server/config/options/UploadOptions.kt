package com.appforge.server.config.options

import com.appforge.server.config.ConfigReader
import com.appforge.server.config.ConfigDefaults.DEFAULT_UPLOAD_URL_EXPIRY_SECONDS

data class UploadOptions(
    val uploadsBucket: String,
    val uploadEventSharedSecret: String,
    val uploadUrlExpirySeconds: Int,
    val uploadMaxBytes: Long,
) {
    companion object {
        fun load(reader: ConfigReader): UploadOptions = UploadOptions(
            uploadsBucket = reader.requiredString("UPLOADS_BUCKET"),
            uploadEventSharedSecret = reader.requiredString("UPLOAD_EVENT_SHARED_SECRET"),
            uploadUrlExpirySeconds = reader.int("UPLOAD_URL_EXPIRY_SECONDS")
                ?.coerceIn(60, 600) ?: DEFAULT_UPLOAD_URL_EXPIRY_SECONDS,
            uploadMaxBytes = reader.requiredLong("UPLOAD_MAX_BYTES"),
        )
    }
}

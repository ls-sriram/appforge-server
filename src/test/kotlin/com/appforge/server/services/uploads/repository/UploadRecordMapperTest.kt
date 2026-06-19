package com.appforge.server.services.uploads.repository

import kotlin.test.Test
import kotlin.test.assertFailsWith

class UploadRecordMapperTest {
    @Test
    fun `upload record mapper fails when assetId is missing`() {
        val doc = mapOf(
            "uploadId" to "up_1",
            "uid" to "user_1",
            "type" to "audio",
            "entityId" to "entity_1",
            "bucket" to "bucket-a",
            "objectName" to "users/user_1/entity_1/up_1.webm",
            "contentType" to "audio/webm",
            "sizeBytes" to 100L,
            "status" to "pending",
            "createdAtTimestamp" to 1_700_000_000_000L,
            "expiresAtTimestamp" to 1_700_000_100_000L,
        )

        assertFailsWith<IllegalStateException> {
            UploadRecordMapper.fromDoc("up_1", doc)
        }
    }
}

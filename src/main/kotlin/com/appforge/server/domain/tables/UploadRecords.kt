package com.appforge.server.domain.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * ─────────────────────────────────────────────────────────────────
 * UPLOAD_RECORDS — GCS upload metadata.
 * ─────────────────────────────────────────────────────────────────
 */
object UploadRecords : Table("upload_records") {
    val uploadId = varchar("upload_id", 255).entityId()
    val uid = varchar("uid", 255)
    val type = varchar("type", 100)
    val entityId = varchar("entity_id", 255)
    val bucket = varchar("bucket", 255)
    val objectName = varchar("object_name", 1000)
    val contentType = varchar("content_type", 255)
    val sizeBytes = long("size_bytes")
    val status = varchar("status", 50).default("pending")
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at").nullable()

    override val primaryKey = PrimaryKey(uploadId)

    init {
        index("idx_uploads_user", false, uid)
        index("idx_uploads_status", false, status)
        index("idx_uploads_expires", false, expiresAt)
    }
}

package com.appforge.server.services.uploads.repository

import com.appforge.server.services.uploads.UploadRecord
import com.appforge.server.services.uploads.UploadStatus
import com.appforge.server.services.uploads.UploadType
import com.appforge.server.utils.DocReader
import com.appforge.server.utils.Mapper

object UploadRecordMapper : Mapper<UploadRecord, Map<String, Any?>> {
    override fun toDoc(domain: UploadRecord): Map<String, Any?> =
            mapOf(
                    "uploadId" to domain.uploadId,
                    "assetId" to domain.assetId,
                    "uid" to domain.uid,
                    "type" to domain.type.wire,
                    "entityId" to domain.entityId,
                    "bucket" to domain.bucket,
                    "objectName" to domain.objectName,
                    "contentType" to domain.contentType,
                    "sizeBytes" to domain.sizeBytes,
                    "status" to domain.status.wire,
                    "createdAtTimestamp" to domain.createdAtTimestamp,
                    "expiresAtTimestamp" to domain.expiresAtTimestamp,
            )

    override fun fromDoc(id: String, doc: Map<String, Any?>): UploadRecord {
        val r = DocReader(doc, id)
        return UploadRecord(
                uploadId = r.optionalString("uploadId") ?: id,
                assetId = r.string("assetId"),
                uid = r.string("uid"),
                type = UploadType.fromWire(r.string("type")),
                entityId = r.string("entityId"),
                bucket = r.string("bucket"),
                objectName = r.string("objectName"),
                contentType = r.string("contentType"),
                sizeBytes = r.long("sizeBytes"),
                status = UploadStatus.entries.firstOrNull { it.wire == r.string("status") }
                    ?: throw IllegalArgumentException("Unsupported upload status: ${r.string("status")}"),
                createdAtTimestamp = r.long("createdAtTimestamp"),
                expiresAtTimestamp = r.long("expiresAtTimestamp"),
        )
    }
}

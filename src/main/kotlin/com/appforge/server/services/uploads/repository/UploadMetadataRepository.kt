package com.appforge.server.services.uploads.repository

import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.infrastructure.sql.NamedSql
import com.appforge.server.infrastructure.time.setInstant
import com.appforge.server.providers.transaction.SqlTransactionProvider
import com.appforge.server.providers.transaction.TransactionProvider
import com.appforge.server.services.uploads.UploadRecord
import com.appforge.server.services.uploads.UploadStatus
import com.appforge.server.services.uploads.UploadType
import com.appforge.server.infrastructure.time.*

class UploadMetadataRepositoryImpl(
    sqlDatabase: ExposedDatabase,
) : com.appforge.server.services.uploads.UploadMetadataRepository {
    private val sql = NamedSql.fromResource(
        resourcePath = "com/appforge/server/services/uploads/uploads.sql",
        classLoader = UploadMetadataRepositoryImpl::class.java.classLoader,
    )
    private val transactionProvider: TransactionProvider = SqlTransactionProvider(sqlDatabase)

    override suspend fun createPending(record: UploadRecord) {
        transactionProvider.write { conn ->
                conn.prepareStatement(
                    sql.query("uploads.upsert_pending_upload")
                ).use { stmt ->
                    stmt.setString(1, record.uploadId)
                    stmt.setString(2, record.uid)
                    stmt.setString(3, record.type.wire)
                    stmt.setString(4, record.entityId)
                    stmt.setString(5, record.bucket)
                    stmt.setString(6, record.objectName)
                    stmt.setString(7, record.contentType)
                    stmt.setLong(8, record.sizeBytes)
                    stmt.setString(9, record.status.wire)
                    stmt.setInstant(10, timestampFromEpochMilli(record.createdAtTimestamp))
                    stmt.setInstant(11, timestampFromEpochMilli(record.expiresAtTimestamp))
                    stmt.executeUpdate()
                }
        }
    }

    override suspend fun getByAssetId(assetId: String): UploadRecord? {
        return transactionProvider.read { conn ->
                conn.prepareStatement(
                    sql.query("uploads.select_upload_by_id")
                ).use { stmt ->
                    stmt.setString(1, assetId)
                    stmt.executeQuery().use { rs ->
                        if (!rs.next()) return@read null
                        toUploadRecord(rs)
                    }
                }
        }
    }

    override suspend fun getByObjectName(objectName: String): UploadRecord? {
        return transactionProvider.read { conn ->
                conn.prepareStatement(
                    sql.query("uploads.select_upload_by_object_name")
                ).use { stmt ->
                    stmt.setString(1, objectName)
                    stmt.executeQuery().use { rs ->
                        if (!rs.next()) return@read null
                        toUploadRecord(rs)
                    }
                }
        }
    }

    override suspend fun markCompleted(
        uploadId: String,
        generation: Long,
        sizeBytes: Long,
        contentType: String?,
        completedAtTimestamp: Long,
        eventTimeEpochSeconds: Long?,
    ) {
        transactionProvider.write { conn ->
                conn.prepareStatement(
                    sql.query("uploads.mark_upload_completed")
                ).use { stmt ->
                    stmt.setLong(1, sizeBytes)
                    stmt.setString(2, contentType)
                    stmt.setString(3, uploadId)
                    stmt.executeUpdate()
                }
        }
    }

    private fun toUploadRecord(rs: java.sql.ResultSet): UploadRecord =
        UploadRecord(
            uploadId = rs.getString("upload_id"),
            assetId = rs.getString("upload_id"),
            uid = rs.getString("uid"),
            type = UploadType.fromWire(rs.getString("type")),
            entityId = rs.getString("entity_id"),
            bucket = rs.getString("bucket"),
            objectName = rs.getString("object_name"),
            contentType = rs.getString("content_type"),
            sizeBytes = rs.getLong("size_bytes"),
            status = UploadStatus.entries.firstOrNull { it.wire == rs.getString("status") }
                ?: throw IllegalArgumentException("Unsupported upload status: ${rs.getString("status")}"),
            createdAtTimestamp = (rs.getAppTimestamp("created_at") ?: error("uploads.created_at is null")).toEpochMilli(),
            expiresAtTimestamp = (rs.getAppTimestamp("expires_at") ?: error("uploads.expires_at is null")).toEpochMilli(),
        )
}

package com.appforge.server.services.recordings.repository

import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.infrastructure.sql.NamedSql
import com.appforge.server.infrastructure.time.getAppTimestamp
import com.appforge.server.providers.transaction.SqlTransactionProvider
import com.appforge.server.providers.transaction.TransactionProvider
import com.appforge.server.services.recordings.RecordingContent
import com.appforge.server.services.recordings.RecordingMetadata
import java.sql.Types

interface RecordingRepository {
    suspend fun create(
        id: String,
        uid: String,
        audioBytes: ByteArray,
        contentType: String,
        durationSeconds: Int?,
    ): RecordingMetadata

    suspend fun listByUser(uid: String, limit: Int): List<RecordingMetadata>
    suspend fun getByIdAndUser(id: String, uid: String): RecordingContent?
    suspend fun existsByIdAndUser(id: String, uid: String): Boolean
}

class SqlRecordingRepository(
    sqlDatabase: ExposedDatabase,
) : RecordingRepository {
    private val sql = NamedSql.fromResource(
        resourcePath = "com/appforge/server/services/recordings/recordings.sql",
        classLoader = SqlRecordingRepository::class.java.classLoader,
    )
    private val transactionProvider: TransactionProvider = SqlTransactionProvider(sqlDatabase)

    override suspend fun create(
        id: String,
        uid: String,
        audioBytes: ByteArray,
        contentType: String,
        durationSeconds: Int?,
    ): RecordingMetadata {
        val sizeBytes = audioBytes.size.toLong()
        transactionProvider.write { conn ->
            conn.prepareStatement(sql.query("recordings.insert")).use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, uid)
                stmt.setBytes(3, audioBytes)
                stmt.setString(4, contentType)
                stmt.setLong(5, sizeBytes)
                if (durationSeconds == null) {
                    stmt.setNull(6, Types.INTEGER)
                } else {
                    stmt.setInt(6, durationSeconds)
                }
                stmt.executeUpdate()
            }
        }
        return getByIdAndUser(id, uid)?.metadata
            ?: error("Recording was inserted but could not be reloaded: $id")
    }

    override suspend fun listByUser(uid: String, limit: Int): List<RecordingMetadata> {
        return transactionProvider.read { conn ->
            conn.prepareStatement(sql.query("recordings.list_by_uid")).use { stmt ->
                stmt.setString(1, uid)
                stmt.setInt(2, limit)
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                RecordingMetadata(
                                    id = rs.getString("id"),
                                    uid = rs.getString("uid"),
                                    contentType = rs.getString("content_type"),
                                    sizeBytes = rs.getLong("size_bytes"),
                                    durationSeconds = rs.getInt("duration_seconds").let { if (rs.wasNull()) null else it },
                                    createdAt = rs.getAppTimestamp("created_at")
                                        ?: error("recordings.created_at is null"),
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    override suspend fun getByIdAndUser(id: String, uid: String): RecordingContent? {
        return transactionProvider.read { conn ->
            conn.prepareStatement(sql.query("recordings.select_content_by_id_and_uid")).use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, uid)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return@read null
                    val metadata = RecordingMetadata(
                        id = rs.getString("id"),
                        uid = rs.getString("uid"),
                        contentType = rs.getString("content_type"),
                        sizeBytes = rs.getLong("size_bytes"),
                        durationSeconds = rs.getInt("duration_seconds").let { if (rs.wasNull()) null else it },
                        createdAt = rs.getAppTimestamp("created_at")
                            ?: error("recordings.created_at is null"),
                    )
                    RecordingContent(
                        metadata = metadata,
                        audioBytes = rs.getBytes("audio_bytes"),
                    )
                }
            }
        }
    }

    override suspend fun existsByIdAndUser(id: String, uid: String): Boolean {
        return transactionProvider.read { conn ->
            conn.prepareStatement(sql.query("recordings.select_exists_by_id_and_uid")).use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, uid)
                stmt.executeQuery().use { rs -> rs.next() }
            }
        }
    }
}

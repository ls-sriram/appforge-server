package com.appforge.server.services.documents.repository

import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.infrastructure.sql.NamedSql
import com.appforge.server.infrastructure.time.getAppTimestamp
import com.appforge.server.providers.transaction.SqlTransactionProvider
import com.appforge.server.providers.transaction.TransactionProvider
import com.appforge.server.services.documents.DocumentModel

interface DocumentRepository {
    suspend fun upsert(
        id: String,
        ownerUid: String,
        title: String,
        tag: String,
        version: String,
        content: String,
    ): DocumentModel

    suspend fun getByIdAndOwner(id: String, ownerUid: String): DocumentModel?
    suspend fun listByOwner(ownerUid: String, limit: Int): List<DocumentModel>
    suspend fun existsByIdAndOwner(id: String, ownerUid: String): Boolean
}

class SqlDocumentRepository(
    sqlDatabase: ExposedDatabase,
) : DocumentRepository {
    private val sql = NamedSql.fromResource(
        resourcePath = "com/appforge/server/services/documents/documents.sql",
        classLoader = SqlDocumentRepository::class.java.classLoader,
    )
    private val tx: TransactionProvider = SqlTransactionProvider(sqlDatabase)

    override suspend fun upsert(
        id: String,
        ownerUid: String,
        title: String,
        tag: String,
        version: String,
        content: String,
    ): DocumentModel {
        val contentLength = content.length
        tx.write { conn ->
            conn.prepareStatement(sql.query("documents.upsert")).use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, ownerUid)
                stmt.setString(3, title)
                stmt.setString(4, tag)
                stmt.setString(5, version)
                stmt.setString(6, content)
                stmt.setInt(7, contentLength)
                stmt.executeUpdate()
            }
        }
        return getByIdAndOwner(id, ownerUid)
            ?: error("Document was upserted but could not be reloaded: $id")
    }

    override suspend fun getByIdAndOwner(id: String, ownerUid: String): DocumentModel? {
        return tx.read { conn ->
            conn.prepareStatement(sql.query("documents.select_by_id_and_owner")).use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, ownerUid)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return@read null
                    DocumentModel(
                        id = rs.getString("id"),
                        ownerUid = rs.getString("owner_uid"),
                        title = rs.getString("title"),
                        tag = rs.getString("tag"),
                        version = rs.getString("version"),
                        content = rs.getString("content"),
                        contentLength = rs.getInt("content_length"),
                        createdAt = rs.getAppTimestamp("created_at")
                            ?: error("documents.created_at is null"),
                        updatedAt = rs.getAppTimestamp("updated_at")
                            ?: error("documents.updated_at is null"),
                    )
                }
            }
        }
    }

    override suspend fun listByOwner(ownerUid: String, limit: Int): List<DocumentModel> {
        return tx.read { conn ->
            conn.prepareStatement(sql.query("documents.list_by_owner")).use { stmt ->
                stmt.setString(1, ownerUid)
                stmt.setInt(2, limit)
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                DocumentModel(
                                    id = rs.getString("id"),
                                    ownerUid = rs.getString("owner_uid"),
                                    title = rs.getString("title"),
                                    tag = rs.getString("tag"),
                                    version = rs.getString("version"),
                                    content = rs.getString("content"),
                                    contentLength = rs.getInt("content_length"),
                                    createdAt = rs.getAppTimestamp("created_at")
                                        ?: error("documents.created_at is null"),
                                    updatedAt = rs.getAppTimestamp("updated_at")
                                        ?: error("documents.updated_at is null"),
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    override suspend fun existsByIdAndOwner(id: String, ownerUid: String): Boolean {
        return tx.read { conn ->
            conn.prepareStatement(sql.query("documents.select_exists_by_id_and_owner")).use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, ownerUid)
                stmt.executeQuery().use { rs -> rs.next() }
            }
        }
    }
}

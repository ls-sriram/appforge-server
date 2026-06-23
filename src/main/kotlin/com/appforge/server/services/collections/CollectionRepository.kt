package com.appforge.server.services.collections

import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.infrastructure.sql.NamedSql
import com.appforge.server.infrastructure.time.getAppTimestamp
import com.appforge.server.providers.transaction.SqlTransactionProvider
import com.appforge.server.providers.transaction.TransactionProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

interface CollectionRepository {
    suspend fun create(record: CollectionRecordModel): CollectionRecordModel
    suspend fun getByIdAndOwner(id: String, appId: String, collection: String, ownerUid: String): CollectionRecordModel?
    suspend fun listByOwner(appId: String, collection: String, ownerUid: String, limit: Int): List<CollectionRecordModel>
    suspend fun update(record: CollectionRecordModel): CollectionRecordModel
    suspend fun deleteByIdAndOwner(id: String, appId: String, collection: String, ownerUid: String): Boolean
}

class SqlCollectionRepository(
    sqlDatabase: ExposedDatabase,
) : CollectionRepository {

    private val sql = NamedSql.fromResource(
        resourcePath = "com/appforge/server/services/collections/collections.sql",
        classLoader = SqlCollectionRepository::class.java.classLoader,
    )
    private val tx: TransactionProvider = SqlTransactionProvider(sqlDatabase)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun create(record: CollectionRecordModel): CollectionRecordModel {
        tx.write { conn ->
            conn.prepareStatement(sql.query("collections.insert")).use { stmt ->
                stmt.setString(1, record.id)
                stmt.setString(2, record.appId)
                stmt.setString(3, record.collection)
                stmt.setString(4, record.ownerUid)
                stmt.setString(5, record.data.toString())
                stmt.executeUpdate()
            }
        }
        return getByIdAndOwner(record.id, record.appId, record.collection, record.ownerUid)
            ?: error("Collection record not found after insert: ${record.id}")
    }

    override suspend fun getByIdAndOwner(id: String, appId: String, collection: String, ownerUid: String): CollectionRecordModel? {
        return tx.read { conn ->
            conn.prepareStatement(sql.query("collections.select_by_id")).use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, appId)
                stmt.setString(3, collection)
                stmt.setString(4, ownerUid)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return@read null
                    fromResultSet(rs)
                }
            }
        }
    }

    override suspend fun listByOwner(appId: String, collection: String, ownerUid: String, limit: Int): List<CollectionRecordModel> {
        return tx.read { conn ->
            conn.prepareStatement(sql.query("collections.list")).use { stmt ->
                stmt.setString(1, appId)
                stmt.setString(2, collection)
                stmt.setString(3, ownerUid)
                stmt.setInt(4, limit)
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(fromResultSet(rs))
                    }
                }
            }
        }
    }

    override suspend fun update(record: CollectionRecordModel): CollectionRecordModel {
        tx.write { conn ->
            conn.prepareStatement(sql.query("collections.update")).use { stmt ->
                stmt.setString(1, record.data.toString())
                stmt.setString(2, record.id)
                stmt.setString(3, record.appId)
                stmt.setString(4, record.collection)
                stmt.setString(5, record.ownerUid)
                stmt.executeUpdate()
            }
        }
        return getByIdAndOwner(record.id, record.appId, record.collection, record.ownerUid)
            ?: error("Collection record not found after update: ${record.id}")
    }

    override suspend fun deleteByIdAndOwner(id: String, appId: String, collection: String, ownerUid: String): Boolean {
        return tx.write { conn ->
            conn.prepareStatement(sql.query("collections.delete")).use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, appId)
                stmt.setString(3, collection)
                stmt.setString(4, ownerUid)
                stmt.executeUpdate() > 0
            }
        }
    }

    private fun fromResultSet(rs: java.sql.ResultSet): CollectionRecordModel {
        val dataJson = rs.getString("data_json") ?: "{}"
        val data = runCatching {
            json.parseToJsonElement(dataJson) as? JsonObject
        }.getOrNull() ?: JsonObject(emptyMap())
        return CollectionRecordModel(
            id = rs.getString("id"),
            appId = rs.getString("app_id"),
            collection = rs.getString("collection"),
            ownerUid = rs.getString("owner_uid"),
            data = data,
            createdAt = rs.getAppTimestamp("created_at") ?: error("custom_collections.created_at is null"),
            updatedAt = rs.getAppTimestamp("updated_at") ?: error("custom_collections.updated_at is null"),
        )
    }
}

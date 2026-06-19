package com.appforge.server.services.uploads.repository

import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.infrastructure.Repository
import com.appforge.server.infrastructure.Resource
import com.appforge.server.infrastructure.SortDirection
import com.appforge.server.infrastructure.sql.NamedSql
import com.appforge.server.providers.transaction.SqlTransactionProvider
import com.appforge.server.providers.transaction.TransactionProvider

class ProcessedUploadEventsRepository(
    sqlDatabase: ExposedDatabase,
) : Repository<Map<String, Any?>> {
    private val sql = NamedSql.fromResource(
        resourcePath = "com/appforge/server/services/uploads/uploads.sql",
        classLoader = ProcessedUploadEventsRepository::class.java.classLoader,
    )
    private val transactionProvider: TransactionProvider = SqlTransactionProvider(sqlDatabase)

    override suspend fun setIfAbsent(id: String, initial: Map<String, Any?>): Resource<Boolean> {
        return try {
            val inserted = transactionProvider.write { conn ->
                conn.prepareStatement(
                    sql.query("uploads.insert_processed_event_once")
                ).use { stmt ->
                    stmt.setString(1, id)
                    stmt.setString(2, initial["bucket"] as? String)
                    stmt.setString(3, initial["objectName"] as? String)
                    stmt.setLong(4, (initial["generation"] as? Number)?.toLong() ?: 0L)
                    stmt.setLong(5, (initial["sizeBytes"] as? Number)?.toLong() ?: 0L)
                    stmt.setString(6, initial["contentType"] as? String)
                    val eventTime = (initial["eventTimeEpochSeconds"] as? Number)?.toLong()
                    if (eventTime == null) stmt.setNull(7, java.sql.Types.BIGINT) else stmt.setLong(7, eventTime)
                    stmt.executeUpdate() > 0
                }
            }
            Resource.Success(inserted)
        } catch (e: Exception) {
            Resource.Error(e)
        }
    }

    override suspend fun create(id: String, data: Map<String, Any?>): Resource<String> =
        Resource.Error(UnsupportedOperationException("Not used"))

    override suspend fun get(id: String): Resource<Map<String, Any?>> =
        Resource.Error(UnsupportedOperationException("Not used"))

    override suspend fun update(id: String, data: Map<String, Any?>): Resource<Unit> =
        Resource.Error(UnsupportedOperationException("Not used"))

    override suspend fun delete(id: String): Resource<Unit> =
        Resource.Error(UnsupportedOperationException("Not used"))

    override suspend fun merge(id: String, update: Map<String, Any?>): Resource<Unit> =
        Resource.Error(UnsupportedOperationException("Not used"))

    override suspend fun findFirstByField(fieldName: String, value: Any): Resource<Map<String, Any?>?> =
        Resource.Error(UnsupportedOperationException("Not used"))

    override suspend fun query(
        filters: Map<String, Any?>,
        orderBy: String?,
        direction: SortDirection,
        limit: Long?,
    ): Resource<List<Map<String, Any?>>> =
        Resource.Error(UnsupportedOperationException("Not used"))
}

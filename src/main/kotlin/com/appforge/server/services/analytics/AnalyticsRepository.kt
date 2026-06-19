package com.appforge.server.services.analytics

import com.appforge.server.infrastructure.Database
import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.infrastructure.sql.NamedSql
import com.appforge.server.services.analytics.models.ApiCallRecord
import org.slf4j.LoggerFactory
import com.appforge.server.providers.identifier.IdentifierProvider
import com.appforge.server.providers.transaction.SqlTransactionProvider
import com.appforge.server.providers.transaction.TransactionProvider

/**
 * Repository for API call analytics using the platform Database interface.
 * Stores records in the `api_calls` collection.
 */
class AnalyticsRepository(
    database: Database,
) {
    private val logger = LoggerFactory.getLogger(AnalyticsRepository::class.java)
    private val transactionProvider: TransactionProvider =
        SqlTransactionProvider(database as? ExposedDatabase ?: error("AnalyticsRepository requires ExposedDatabase"))
    private val sql = NamedSql.fromResource(
        resourcePath = "com/appforge/server/services/analytics/analytics.sql",
        classLoader = AnalyticsRepository::class.java.classLoader,
    )

    suspend fun insert(record: ApiCallRecord) {
        val id = IdentifierProvider.newUuid()
        try {
            transactionProvider.write { conn ->
                conn.prepareStatement(
                    sql.query("analytics.insert_api_call")
                ).use { stmt ->
                    stmt.setString(1, id)
                    stmt.setString(2, record.appId)
                    stmt.setString(3, record.requestId)
                    stmt.setLong(4, record.timestamp)
                    stmt.setString(5, record.method)
                    stmt.setString(6, record.path)
                    stmt.setString(7, record.rawPath)
                    stmt.setInt(8, record.status)
                    stmt.setDouble(9, record.durationMs.toDouble())
                    stmt.setString(10, record.userId)
                    stmt.setString(11, record.teamId)
                    stmt.setString(12, record.userAgent)
                    stmt.setString(13, record.error)
                    stmt.executeUpdate()
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to store analytics record: ${e.message}")
        }
    }

    suspend fun getAllSince(sinceTimestamp: Double): List<Map<String, Any?>> {
        return transactionProvider.read { conn ->
            conn.prepareStatement(
                sql.query("analytics.select_api_calls_since")
            ).use { stmt ->
                stmt.setDouble(1, sinceTimestamp)
                stmt.executeQuery().use { rs ->
                    val out = mutableListOf<Map<String, Any?>>()
                    while (rs.next()) {
                        out.add(
                            mapOf(
                                "requestId" to rs.getString("request_id"),
                                "timestamp" to rs.getDouble("ts"),
                                "method" to rs.getString("method"),
                                "path" to rs.getString("path"),
                                "rawPath" to rs.getString("raw_path"),
                                "status" to rs.getInt("status").toDouble(),
                                "durationMs" to rs.getDouble("duration_ms"),
                                "userId" to rs.getString("user_id"),
                                "appId" to rs.getString("app_id"),
                                "teamId" to rs.getString("team_id"),
                                "userAgent" to rs.getString("user_agent"),
                                "error" to rs.getString("error"),
                            )
                        )
                    }
                    out
                }
            }
        }
    }

    /**
     * Initialize the analytics collection with a seed document.
     * Silently fails if it already exists.
     */
    suspend fun initializeCollection() {
        // No-op for SQL table-backed analytics.
    }
}

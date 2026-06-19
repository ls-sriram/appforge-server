package com.appforge.server.domain.platform_tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * ─────────────────────────────────────────────────────────────────
 * API_CALLS — Platform-level request analytics.
 * Tracks all API requests across all applications.
 * ─────────────────────────────────────────────────────────────────
 */
object ApiCalls : Table("platform_api_calls") {
    val id = varchar("id", 255).entityId()
    val appId = varchar("app_id", 100)
    val requestId = varchar("request_id", 255).nullable()
    val timestamp = timestamp("timestamp")
    val method = varchar("method", 10)
    val path = varchar("path", 500)
    val rawPath = varchar("raw_path", 1000)
    val status = integer("status")
    val durationMs = double("duration_ms")
    val userId = varchar("user_id", 255).nullable()
    val teamId = varchar("team_id", 255).nullable()
    val userAgent = text("user_agent").nullable()
    val error = text("error").nullable()

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_platform_api_timestamp", false, timestamp)
        index("idx_platform_api_app", false, appId)
        index("idx_platform_api_user", false, userId)
        index("idx_platform_api_path", false, path)
    }
}

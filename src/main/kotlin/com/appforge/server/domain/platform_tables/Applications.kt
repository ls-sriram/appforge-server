package com.appforge.server.domain.platform_tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * ─────────────────────────────────────────────────────────────────
 * APPLICATIONS — Platform-level app registry.
 * Tracks all applications deployed on this platform instance.
 * ─────────────────────────────────────────────────────────────────
 */
object Applications : Table("platform_applications") {
    val appId = varchar("app_id", 100).entityId()
    val name = varchar("name", 255)
    val config = text("config")
    val status = varchar("status", 50).default("active")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(appId)

    init {
        index("idx_platform_apps_status", false, status)
    }
}

package com.appforge.server.domain.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * ─────────────────────────────────────────────────────────────────
 * EARLY_ACCESS_ENTRIES — App-level waitlist/approval.
 * Each app manages its own early access list.
 * ─────────────────────────────────────────────────────────────────
 */
object EarlyAccessEntries : Table("early_access_entries") {
    val email = varchar("email", 255).entityId()
    val status = varchar("status", 50).default("waitlist")
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at").nullable()
    val approvedAt = timestamp("approved_at").nullable()

    override val primaryKey = PrimaryKey(email)

    init {
        index("idx_early_access_status", false, status)
    }
}

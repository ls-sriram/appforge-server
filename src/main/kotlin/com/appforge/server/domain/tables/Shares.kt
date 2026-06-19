package com.appforge.server.domain.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * ─────────────────────────────────────────────────────────────────
 * SHARES — Share links within this application.
 * ─────────────────────────────────────────────────────────────────
 */
object Shares : Table("shares") {
    val token = varchar("token", 255).entityId()
    val entityId = varchar("entity_id", 255)
    val entityCategory = varchar("entity_category", 100)
    val entityPath = varchar("entity_path", 500).nullable()
    val ownerUid = varchar("owner_uid", 255)
    val expiresAt = timestamp("expires_at")
    val revokedAt = timestamp("revoked_at").nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(token)

    init {
        index("idx_shares_owner", false, ownerUid)
        index("idx_shares_entity", false, entityId, entityCategory)
        index("idx_shares_expires", false, expiresAt)
    }
}

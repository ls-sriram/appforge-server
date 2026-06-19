package com.appforge.server.domain.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * ─────────────────────────────────────────────────────────────────
 * PROFILES — App-level user profiles keyed by uid.
 * Each application manages its own profiles.
 * ─────────────────────────────────────────────────────────────────
 */
object Profiles : Table("profiles") {
    val userId = varchar("user_id", 255).entityId()
    val displayName = varchar("display_name", 255).nullable()
    val email = varchar("email", 255)
    val emailNormalized = varchar("email_normalized", 255)
    val lastSeenAt = timestamp("last_seen_at").nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(userId)

    init {
        uniqueIndex("uq_profiles_user_id", userId)
        index("idx_profiles_user_id", false, userId)
        uniqueIndex("uq_profiles_email", emailNormalized)
        index("idx_profiles_email_normalized", false, emailNormalized)
    }
}

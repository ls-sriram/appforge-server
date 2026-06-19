package com.appforge.server.domain.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * ─────────────────────────────────────────────────────────────────
 * APP_USERS — User identity for this application.
 * uid = Firebase Auth UID (unique within this app's database).
 * The database connection IS the security boundary.
 * ─────────────────────────────────────────────────────────────────
 */
object AppUsers : Table("app_users") {
    val uid = varchar("uid", 255).entityId()
    val email = varchar("email", 255)
    val emailNormalized = varchar("email_normalized", 255)
    val displayName = varchar("display_name", 255).nullable()
    val createdAt = timestamp("created_at")
    val lastLoginAt = timestamp("last_login_at").nullable()

    override val primaryKey = PrimaryKey(uid)

    init {
        uniqueIndex("uq_app_users_email", emailNormalized)
        index("idx_app_users_created", false, createdAt)
    }
}

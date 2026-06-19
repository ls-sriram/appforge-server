package com.appforge.server.domain.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * ─────────────────────────────────────────────────────────────────
 * REVIEWS — Per-user reviews with RLS.
 * ─────────────────────────────────────────────────────────────────
 */
object Reviews : Table("reviews") {
    val id = varchar("id", 255)
    val ownerUid = varchar("owner_uid", 255)
    val entityId = varchar("entity_id", 255)
    val entityCategory = varchar("entity_category", 100)
    val entityType = varchar("entity_type", 100)
    val authorRole = varchar("author_role", 50)
    val authorId = varchar("author_id", 255).nullable()
    val authorName = varchar("author_name", 255).nullable()
    val authorEmail = varchar("author_email", 255).nullable()
    val content = text("content")
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id, ownerUid)

    init {
        index("idx_reviews_user_entity", false, ownerUid, entityId, entityCategory)
        index("idx_reviews_user_created", false, ownerUid, createdAt)
    }
}

package com.appforge.server.services.reviews.models

import com.appforge.server.infrastructure.time.*

enum class ReviewAuthorRole(val wire: String) {
    AI("ai"),
    EXTERNAL("external"),
    SELF("self");

    companion object {
        fun fromWire(wire: String): ReviewAuthorRole =
            values().find { it.wire == wire } ?: EXTERNAL
    }
}

data class Review(
    val id: String,
    val entityId: String,
    val entityCategory: EntityCategory,
    val authorRole: ReviewAuthorRole,
    val authorId: String?, // Link to Profile id or auth uid
    val authorName: String?,
    val authorEmail: String?,
    val content: Map<String, Any?>,
    val createdAt: AppTimestamp
)

package com.appforge.server.services.reviews.models

import com.appforge.server.utils.DocReader
import com.appforge.server.utils.Mapper
import com.appforge.server.infrastructure.time.*

object ReviewMapper : Mapper<Review, Map<String, Any?>> {
    object Fields {
        const val AuthorRole = "authorRole"
        const val AuthorId = "authorId"
        const val AuthorName = "authorName"
        const val AuthorEmail = "authorEmail"
        const val Content = "content"
        const val CreatedAtTimestamp = "createdAtTimestamp"
    }

    override fun toDoc(domain: Review): Map<String, Any?> =
        mapOf(
            "entityId" to domain.entityId,
            "entityCategory" to domain.entityCategory.value,
            "entityType" to domain.entityCategory.value,
            Fields.AuthorRole to domain.authorRole.wire,
            Fields.AuthorId to domain.authorId,
            Fields.AuthorName to domain.authorName,
            Fields.AuthorEmail to domain.authorEmail,
            Fields.Content to domain.content,
            Fields.CreatedAtTimestamp to domain.createdAt.toEpochMilli(),
        )

    override fun fromDoc(id: String, doc: Map<String, Any?>): Review {
        val r = DocReader(doc, id)
        val createdAt = timestampFromEpochMilli(r.long(Fields.CreatedAtTimestamp))
        return Review(
            id = id,
            entityId = r.string("entityId"),
            entityCategory = EntityCategory.fromWire(r.string("entityCategory")),
            authorRole = ReviewAuthorRole.fromWire(r.string(Fields.AuthorRole)),
            authorId = r.optionalString(Fields.AuthorId),
            authorName = r.optionalString(Fields.AuthorName),
            authorEmail = r.optionalString(Fields.AuthorEmail),
            content = r.map(Fields.Content),
            createdAt = createdAt
        )
    }
}

package com.appforge.server.services.sharing.models

import com.appforge.server.services.reviews.models.EntityCategory
import com.appforge.server.utils.DocReader
import com.appforge.server.utils.Mapper
import com.appforge.server.infrastructure.time.*

object ShareMapper : Mapper<Share, Map<String, Any?>> {
    object Fields {
        const val AccessMode = "accessMode"
        const val EntityId = "entityId"
        const val EntityCategory = "entityCategory"
        const val OwnerId = "ownerId"
        const val TokenHash = "tokenHash"
        const val CreatedAtTimestamp = "createdAtTimestamp" // epoch millis
        const val CreatedBy = "createdBy"
        const val ExpiresAtTimestamp = "expiresAtTimestamp" // epoch millis
        const val RevokedAtTimestamp = "revokedAtTimestamp" // epoch millis
        const val RevokedBy = "revokedBy"
    }

    override fun toDoc(domain: Share): Map<String, Any?> =
        mapOf(
            "id" to domain.id,
            "token" to domain.token,
            Fields.AccessMode to domain.accessMode.wire,
            Fields.EntityId to domain.entityId,
            Fields.EntityCategory to domain.entityCategory.value,
            Fields.OwnerId to domain.ownerId,
            Fields.TokenHash to domain.tokenHash,
            Fields.CreatedAtTimestamp to domain.createdAt.toEpochMilli(),
            Fields.CreatedBy to domain.createdBy,
            Fields.ExpiresAtTimestamp to domain.expiresAt?.toEpochMilli(),
            Fields.RevokedAtTimestamp to domain.revokedAt?.toEpochMilli(),
            Fields.RevokedBy to domain.revokedBy,
        )

    override fun fromDoc(id: String, doc: Map<String, Any?>): Share {
        val r = DocReader(doc, id)
        return Share(
            id = r.optionalString("id") ?: id,
            token = id,
            accessMode = ShareAccessMode.fromWire(r.optionalString(Fields.AccessMode) ?: ShareAccessMode.PUBLIC_LINK.wire),
            entityId = r.string(Fields.EntityId),
            entityCategory = EntityCategory.fromWire(r.string(Fields.EntityCategory)),
            ownerId = r.string(Fields.OwnerId),
            tokenHash = r.string(Fields.TokenHash),
            createdAt = timestampFromEpochMilli(r.long(Fields.CreatedAtTimestamp)),
            createdBy = r.optionalString(Fields.CreatedBy) ?: r.string(Fields.OwnerId),
            expiresAt = r.optionalLong(Fields.ExpiresAtTimestamp)?.let(AppTimestamp::ofEpochMilli),
            revokedAt = r.optionalLong(Fields.RevokedAtTimestamp)?.let(AppTimestamp::ofEpochMilli),
            revokedBy = r.optionalString(Fields.RevokedBy),
        )
    }
}

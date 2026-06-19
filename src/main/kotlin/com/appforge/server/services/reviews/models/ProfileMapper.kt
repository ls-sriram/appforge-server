package com.appforge.server.services.reviews.models

import com.appforge.server.utils.DocReader
import com.appforge.server.utils.Mapper

object ProfileMapper : Mapper<Profile, Map<String, Any?>> {
    object Fields {
        const val DisplayName = "displayName"
        const val Email = "email"
        const val EmailNormalized = "emailNormalized"
        const val LastSeenAt = "lastSeenAt"
    }

    override fun toDoc(domain: Profile): Map<String, Any?> =
        mapOf(
            Fields.DisplayName to domain.displayName,
            Fields.Email to domain.email,
            Fields.EmailNormalized to domain.emailNormalized,
            Fields.LastSeenAt to domain.lastSeenAt.toString()
        )

    override fun fromDoc(id: String, doc: Map<String, Any?>): Profile {
        val r = DocReader(doc, id)
        return Profile(
            userId = id,
            displayName = r.string(Fields.DisplayName),
            email = r.string(Fields.Email),
            emailNormalized = r.string(Fields.EmailNormalized),
            lastSeenAt = r.instant(Fields.LastSeenAt)
        )
    }
}

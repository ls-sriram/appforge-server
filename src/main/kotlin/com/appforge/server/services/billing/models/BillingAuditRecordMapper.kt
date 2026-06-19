package com.appforge.server.services.billing.models

import com.appforge.server.utils.DocReader
import com.appforge.server.utils.Mapper

object BillingAuditRecordMapper : Mapper<BillingAuditRecord, Map<String, Any?>> {
    object Fields {
        const val Payload = "payload"
        const val Timestamp = "timestamp"
        const val WebhookId = "webhookId"
        const val Source = "source"
    }

    override fun toDoc(domain: BillingAuditRecord): Map<String, Any?> =
            mapOf(
                    Fields.Payload to domain.payload,
                    Fields.Timestamp to domain.timestamp.toString(),
                    Fields.WebhookId to domain.webhookId,
                    Fields.Source to domain.source,
            )

    override fun fromDoc(id: String, doc: Map<String, Any?>): BillingAuditRecord {
        val r = DocReader(doc, id)
        return BillingAuditRecord(
                payload = r.string(Fields.Payload),
                timestamp = r.instant(Fields.Timestamp),
                webhookId = r.optionalString(Fields.WebhookId),
                source = r.string(Fields.Source),
        )
    }
}

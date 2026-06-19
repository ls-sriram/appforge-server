package com.appforge.server.services.billing.models

import com.appforge.server.utils.DocReader
import com.appforge.server.utils.Mapper

object PaymentRecordMapper : Mapper<PaymentRecord, Map<String, Any?>> {
    object Fields {
        const val Date = "date"
        const val AmountCents = "amountCents"
        const val Currency = "currency"
        const val PlanId = "planId"
        const val EmailSentAt = "emailSentAt"
    }

    override fun toDoc(domain: PaymentRecord): Map<String, Any?> =
            mapOf(
                    Fields.Date to domain.date.toString(),
                    Fields.AmountCents to domain.amountCents,
                    Fields.Currency to domain.currency,
                    Fields.PlanId to domain.planId,
                    Fields.EmailSentAt to domain.emailSentAt?.toString(),
            )

    override fun fromDoc(id: String, doc: Map<String, Any?>): PaymentRecord {
        val r = DocReader(doc, id)
        return PaymentRecord(
                date = r.instant(Fields.Date),
                amountCents = r.long(Fields.AmountCents),
                currency = r.string(Fields.Currency),
                planId = r.string(Fields.PlanId),
                emailSentAt = doc[Fields.EmailSentAt]?.let { r.instant(Fields.EmailSentAt) }
        )
    }
}

package com.appforge.server.services.billing.models

import com.appforge.server.services.billing.models.BillingFeature
import com.appforge.server.services.billing.models.BillingPaymentType
import com.appforge.server.services.billing.models.BillingSource
import com.appforge.server.services.billing.models.BillingStatus
import com.appforge.server.services.billing.models.Plan
import com.appforge.server.utils.DocReader
import com.appforge.server.utils.Mapper
import com.appforge.server.utils.wireEnumFrom
import com.appforge.server.infrastructure.time.*

object BillingEntitlementMapper : Mapper<BillingEntitlement, Map<String, Any?>> {
    object Fields {
        const val CustomerId = "customerId"
        const val Plan = "plan"
        const val Status = "status"
        const val ExpiresAtTimestamp = "expiresAtTimestamp" // epoch millis
        const val StartedAtTimestamp = "startedAtTimestamp" // epoch millis
        const val Features = "features"
        const val Source = "source"
        const val ExternalCustomerId = "externalCustomerId"
        const val ExternalReferenceId = "externalReferenceId"
        const val BillingType = "billingType"
        const val LastPaymentAmountCents = "lastPaymentAmountCents"
        const val LastPaymentCurrency = "lastPaymentCurrency"
        const val CreatedAtTimestamp = "createdAtTimestamp" // epoch millis
        const val UpdatedAtTimestamp = "updatedAtTimestamp" // epoch millis
    }

    private object FeatureFields {
        const val Limit = "limit"
        const val Used = "used"
        const val Unlocked = "unlocked"
    }

    override fun toDoc(domain: BillingEntitlement): Map<String, Any?> =
        mapOf(
            Fields.CustomerId to domain.customerId,
            Fields.Plan to domain.plan.wire,
            Fields.Status to domain.status.wire,
            Fields.ExpiresAtTimestamp to domain.expiresAt?.toEpochMilli(),
            Fields.StartedAtTimestamp to domain.startedAt.toEpochMilli(),
            Fields.Features to domain.features.mapValues { (_, feature) ->
                mapOf(
                    FeatureFields.Limit to feature.limit,
                    FeatureFields.Used to feature.used,
                    FeatureFields.Unlocked to feature.unlocked,
                )
            },
            Fields.Source to domain.source.wire,
            Fields.ExternalCustomerId to domain.externalCustomerId,
            Fields.ExternalReferenceId to domain.externalReferenceId,
            Fields.BillingType to domain.billingType?.wire,
            Fields.LastPaymentAmountCents to domain.lastPaymentAmountCents,
            Fields.LastPaymentCurrency to domain.lastPaymentCurrency,
            Fields.CreatedAtTimestamp to domain.createdAt.toEpochMilli(),
            Fields.UpdatedAtTimestamp to domain.updatedAt.toEpochMilli(),
        )

    override fun fromDoc(id: String, doc: Map<String, Any?>): BillingEntitlement {
        val r = DocReader(doc, id)
        val createdAt = timestampFromEpochMilli(r.long(Fields.CreatedAtTimestamp))
        val updatedAt = timestampFromEpochMilli(r.long(Fields.UpdatedAtTimestamp))

        val externalReferenceId = r.optionalString(Fields.ExternalReferenceId)
        val billingType = billingTypeFromDoc(r.optionalString(Fields.BillingType), externalReferenceId)

        return BillingEntitlement(
            customerId = r.string(Fields.CustomerId),
            plan = planFromRaw(r.string(Fields.Plan)),
            status = r.enum(Fields.Status),
            expiresAt = r.optionalLong(Fields.ExpiresAtTimestamp)?.let(AppTimestamp::ofEpochMilli),
            startedAt = r.optionalLong(Fields.StartedAtTimestamp)?.let(AppTimestamp::ofEpochMilli) ?: createdAt,
            externalCustomerId = r.optionalString(Fields.ExternalCustomerId),
            externalReferenceId = externalReferenceId,
            billingType = billingType,
            lastPaymentAmountCents = r.optionalLong(Fields.LastPaymentAmountCents),
            lastPaymentCurrency = r.optionalString(Fields.LastPaymentCurrency),
            source = r.enum(Fields.Source),
            features = parseFeatures(r.map(Fields.Features)),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    fun updateForOneOff(
        expiresAt: AppTimestamp?,
        updatedAt: AppTimestamp,
        externalReferenceId: String?,
        lastPaymentAmountCents: Long?,
        lastPaymentCurrency: String?,
        source: BillingSource,
        externalCustomerId: String? = null,
    ): Map<String, Any?> =
        mapOf(
            Fields.ExpiresAtTimestamp to expiresAt?.toEpochMilli(),
            Fields.Status to BillingStatus.ACTIVE.wire,
            Fields.Source to source.wire,
            Fields.ExternalReferenceId to externalReferenceId,
            Fields.ExternalCustomerId to externalCustomerId,
            Fields.LastPaymentAmountCents to lastPaymentAmountCents,
            Fields.LastPaymentCurrency to lastPaymentCurrency,
            Fields.UpdatedAtTimestamp to updatedAt.toEpochMilli(),
        )

    fun planFromRaw(raw: String): Plan {
        val normalized = raw.trim().lowercase()
        return when (normalized) {
            Plan.FREE.wire -> Plan.FREE
            Plan.TRIAL.wire -> Plan.TRIAL
            Plan.PRO.wire -> Plan.PRO
            else -> error("Unknown plan: $raw")
        }
    }

    private fun billingTypeFromDoc(
        billingTypeRaw: String?,
        externalReferenceId: String?
    ): BillingPaymentType? {
        if (!billingTypeRaw.isNullOrBlank()) {
            return wireEnumFrom(billingTypeRaw)
        }
        return when {
            externalReferenceId.isNullOrBlank() -> null
            externalReferenceId.startsWith("sub_") -> BillingPaymentType.SUBSCRIPTION
            else -> BillingPaymentType.ONE_TIME
        }
    }

    private fun parseFeatures(raw: Map<String, Any?>): Map<String, BillingFeature> =
        raw.mapValues { (_, value) ->
            val featureMap = (value as? Map<*, *>)?.mapNotNull { (key, entry) ->
                (key as? String)?.let { it to entry }
            }?.toMap().orEmpty()
            val used = (featureMap[FeatureFields.Used] as? Number)?.toLong()
                ?: throw IllegalArgumentException("Billing feature '${FeatureFields.Used}' is required and must be numeric")
            val unlocked = featureMap[FeatureFields.Unlocked] as? Boolean
                ?: throw IllegalArgumentException("Billing feature '${FeatureFields.Unlocked}' is required and must be boolean")
            BillingFeature(
                limit = (featureMap[FeatureFields.Limit] as? Number)?.toLong(),
                used = used,
                unlocked = unlocked,
            )
        }
}

package com.appforge.server.services.billing.models

import com.appforge.server.services.billing.models.BillingFeature
import com.appforge.server.utils.WireEnum
import com.appforge.server.infrastructure.time.*

enum class Plan(override val wire: String, val displayName: String) : WireEnum {
    FREE("free", "Free"),
    TRIAL("trial", "Trial"),
    PRO("pro", "Pro"),
}

enum class BillingStatus(override val wire: String) : WireEnum {
    ACTIVE("active"),
    TRIALING("trialing"),
    CANCEL_PENDING("cancel_pending"),
    PAST_DUE("past_due"),
    CANCELED("canceled"),
}

enum class BillingSource(override val wire: String) : WireEnum {
    DODO_PAYMENTS("dodo_payments"),
    TRIAL("trial"),
    MANUAL("manual"),
}

enum class BillingPaymentType(override val wire: String) : WireEnum {
    SUBSCRIPTION("subscription"),
    ONE_TIME("one_time"),
}

data class BillingEntitlement(
    val customerId: String,
    val plan: Plan,
    val status: BillingStatus,
    val expiresAt: AppTimestamp?, // null = no expiry
    val startedAt: AppTimestamp,
    val externalCustomerId: String? = null,
    val externalReferenceId: String? = null,
    val billingType: BillingPaymentType? = null,
    val lastPaymentAmountCents: Long? = null,
    val lastPaymentCurrency: String? = null,
    val source: BillingSource,
    val features: Map<String, BillingFeature>,
    val createdAt: AppTimestamp,
    val updatedAt: AppTimestamp,
)

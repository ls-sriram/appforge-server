package com.appforge.server.domain.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * ─────────────────────────────────────────────────────────────────
 * BILLING_ENTITLEMENTS — Per-user plan/subscription.
 * ─────────────────────────────────────────────────────────────────
 */
object BillingEntitlements : Table("billing_entitlements") {
    val userId = varchar("user_id", 255).entityId()
    val plan = varchar("plan", 50).default("free")
    val status = varchar("status", 50).default("active")
    val expiresAt = timestamp("expires_at").nullable()
    val startedAt = timestamp("started_at")
    val features = text("features")
    val entitlementSource = varchar("entitlement_source", 50).default("manual")
    val externalCustomerId = varchar("external_customer_id", 255).nullable()
    val externalReferenceId = varchar("external_reference_id", 255).nullable()
    val billingType = varchar("billing_type", 50).nullable()
    val lastPaymentAmountCents = long("last_payment_amount_cents").nullable()
    val lastPaymentCurrency = varchar("last_payment_currency", 10).nullable()
    val createdAt = timestamp("created_at")
    val updatedAt = timestamp("updated_at")

    override val primaryKey = PrimaryKey(userId)

    init {
        index("idx_billing_entitlements_status", false, status)
        index("idx_billing_entitlements_expires", false, expiresAt)
    }
}

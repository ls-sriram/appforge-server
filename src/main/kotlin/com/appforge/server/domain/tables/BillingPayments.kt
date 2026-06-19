package com.appforge.server.domain.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * ─────────────────────────────────────────────────────────────────
 * BILLING_PAYMENTS — Per-user payment history.
 * ─────────────────────────────────────────────────────────────────
 */
object BillingPayments : Table("billing_payments") {
    val id = varchar("id", 255).entityId()
    val userId = varchar("user_id", 255)
    val date = timestamp("date")
    val amountCents = long("amount_cents")
    val currency = varchar("currency", 10)
    val planId = varchar("plan_id", 100)
    val emailSentAt = timestamp("email_sent_at").nullable()
    val createdAt = timestamp("created_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_billing_payments_user", false, userId)
        index("idx_billing_payments_user_date", false, userId, date)
    }
}

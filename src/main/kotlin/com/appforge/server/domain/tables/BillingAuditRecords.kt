package com.appforge.server.domain.tables

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

/**
 * ─────────────────────────────────────────────────────────────────
 * BILLING_AUDIT_RECORDS — App-level webhook audit log.
 * Each app tracks its own payment events.
 * ─────────────────────────────────────────────────────────────────
 */
object BillingAuditRecords : Table("billing_audit_records") {
    val id = varchar("id", 255).entityId()
    val payload = text("payload")
    val timestamp = timestamp("timestamp")
    val webhookId = varchar("webhook_id", 255).nullable()
    val auditSource = varchar("audit_source", 100)

    override val primaryKey = PrimaryKey(id)

    init {
        index("idx_billing_audit_webhook", false, webhookId)
        index("idx_billing_audit_timestamp", false, timestamp)
    }
}

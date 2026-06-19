package com.appforge.server.services.billing.models

import com.appforge.server.infrastructure.time.*

data class BillingAuditRecord(
        val payload: String,
        val timestamp: AppTimestamp,
        val webhookId: String?,
        val source: String,
)

package com.appforge.server.services.billing.models

import com.appforge.server.infrastructure.time.*

data class PaymentRecord(
        val date: AppTimestamp,
        val amountCents: Long,
        val currency: String,
        val planId: String,
        val emailSentAt: AppTimestamp? = null,
)

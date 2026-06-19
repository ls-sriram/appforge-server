package com.appforge.server.services.billing.models

data class BillingFeature(
        val limit: Long? = null,
        val used: Long = 0L,
        val unlocked: Boolean = true,
)

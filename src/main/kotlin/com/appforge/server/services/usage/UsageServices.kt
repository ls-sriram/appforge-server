package com.appforge.server.services.usage

import com.appforge.server.services.billing.BillingAccountService
import com.appforge.server.services.billing.EntitlementService

interface UsageServices {
    val billingAccountService: BillingAccountService
    val entitlementService: EntitlementService
    val usageMetricsService: UsageMetricsService
}

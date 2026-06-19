package com.appforge.server.services.usage

import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.services.billing.BillingAccountService
import com.appforge.server.services.billing.BillingAccountServiceImpl
import com.appforge.server.services.billing.EntitlementService
import com.appforge.server.services.billing.EntitlementServiceImpl
import com.appforge.server.services.billing.repository.BillingRepositoryApi

class UsageProvider(
    private val sqlDatabase: ExposedDatabase,
    private val billingRepository: BillingRepositoryApi,
) : UsageServices {
    override val billingAccountService: BillingAccountService by lazy {
        BillingAccountServiceImpl(billingRepository)
    }

    override val entitlementService: EntitlementService by lazy {
        EntitlementServiceImpl()
    }

    private val usageMetricsRepository: UsageMetricsRepository by lazy {
        SqlUsageMetricsRepository(sqlDatabase = sqlDatabase)
    }

    override val usageMetricsService: UsageMetricsService by lazy {
        UsageMetricsServiceImpl(repository = usageMetricsRepository)
    }
}

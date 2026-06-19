package com.appforge.server.services.billing

import com.appforge.server.services.billing.models.BillingEntitlement
import com.appforge.server.services.billing.repository.BillingRepositoryApi

interface BillingAccountService {
    suspend fun getEntitlement(userId: String): BillingEntitlement?
}

class BillingAccountServiceImpl(
    private val billingRepository: BillingRepositoryApi,
) : BillingAccountService {
    override suspend fun getEntitlement(userId: String): BillingEntitlement? {
        return billingRepository.get(userId)
    }
}


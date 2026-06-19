package com.appforge.server.services.billing.repository

import com.appforge.server.services.billing.models.BillingEntitlement
import com.appforge.server.services.billing.models.PaymentRecord

interface BillingRepositoryApi {
    suspend fun upsert(userId: String, entitlement: BillingEntitlement)
    suspend fun get(userId: String): BillingEntitlement?
    suspend fun getPayment(userId: String, recordId: String): PaymentRecord?
    suspend fun recordPayment(userId: String, record: PaymentRecord, recordId: String? = null)
}

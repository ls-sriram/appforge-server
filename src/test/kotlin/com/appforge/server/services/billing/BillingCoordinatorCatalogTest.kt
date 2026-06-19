package com.appforge.server.services.billing

import com.appforge.server.services.billing.repository.BillingRepositoryApi
import com.appforge.server.services.billing.models.BillingEntitlement
import com.appforge.server.services.billing.models.PaymentRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class BillingCoordinatorCatalogTest {
    @Test
    fun `pricing cards exposes only pro products`() = runBlocking {
        val coordinator = BillingCoordinator(repository = NoopBillingRepository())
        val cards = coordinator.listPricingCards().cards
        assertEquals(2, cards.size)
        val ids = cards.map { it.id }.toSet()
        assertEquals(setOf("pro_monthly", "pro_annual"), ids)
    }

    private class NoopBillingRepository : BillingRepositoryApi {
        override suspend fun upsert(userId: String, entitlement: BillingEntitlement) = Unit
        override suspend fun get(userId: String): BillingEntitlement? = null
        override suspend fun getPayment(userId: String, recordId: String): PaymentRecord? = null
        override suspend fun recordPayment(userId: String, record: PaymentRecord, recordId: String?) = Unit
    }
}

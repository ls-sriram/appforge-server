package com.appforge.server.services.billing.repository

import com.appforge.server.infrastructure.InMemoryRepositoryFactory
import com.appforge.server.services.billing.models.BillingEntitlement
import com.appforge.server.services.billing.models.BillingFeature
import com.appforge.server.services.billing.models.BillingSource
import com.appforge.server.services.billing.models.BillingStatus
import com.appforge.server.services.billing.models.Plan
import com.appforge.server.services.billing.models.PaymentRecord
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BillingRepositoryTest {
    @Test
    fun `upsert and get entitlement`() = kotlinx.coroutines.runBlocking {
        val factory = InMemoryRepositoryFactory()
        val repo = BillingRepository(factory)

	        val entitlement = BillingEntitlement(
	            customerId = "cust-1",
	            plan = Plan.PRO,
	            status = BillingStatus.ACTIVE,
	            expiresAt = Instant.parse("2024-01-01T00:00:00Z"),
	            startedAt = Instant.parse("2023-12-01T00:00:00Z"),
	            features = mapOf("feature" to BillingFeature(limit = 5, used = 1, unlocked = true)),
	            source = BillingSource.DODO_PAYMENTS,
	            externalCustomerId = "ext-1",
	            externalReferenceId = "ref-1",
	            lastPaymentAmountCents = 1000,
	            lastPaymentCurrency = "USD",
	            createdAt = Instant.parse("2023-12-01T00:00:00Z"),
	            updatedAt = Instant.parse("2023-12-02T00:00:00Z")
	        )

        repo.upsert("user1", entitlement)
        val loaded = repo.get("user1")
        assertNotNull(loaded)
        assertEquals(entitlement.customerId, loaded.customerId)
        assertEquals(entitlement.plan, loaded.plan)
    }

    @Test
    fun `recordPayment and getPayment`() = kotlinx.coroutines.runBlocking {
        val factory = InMemoryRepositoryFactory()
        val repo = BillingRepository(factory)

        val record = PaymentRecord(
            date = Instant.parse("2024-01-01T00:00:00Z"),
            amountCents = 1200,
            currency = "USD",
            planId = "pro",
        )

        repo.recordPayment("user1", record, "rec-1")
        val loaded = repo.getPayment("user1", "rec-1")
        assertNotNull(loaded)
        assertEquals(record.amountCents, loaded.amountCents)
        assertEquals(record.currency, loaded.currency)
    }
}

package com.appforge.server.services.billing

import com.appforge.server.config.options.BillingOptions
import com.appforge.server.services.billing.models.BillingAuditRecord
import com.appforge.server.services.billing.models.BillingEntitlement
import com.appforge.server.services.billing.models.BillingSource
import com.appforge.server.services.billing.models.BillingStatus
import com.appforge.server.services.billing.models.PaymentRecord
import com.appforge.server.services.billing.models.Plan
import com.appforge.server.services.billing.repository.BillingAuditRepositoryApi
import com.appforge.server.services.billing.repository.BillingRepositoryApi
import com.appforge.server.clients.FirebaseAdminClient
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking

class SignupEntitlementCoordinatorTest {
    private val fixedNow = Instant.parse("2024-01-01T00:00:00Z")
    private val clock: Clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
    private val billingOptions = BillingOptions(
            trialDurationDays = 7,
            dodoProductIds = mapOf(
                    "pro_monthly" to "p_monthly",
                    "pro_annual" to "p_annual"
            )
    )
    private val repo = InMemoryBillingRepository()
    private val auditRepo = mockk<BillingAuditRepositoryApi>(relaxed = true)

    @Test
    fun `initializes default free entitlement`() = runBlocking {
        val coordinator = SignupEntitlementCoordinator(repo, auditRepo, billingOptions, clock)

        val result = coordinator.initializeDefaultEntitlement("user-1")

	        assertNotNull(result)
	        assertEquals("user-1", result.customerId)
	        assertEquals(Plan.FREE, result.plan)
	        assertEquals(BillingStatus.ACTIVE, result.status)
	        assertEquals(null, result.expiresAt)

        assertEquals(result, repo.store["user-1"])
        coVerify(exactly = 1) { auditRepo.record(any()) }
    }

    @Test
    fun `returns existing entitlement if already present`() = runBlocking {
        val repo = InMemoryBillingRepository()
        val auditRepo = mockk<BillingAuditRepositoryApi>(relaxed = true)
        val coordinator = SignupEntitlementCoordinator(repo, auditRepo, billingOptions, clock)

        val existing = BillingEntitlement(
            customerId = "user-1",
            plan = Plan.PRO,
            status = BillingStatus.ACTIVE,
            expiresAt = fixedNow.plusSeconds(30 * 86400),
            startedAt = fixedNow.minusSeconds(86400),
            source = BillingSource.TRIAL,
            features = emptyMap(),
            createdAt = fixedNow.minusSeconds(86400),
            updatedAt = fixedNow.minusSeconds(86400)
        )
        repo.store["user-1"] = existing

        val result = coordinator.initializeDefaultEntitlement("user-1")

        assertEquals(existing, result)
        assertEquals(existing, repo.store["user-1"])
        coVerify(exactly = 0) { auditRepo.record(any()) }
    }

    private class InMemoryBillingRepository : BillingRepositoryApi {
        val store = mutableMapOf<String, BillingEntitlement>()

        override suspend fun upsert(userId: String, entitlement: BillingEntitlement) {
            store[userId] = entitlement
        }
        override suspend fun get(userId: String): BillingEntitlement? = store[userId]
        override suspend fun getPayment(userId: String, recordId: String): PaymentRecord? = null
        override suspend fun recordPayment(userId: String, record: PaymentRecord, recordId: String?) = Unit
    }
}

package com.appforge.server.services.billing

import com.appforge.server.services.billing.models.BillingEntitlement
import com.appforge.server.services.billing.models.BillingPaymentType
import com.appforge.server.services.billing.models.BillingSource
import com.appforge.server.services.billing.models.BillingStatus
import com.appforge.server.services.billing.models.Plan
import com.appforge.server.services.dodopayments.DodoPaymentsService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFailsWith

class BillingUseCasesTest {
    @Test
    fun `cancel subscription requests provider cancel for active dodo entitlement`() {
        val coordinator = mockk<BillingCoordinator>()
        val dodoService = mockk<DodoPaymentsService>()
        val useCases = BillingUseCasesImpl(coordinator, dodoService) { }

        val entitlement = sampleEntitlement(
            source = BillingSource.DODO_PAYMENTS,
            status = BillingStatus.ACTIVE,
            billingType = BillingPaymentType.SUBSCRIPTION,
            externalReferenceId = "subscription_ref_123"
        )

        coEvery { coordinator.getEntitlement("user-1") } returns entitlement
        every { dodoService.cancelSubscriptionAtPeriodEnd("subscription_ref_123") } returns Unit
        coEvery { coordinator.markSubscriptionCancelPending("user-1") } returns Unit

        useCases.cancelSubscription("user-1")

        coVerify(exactly = 1) { coordinator.getEntitlement("user-1") }
        coVerify(exactly = 1) { coordinator.markSubscriptionCancelPending("user-1") }
        verify(exactly = 1) { dodoService.cancelSubscriptionAtPeriodEnd("subscription_ref_123") }
    }

    @Test
    fun `cancel subscription rejects non subscription entitlement`() {
        val coordinator = mockk<BillingCoordinator>()
        val dodoService = mockk<DodoPaymentsService>()
        val useCases = BillingUseCasesImpl(coordinator, dodoService) { }

        val entitlement = sampleEntitlement(
            source = BillingSource.DODO_PAYMENTS,
            status = BillingStatus.ACTIVE,
            billingType = BillingPaymentType.SUBSCRIPTION,
            externalReferenceId = null
        )

        coEvery { coordinator.getEntitlement("user-1") } returns entitlement

        assertFailsWith<IllegalArgumentException> {
            useCases.cancelSubscription("user-1")
        }

        coVerify(exactly = 0) { coordinator.markSubscriptionCancelPending("user-1") }
        verify(exactly = 0) { dodoService.cancelSubscriptionAtPeriodEnd(any()) }
    }

    @Test
    fun `cancel subscription rejects non-active entitlement`() {
        val coordinator = mockk<BillingCoordinator>()
        val dodoService = mockk<DodoPaymentsService>()
        val useCases = BillingUseCasesImpl(coordinator, dodoService) { }

        val entitlement = sampleEntitlement(
            source = BillingSource.DODO_PAYMENTS,
            status = BillingStatus.CANCELED,
            billingType = BillingPaymentType.SUBSCRIPTION,
            externalReferenceId = "subscription_ref_123"
        )

        coEvery { coordinator.getEntitlement("user-1") } returns entitlement

        assertFailsWith<IllegalArgumentException> {
            useCases.cancelSubscription("user-1")
        }

        coVerify(exactly = 0) { coordinator.markSubscriptionCancelPending("user-1") }
        verify(exactly = 0) { dodoService.cancelSubscriptionAtPeriodEnd(any()) }
    }

    @Test
    fun `cancel subscription rejects one-time entitlement`() {
        val coordinator = mockk<BillingCoordinator>()
        val dodoService = mockk<DodoPaymentsService>()
        val useCases = BillingUseCasesImpl(coordinator, dodoService) { }

        val entitlement = sampleEntitlement(
            source = BillingSource.DODO_PAYMENTS,
            status = BillingStatus.ACTIVE,
            billingType = BillingPaymentType.ONE_TIME,
            externalReferenceId = "pay_123"
        )

        coEvery { coordinator.getEntitlement("user-1") } returns entitlement

        assertFailsWith<IllegalArgumentException> {
            useCases.cancelSubscription("user-1")
        }

        coVerify(exactly = 0) { coordinator.markSubscriptionCancelPending("user-1") }
        verify(exactly = 0) { dodoService.cancelSubscriptionAtPeriodEnd(any()) }
    }

    private fun sampleEntitlement(
        source: BillingSource,
        status: BillingStatus,
        billingType: BillingPaymentType?,
        externalReferenceId: String?
    ): BillingEntitlement {
        val now = Instant.parse("2024-01-01T00:00:00Z")
        return BillingEntitlement(
            customerId = "user-1",
            plan = Plan.PRO,
            status = status,
            expiresAt = now.plusSeconds(86400),
            startedAt = now,
            externalCustomerId = "cust_123",
            externalReferenceId = externalReferenceId,
            billingType = billingType,
            lastPaymentAmountCents = 1000,
            lastPaymentCurrency = "usd",
            source = source,
            features = emptyMap(),
            createdAt = now,
            updatedAt = now,
        )
    }
}

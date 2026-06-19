package com.appforge.server.services.billing

import com.appforge.server.clients.FirebaseAdminClient
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.billing.models.BillingEntitlement
import com.appforge.server.services.billing.models.BillingSource
import com.appforge.server.services.billing.models.BillingStatus
import com.appforge.server.services.billing.models.PaymentRecord
import com.appforge.server.services.billing.models.Plan
import com.appforge.server.services.billing.repository.BillingRepositoryApi
import com.appforge.server.services.email.EmailService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking

class BillingCoordinatorTest {
    private val fixedNow = Instant.parse("2024-01-01T00:00:00Z")
    private val clock: Clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
    private val authService = mockk<AuthService>()
    private val emailService = mockk<EmailService>()

    @Test
    fun `records dodo one-off and triggers email`() = runBlocking {
        val repo = InMemoryBillingRepository()
        val emailCoordinator = BillingEmailCoordinator(repo, authService, emailService)
        val coordinator = BillingCoordinator(repo, emailCoordinator, clock)

        every { authService.getUserEmail("user-2") } returns "test@example.com"
        coEvery { emailService.sendPaymentConfirmation(any(), any(), any(), any(), any()) } returns Unit

        coordinator.handleOneOffPayment(
                userId = "user-2",
                externalCustomerId = null,
                externalReferenceId = "order_123",
                amountCents = 25000,
                currency = "usd",
                source = BillingSource.DODO_PAYMENTS,
                planId = "pro_annual"
        )

        val entitlement = repo.store["user-2"]
        assertNotNull(entitlement)
        assertEquals(Plan.PRO, entitlement.plan)

        val payments = repo.history["user-2"]
        assertNotNull(payments)
        assertEquals(1, payments.size)
        assertNotNull(payments[0].emailSentAt)

        coVerify(exactly = 1) {
            emailService.sendPaymentConfirmation(
                    to = "test@example.com",
                    amount = "250.00",
                    currency = "USD",
                    planName = "Pro",
                    transactionId = "order_123"
            )
        }
    }

    @Test
    fun `does not send email if already sent (idempotency)`() = runBlocking {
        val repo = InMemoryBillingRepository()
        val emailCoordinator = BillingEmailCoordinator(repo, authService, emailService)
        val coordinator = BillingCoordinator(repo, emailCoordinator, clock)

        // Pre-fill history with a record that has emailSentAt
        repo.paymentRecords["order_123"] = PaymentRecord(
                date = fixedNow,
                amountCents = 25000,
                currency = "usd",
                planId = "as_ultra",
                emailSentAt = fixedNow
        )

        coordinator.handleOneOffPayment(
                userId = "user-2",
                externalCustomerId = null,
                externalReferenceId = "order_123",
                amountCents = 25000,
                currency = "usd",
                source = BillingSource.DODO_PAYMENTS,
                planId = "pro_annual"
        )

        coVerify(exactly = 0) {
            emailService.sendPaymentConfirmation(any(), any(), any(), any(), any())
        }
    }

    @Test
    fun `sends plan changed email when plan transitions`() = runBlocking {
        val repo = InMemoryBillingRepository()
        val emailCoordinator = BillingEmailCoordinator(repo, authService, emailService)
        val coordinator = BillingCoordinator(repo, emailCoordinator, clock)

        repo.store["user-3"] = BillingEntitlement(
            customerId = "user-3",
            plan = Plan.TRIAL,
            status = BillingStatus.TRIALING,
            expiresAt = fixedNow.plusSeconds(86400),
            startedAt = fixedNow.minusSeconds(3600),
            source = BillingSource.TRIAL,
            features = emptyMap(),
            createdAt = fixedNow.minusSeconds(3600),
            updatedAt = fixedNow.minusSeconds(3600),
        )

        every { authService.getUserEmail("user-3") } returns "planchange@example.com"
        coEvery { emailService.sendPaymentConfirmation(any(), any(), any(), any(), any()) } returns Unit
        coEvery { emailService.sendEmail(any(), any(), any(), any()) } returns Unit

        coordinator.handleSubscriptionPayment(
            userId = "user-3",
            externalCustomerId = null,
            externalReferenceId = "sub_123",
            amountCents = 1900,
            currency = "usd",
            source = BillingSource.DODO_PAYMENTS,
            planId = "pro_monthly",
        )

        coVerify(exactly = 1) {
            emailService.sendEmail(
                to = "planchange@example.com",
                subject = "Your AppForge Plan Has Changed",
                content = any(),
                isHtml = true,
            )
        }
    }

    @Test
    fun `does not send plan changed email when plan remains same`() = runBlocking {
        val repo = InMemoryBillingRepository()
        val emailCoordinator = BillingEmailCoordinator(repo, authService, emailService)
        val coordinator = BillingCoordinator(repo, emailCoordinator, clock)

        repo.store["user-4"] = BillingEntitlement(
            customerId = "user-4",
            plan = Plan.PRO,
            status = BillingStatus.ACTIVE,
            expiresAt = fixedNow.plusSeconds(86400),
            startedAt = fixedNow.minusSeconds(3600),
            source = BillingSource.DODO_PAYMENTS,
            features = emptyMap(),
            createdAt = fixedNow.minusSeconds(3600),
            updatedAt = fixedNow.minusSeconds(3600),
        )

        every { authService.getUserEmail("user-4") } returns "norepeat@example.com"
        coEvery { emailService.sendPaymentConfirmation(any(), any(), any(), any(), any()) } returns Unit
        coEvery { emailService.sendEmail(any(), any(), any(), any()) } returns Unit

        coordinator.handleSubscriptionPayment(
            userId = "user-4",
            externalCustomerId = null,
            externalReferenceId = "sub_456",
            amountCents = 1900,
            currency = "usd",
            source = BillingSource.DODO_PAYMENTS,
            planId = "pro_monthly",
        )

        coVerify(exactly = 0) {
            emailService.sendEmail(any(), "Your AppForge Plan Has Changed", any(), any())
        }
    }

    @Test
    fun `rejects unsupported canonical product id`() = runBlocking {
        val repo = InMemoryBillingRepository()
        val coordinator = BillingCoordinator(repo, null, clock)

        assertFailsWith<IllegalArgumentException> {
            coordinator.handleSubscriptionPayment(
                userId = "user-5",
                externalCustomerId = null,
                externalReferenceId = "sub_bad_plan",
                amountCents = 1900,
                currency = "usd",
                source = BillingSource.DODO_PAYMENTS,
                planId = "pro_enterprise",
            )
        }
    }

    private class InMemoryBillingRepository : BillingRepositoryApi {
        val store = mutableMapOf<String, BillingEntitlement>()
        val history = mutableMapOf<String, MutableList<PaymentRecord>>()
        val paymentRecords = mutableMapOf<String, PaymentRecord>()

        override suspend fun upsert(userId: String, entitlement: BillingEntitlement) {
            store[userId] = entitlement
        }

        override suspend fun get(userId: String): BillingEntitlement? = store[userId]

        override suspend fun getPayment(userId: String, recordId: String): PaymentRecord? = paymentRecords[recordId]

        override suspend fun recordPayment(userId: String, record: PaymentRecord, recordId: String?) {
            history.computeIfAbsent(userId) { mutableListOf() }.add(record)
            if (recordId != null) {
                paymentRecords[recordId] = record
            }
        }
    }
}

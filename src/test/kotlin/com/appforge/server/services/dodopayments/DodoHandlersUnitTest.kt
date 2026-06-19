package com.appforge.server.services.dodopayments

import com.appforge.server.services.billing.OneOffBillingHandler
import com.appforge.server.services.billing.SubscriptionBillingHandler
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlin.test.Test

class DodoHandlersUnitTest {
    private val createdAtTimestamp = Instant.parse("2025-01-01T00:00:00Z").toEpochMilli()
    @Test
    fun `one-off handler records only one_time payments`() {
        val oneOff = mockk<OneOffBillingHandler>(relaxed = true)
        val handler = DodoOneOffPaymentHandler(oneOff)

        handler.handle(
            DodoDomainEvent.PaymentSucceeded(
                paymentId = "pay_1",
                userId = "u1",
                totalAmount = 100,
                currency = "USD",
                planId = "pro_annual",
                paymentType = "one_time",
                status = "succeeded",
                createdAtTimestamp = createdAtTimestamp
            )
        )
        handler.handle(
            DodoDomainEvent.PaymentSucceeded(
                paymentId = "pay_2",
                userId = "u2",
                totalAmount = 100,
                currency = "USD",
                planId = "pro_monthly",
                paymentType = "subscription",
                status = "succeeded",
                createdAtTimestamp = createdAtTimestamp
            )
        )

        verify(exactly = 1) { oneOff.recordOneOffPayment(any()) }
    }

    @Test
    fun `subscription handler maps statuses to billing calls`() {
        val sub = mockk<SubscriptionBillingHandler>(relaxed = true)
        val handler = DodoSubscriptionHandler(sub)

        handler.handle(
            DodoDomainEvent.SubscriptionStatusChanged(
                subscriptionId = "sub_1",
                userId = "u1",
                totalAmount = 1000,
                currency = "USD",
                planId = "pro_monthly",
                productId = "pdt_1",
                status = "active",
                createdAtTimestamp = createdAtTimestamp
            )
        )
        handler.handle(
            DodoDomainEvent.SubscriptionStatusChanged(
                subscriptionId = "sub_2",
                userId = "u2",
                totalAmount = 1000,
                currency = "USD",
                planId = "pro_monthly",
                productId = "pdt_2",
                status = "renewed",
                createdAtTimestamp = createdAtTimestamp
            )
        )
        handler.handle(
            DodoDomainEvent.SubscriptionStatusChanged(
                subscriptionId = "sub_3",
                userId = "u3",
                totalAmount = 1000,
                currency = "USD",
                planId = "pro_monthly",
                productId = "pdt_3",
                status = "on_hold",
                createdAtTimestamp = createdAtTimestamp
            )
        )
        handler.handle(
            DodoDomainEvent.SubscriptionStatusChanged(
                subscriptionId = "sub_4",
                userId = "u4",
                totalAmount = 1000,
                currency = "USD",
                planId = "pro_monthly",
                productId = "pdt_4",
                status = "expired",
                createdAtTimestamp = createdAtTimestamp
            )
        )

        verify(exactly = 2) { sub.recordSubscriptionPayment(any()) }
        verify(exactly = 1) { sub.markSubscriptionOnHold("u3", "pro_monthly") }
        verify(exactly = 1) { sub.cancelSubscription("u4", "pro_monthly") }
    }
}

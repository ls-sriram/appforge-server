package com.appforge.server.services.dodopayments

import com.appforge.server.services.billing.OneOffBillingHandler
import com.appforge.server.services.billing.SubscriptionBillingHandler
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test

class DodoEventHandlerTest {
    private val createdAtTimestamp = Instant.parse("2025-01-01T00:00:00Z").toEpochMilli()
    private val oneOffHandler = mockk<OneOffBillingHandler>(relaxed = true)
    private val subscriptionHandler = mockk<SubscriptionBillingHandler>(relaxed = true)
    private val handler = DodoEventHandler(oneOffHandler, subscriptionHandler)

    @AfterTest
    fun tearDown() {
        clearMocks(oneOffHandler, subscriptionHandler)
    }

    @Test
    fun `routes payment events to one-off handler`() {
        val event = DodoDomainEvent.PaymentSucceeded(
            paymentId = "pay_1",
            userId = "user-1",
            totalAmount = 2000,
            currency = "USD",
            planId = "pro_annual",
            paymentType = "one_time",
            status = "succeeded",
            createdAtTimestamp = createdAtTimestamp
        )

        handler.handle(event)

        verify(exactly = 1) { oneOffHandler.recordOneOffPayment(any()) }
        verify(exactly = 0) { subscriptionHandler.recordSubscriptionPayment(any()) }
    }

    @Test
    fun `routes subscription status to subscription handler`() {
        val event = DodoDomainEvent.SubscriptionStatusChanged(
            subscriptionId = "sub_1",
            userId = "user-2",
            totalAmount = 1000,
            currency = "USD",
            planId = "pro_monthly",
            productId = "pdt_123",
            status = "active",
            createdAtTimestamp = createdAtTimestamp
        )

        handler.handle(event)

        verify(exactly = 1) { subscriptionHandler.recordSubscriptionPayment(any()) }
        verify(exactly = 0) { oneOffHandler.recordOneOffPayment(any()) }
    }

    @Test
    fun `ignores subscription payments in one-off handler`() {
        val event = DodoDomainEvent.PaymentSucceeded(
            paymentId = "pay_sub",
            userId = "user-3",
            totalAmount = 1000,
            currency = "USD",
            planId = "pro_monthly",
            paymentType = "subscription",
            status = "succeeded",
            createdAtTimestamp = createdAtTimestamp
        )

        handler.handle(event)

        verify(exactly = 0) { oneOffHandler.recordOneOffPayment(any()) }
        verify(exactly = 0) { subscriptionHandler.recordSubscriptionPayment(any()) }
    }

    @Test
    fun `subscription on hold marks past due`() {
        val event = DodoDomainEvent.SubscriptionStatusChanged(
            subscriptionId = "sub_hold",
            userId = "user-hold",
            totalAmount = 1000,
            currency = "USD",
            planId = "pro_monthly",
            productId = "pdt_hold",
            status = "on_hold",
            createdAtTimestamp = createdAtTimestamp
        )

        handler.handle(event)

        verify(exactly = 1) { subscriptionHandler.markSubscriptionOnHold("user-hold", "pro_monthly") }
        verify(exactly = 0) { subscriptionHandler.cancelSubscription(any(), any()) }
    }

    @Test
    fun `subscription cancelled triggers cancellation`() {
        val event = DodoDomainEvent.SubscriptionStatusChanged(
            subscriptionId = "sub_cancel",
            userId = "user-cancel",
            totalAmount = 1000,
            currency = "USD",
            planId = "pro_monthly",
            productId = "pdt_cancel",
            status = "cancelled",
            createdAtTimestamp = createdAtTimestamp
        )

        handler.handle(event)

        verify(exactly = 1) { subscriptionHandler.cancelSubscription("user-cancel", "pro_monthly") }
        verify(exactly = 0) { subscriptionHandler.markSubscriptionOnHold(any(), any()) }
    }

    @Test
    fun `subscription expired triggers cancellation`() {
        val event = DodoDomainEvent.SubscriptionStatusChanged(
            subscriptionId = "sub_exp",
            userId = "user-exp",
            totalAmount = 1000,
            currency = "USD",
            planId = "pro_monthly",
            productId = "pdt_exp",
            status = "expired",
            createdAtTimestamp = createdAtTimestamp
        )

        handler.handle(event)

        verify(exactly = 1) { subscriptionHandler.cancelSubscription("user-exp", "pro_monthly") }
    }

    @Test
    fun `subscription failed triggers cancellation`() {
        val event = DodoDomainEvent.SubscriptionStatusChanged(
            subscriptionId = "sub_fail",
            userId = "user-fail",
            totalAmount = 1000,
            currency = "USD",
            planId = "pro_monthly",
            productId = "pdt_fail",
            status = "failed",
            createdAtTimestamp = createdAtTimestamp
        )

        handler.handle(event)

        verify(exactly = 1) { subscriptionHandler.cancelSubscription("user-fail", "pro_monthly") }
    }

    @Test
    fun `subscription renewed records payment`() {
        val event = DodoDomainEvent.SubscriptionStatusChanged(
            subscriptionId = "sub_renew",
            userId = "user-renew",
            totalAmount = 1000,
            currency = "USD",
            planId = "pro_monthly",
            productId = "pdt_renew",
            status = "renewed",
            createdAtTimestamp = createdAtTimestamp
        )

        handler.handle(event)

        verify(exactly = 1) { subscriptionHandler.recordSubscriptionPayment(any()) }
    }

    @Test
    fun `payment processing routes to payment handler`() {
        val event = DodoDomainEvent.PaymentProcessing(
            paymentId = "pay_proc",
            userId = "user-proc",
            paymentType = "one_time",
            status = "processing",
            createdAtTimestamp = createdAtTimestamp
        )

        handler.handle(event)

        verify(exactly = 0) { subscriptionHandler.recordSubscriptionPayment(any()) }
    }

    @Test
    fun `payment cancelled routes to payment handler`() {
        val event = DodoDomainEvent.PaymentCancelled(
            paymentId = "pay_cancel",
            userId = "user-cancel",
            paymentType = "one_time",
            status = "cancelled",
            createdAtTimestamp = createdAtTimestamp
        )

        handler.handle(event)

        verify(exactly = 0) { subscriptionHandler.recordSubscriptionPayment(any()) }
    }
}

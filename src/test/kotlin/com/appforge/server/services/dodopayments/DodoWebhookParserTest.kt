package com.appforge.server.services.dodopayments

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DodoWebhookParserTest {
    private val parser =
        DodoWebhookParser(
            canonicalToDodoProductIds =
                mapOf(
                    "pro_monthly" to "pdt_monthly",
                    "pro_annual" to "pdt_annual",
                )
        )

    @Test
    fun `parses payment succeeded`() {
        val payload = """
            {
              "type": "payment.succeeded",
              "data": {
                "payment_id": "pay_123",
                "status": "succeeded",
                "total_amount": 2000,
                "currency": "USD",
                "created_at": "2025-01-01T00:00:00Z",
                "metadata": {
                  "userId": "user-1",
                  "canonicalProductId": "pro_annual",
                  "paymentType": "one_time"
                }
              }
            }
        """.trimIndent()

        val event = parser.parse(payload)
        val payment = event as? DodoDomainEvent.PaymentSucceeded
        assertNotNull(payment)
        assertEquals("pay_123", payment.paymentId)
        assertEquals("user-1", payment.userId)
        assertEquals(2000, payment.totalAmount)
        assertEquals("USD", payment.currency)
        assertEquals("pro_annual", payment.planId)
        assertEquals("one_time", payment.paymentType)
        assertEquals("succeeded", payment.status)
    }

    @Test
    fun `parses payment failed`() {
        val payload = """
            {
              "type": "payment.failed",
              "data": {
                "payment_id": "pay_fail",
                "status": "failed",
                "total_amount": 2000,
                "currency": "USD",
                "created_at": "2025-01-01T00:00:00Z",
                "metadata": {
                  "userId": "user-2",
                  "paymentType": "one_time"
                }
              }
            }
        """.trimIndent()

        val event = parser.parse(payload)
        val failed = event as? DodoDomainEvent.PaymentFailed
        assertNotNull(failed)
        assertEquals("pay_fail", failed.paymentId)
        assertEquals("user-2", failed.userId)
        assertEquals("one_time", failed.paymentType)
        assertEquals("failed", failed.status)
    }

    @Test
    fun `parses subscription active with interval`() {
        val payload = """
            {
              "type": "subscription.active",
              "data": {
                "subscription_id": "sub_1",
                "product_id": "pdt_123",
                "status": "active",
                "recurring_pre_tax_amount": 1000,
                "currency": "USD",
                "created_at": "2025-01-01T00:00:00Z",
                "payment_frequency_count": 1,
                "payment_frequency_interval": "Month",
                "metadata": {
                  "userId": "user-3",
                  "canonicalProductId": "pro_monthly"
                }
              }
            }
        """.trimIndent()

        val event = parser.parse(payload)
        val sub = event as? DodoDomainEvent.SubscriptionStatusChanged
        assertNotNull(sub)
        assertEquals("sub_1", sub.subscriptionId)
        assertEquals("user-3", sub.userId)
        assertEquals(1000, sub.totalAmount)
        assertEquals("USD", sub.currency)
        assertEquals("pro_monthly", sub.planId)
        assertEquals("pdt_123", sub.productId)
        assertEquals("active", sub.status)
    }

    @Test
    fun `parses subscription renewed`() {
        val payload = """
            {
              "type": "subscription.renewed",
              "data": {
                "subscription_id": "sub_renew",
                "product_id": "pdt_renew",
                "status": "active",
                "recurring_pre_tax_amount": 1500,
                "currency": "USD",
                "created_at": "2025-02-01T00:00:00Z",
                "payment_frequency_count": 1,
                "payment_frequency_interval": "Month",
                "metadata": {
                  "userId": "user-4",
                  "canonicalProductId": "pro_monthly"
                }
              }
            }
        """.trimIndent()

        val event = parser.parse(payload)
        val sub = event as? DodoDomainEvent.SubscriptionStatusChanged
        assertNotNull(sub)
        assertEquals("sub_renew", sub.subscriptionId)
        assertEquals("user-4", sub.userId)
        assertEquals(1500, sub.totalAmount)
        assertEquals("active", sub.status)
    }

    @Test
    fun `parses subscription updated`() {
        val payload = """
            {
              "type": "subscription.updated",
              "data": {
                "subscription_id": "sub_updated",
                "product_id": "pdt_monthly",
                "status": "active",
                "recurring_pre_tax_amount": 1500,
                "currency": "USD",
                "created_at": "2025-02-15T00:00:00Z",
                "metadata": {
                  "userId": "user-4b",
                  "canonicalProductId": "pro_monthly"
                }
              }
            }
        """.trimIndent()

        val event = parser.parse(payload)
        val sub = event as? DodoDomainEvent.SubscriptionStatusChanged
        assertNotNull(sub)
        assertEquals("sub_updated", sub.subscriptionId)
        assertEquals("user-4b", sub.userId)
        assertEquals(1500, sub.totalAmount)
        assertEquals("active", sub.status)
    }

    @Test
    fun `parses subscription updated cancelled status`() {
        val payload = """
            {
              "type": "subscription.updated",
              "data": {
                "subscription_id": "sub_updated_cancelled",
                "product_id": "pdt_monthly",
                "status": "cancelled",
                "recurring_pre_tax_amount": 1500,
                "currency": "USD",
                "created_at": "2025-02-15T00:00:00Z",
                "metadata": {
                  "userId": "user-4c",
                  "canonicalProductId": "pro_monthly"
                }
              }
            }
        """.trimIndent()

        val event = parser.parse(payload)
        val sub = event as? DodoDomainEvent.SubscriptionStatusChanged
        assertNotNull(sub)
        assertEquals("sub_updated_cancelled", sub.subscriptionId)
        assertEquals("user-4c", sub.userId)
        assertEquals(1500, sub.totalAmount)
        assertEquals("cancelled", sub.status)
    }

    @Test
    fun `parses subscription on hold`() {
        val payload = """
            {
              "type": "subscription.on_hold",
              "data": {
                "subscription_id": "sub_hold",
                "product_id": "pdt_monthly",
                "status": "on_hold",
                "recurring_pre_tax_amount": 1000,
                "currency": "USD",
                "created_at": "2025-03-01T00:00:00Z",
                "payment_frequency_count": 1,
                "payment_frequency_interval": "Month",
                "metadata": {
                  "userId": "user-5",
                  "canonicalProductId": "pro_monthly"
                }
              }
            }
        """.trimIndent()

        val event = parser.parse(payload)
        val sub = event as? DodoDomainEvent.SubscriptionStatusChanged
        assertNotNull(sub)
        assertEquals("sub_hold", sub.subscriptionId)
        assertEquals("on_hold", sub.status)
    }

    @Test
    fun `parses subscription cancelled`() {
        val payload = """
            {
              "type": "subscription.cancelled",
              "data": {
                "subscription_id": "sub_cancel",
                "product_id": "pdt_monthly",
                "status": "cancelled",
                "recurring_pre_tax_amount": 1000,
                "currency": "USD",
                "created_at": "2025-04-01T00:00:00Z",
                "payment_frequency_count": 1,
                "payment_frequency_interval": "Month",
                "metadata": {
                  "userId": "user-6",
                  "canonicalProductId": "pro_monthly"
                }
              }
            }
        """.trimIndent()

        val event = parser.parse(payload)
        val sub = event as? DodoDomainEvent.SubscriptionStatusChanged
        assertNotNull(sub)
        assertEquals("sub_cancel", sub.subscriptionId)
        assertEquals("cancelled", sub.status)
    }

    @Test
    fun `parses subscription expired`() {
        val payload = """
            {
              "type": "subscription.expired",
              "data": {
                "subscription_id": "sub_exp",
                "product_id": "pdt_monthly",
                "status": "expired",
                "recurring_pre_tax_amount": 1000,
                "currency": "USD",
                "created_at": "2025-05-01T00:00:00Z",
                "payment_frequency_count": 1,
                "payment_frequency_interval": "Month",
                "metadata": {
                  "userId": "user-7",
                  "canonicalProductId": "pro_monthly"
                }
              }
            }
        """.trimIndent()

        val event = parser.parse(payload)
        val sub = event as? DodoDomainEvent.SubscriptionStatusChanged
        assertNotNull(sub)
        assertEquals("sub_exp", sub.subscriptionId)
        assertEquals("expired", sub.status)
    }

    @Test
    fun `parses subscription failed`() {
        val payload = """
            {
              "type": "subscription.failed",
              "data": {
                "subscription_id": "sub_fail",
                "product_id": "pdt_monthly",
                "status": "failed",
                "recurring_pre_tax_amount": 1000,
                "currency": "USD",
                "created_at": "2025-06-01T00:00:00Z",
                "payment_frequency_count": 1,
                "payment_frequency_interval": "Month",
                "metadata": {
                  "userId": "user-8",
                  "canonicalProductId": "pro_monthly"
                }
              }
            }
        """.trimIndent()

        val event = parser.parse(payload)
        val sub = event as? DodoDomainEvent.SubscriptionStatusChanged
        assertNotNull(sub)
        assertEquals("sub_fail", sub.subscriptionId)
        assertEquals("failed", sub.status)
    }

    @Test
    fun `parses payment processing`() {
        val payload = """
            {
              "type": "payment.processing",
              "data": {
                "payment_id": "pay_processing",
                "status": "processing",
                "total_amount": 2000,
                "currency": "USD",
                "created_at": "2025-01-02T00:00:00Z",
                "metadata": {
                  "userId": "user-9",
                  "paymentType": "one_time"
                }
              }
            }
        """.trimIndent()

        val event = parser.parse(payload)
        val processing = event as? DodoDomainEvent.PaymentProcessing
        assertNotNull(processing)
        assertEquals("pay_processing", processing.paymentId)
        assertEquals("processing", processing.status)
    }

    @Test
    fun `parses payment cancelled`() {
        val payload = """
            {
              "type": "payment.cancelled",
              "data": {
                "payment_id": "pay_cancel",
                "status": "cancelled",
                "total_amount": 2000,
                "currency": "USD",
                "created_at": "2025-01-03T00:00:00Z",
                "metadata": {
                  "userId": "user-10",
                  "paymentType": "one_time"
                }
              }
            }
        """.trimIndent()

        val event = parser.parse(payload)
        val cancelled = event as? DodoDomainEvent.PaymentCancelled
        assertNotNull(cancelled)
        assertEquals("pay_cancel", cancelled.paymentId)
        assertEquals("cancelled", cancelled.status)
    }

    @Test
    fun `returns null when missing userId`() {
        val payload = """
            {
              "type": "payment.succeeded",
              "data": {
                "payment_id": "pay_123",
                "status": "succeeded",
                "total_amount": 2000,
                "currency": "USD",
                "created_at": "2025-01-01T00:00:00Z",
                "metadata": {}
              }
            }
        """.trimIndent()

        val event = parser.parse(payload)
        assertNull(event)
    }

    @Test
    fun `returns null when payment event is missing paymentType`() {
        val payload = """
            {
              "type": "payment.failed",
              "data": {
                "payment_id": "pay_fail_missing_type",
                "status": "failed",
                "total_amount": 2000,
                "currency": "USD",
                "created_at": "2025-01-01T00:00:00Z",
                "metadata": {
                  "userId": "user-2"
                }
              }
            }
        """.trimIndent()

        val event = parser.parse(payload)
        assertNull(event)
    }

    @Test
    fun `returns null when missing subscriptionId`() {
        val payload = """
            {
              "type": "subscription.active",
              "data": {
                "status": "active",
                "recurring_pre_tax_amount": 1000,
                "currency": "USD",
                "created_at": "2025-01-01T00:00:00Z",
                "payment_frequency_count": 1,
                "payment_frequency_interval": "Month",
                "metadata": {
                  "userId": "user-3"
                }
              }
            }
        """.trimIndent()

        val event = parser.parse(payload)
        assertNull(event)
    }
}

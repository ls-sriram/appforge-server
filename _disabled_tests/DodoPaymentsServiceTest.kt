package com.appforge.server.services.dodopayments

import com.appforge.server.clients.DodoPaymentsDataClient
import com.appforge.server.config.AppEnv
import com.appforge.server.config.options.*
import com.appforge.server.infrastructure.DatabaseMode
import com.appforge.server.infrastructure.DatabaseProvider
import com.appforge.server.services.billing.OneOffBillingHandler
import com.appforge.server.services.billing.SubscriptionBillingHandler
import com.appforge.server.services.billing.repository.BillingAuditRepositoryApi
import com.appforge.server.services.dodopayments.models.DodoPaymentsPaymentCompletedWrite
import io.mockk.mockk
import io.mockk.coEvery
import io.mockk.verify
import kotlin.test.Test
import kotlin.test.assertFailsWith

class DodoPaymentsServiceTest {
  @Test
  fun `webhook with valid signature succeeds`() {
    val dodoClient = mockk<DodoPaymentsDataClient>()
    val oneOff = mockk<OneOffBillingHandler>(relaxed = true)
    val subscription = mockk<SubscriptionBillingHandler>(relaxed = true)
    val secret = "whsec_test_secret_123"
    val env = createTestEnv(webhookKey = secret)

    val auditRepository = mockk<BillingAuditRepositoryApi>(relaxed = true)
    coEvery { auditRepository.tryRecordOnce(any()) } returns true
    val service = DodoPaymentsService(dodoClient, oneOff, subscription, env, auditRepository)

    val payload =
            """{"type": "payment.succeeded", "data": {"payment_id": "p1", "status": "succeeded", "total_amount": 100, "currency": "usd", "created_at": "2025-01-01T00:00:00Z", "metadata": {"userId": "u1", "canonicalProductId": "pro_monthly"}}}"""
    val id = "msg_123"
    val timestamp = "1700000000"

    // Calculate expected signature using the raw secret bytes (stripping whsec_ prefix if present)
    val signedMessage = "$id.$timestamp.$payload"
    val hmac = javax.crypto.Mac.getInstance("HmacSHA256")
    val cleanSecret = if (secret.startsWith("whsec_")) secret.substring(6) else secret
    // Decode Base64 if possible
    val secretBytes =
            try {
              java.util.Base64.getDecoder().decode(cleanSecret)
            } catch (e: Exception) {
              cleanSecret.toByteArray(Charsets.UTF_8)
            }
    val secretKey = javax.crypto.spec.SecretKeySpec(secretBytes, "HmacSHA256")
    hmac.init(secretKey)
    val hash = hmac.doFinal(signedMessage.toByteArray(Charsets.UTF_8))
    val encodedHash = java.util.Base64.getEncoder().encodeToString(hash)
    val signature = "v1,$encodedHash"

    service.handleWebhook(payload, signature, id, timestamp)

    verify { oneOff.recordOneOffPayment(any<DodoPaymentsPaymentCompletedWrite>()) }
  }

  @Test
  fun `webhook for failed payment is handled`() {
    val dodoClient = mockk<DodoPaymentsDataClient>()
    val oneOff = mockk<OneOffBillingHandler>(relaxed = true)
    val subscription = mockk<SubscriptionBillingHandler>(relaxed = true)
    val secret = "whsec_test_secret_123"
    val env = createTestEnv(webhookKey = secret)
    val auditRepository = mockk<BillingAuditRepositoryApi>(relaxed = true)
    coEvery { auditRepository.tryRecordOnce(any()) } returns true
    val service = DodoPaymentsService(dodoClient, oneOff, subscription, env, auditRepository)

    val payload =
            """{"type": "payment.failed", "data": {"payment_id": "p1", "status": "failed", "total_amount": 100, "currency": "usd", "created_at": "2025-01-01T00:00:00Z", "metadata": {"userId": "u1"}}}"""
    val id = "msg_f1"
    val timestamp = "1700000001"
    val signature = calculateSignature(secret, id, timestamp, payload)

    service.handleWebhook(payload, signature, id, timestamp)

    // No payment should be recorded for failed payment
    verify(exactly = 0) {
      oneOff.recordOneOffPayment(any<DodoPaymentsPaymentCompletedWrite>())
    }
  }

  @Test
  fun `webhook for cancelled payment is handled`() {
    val dodoClient = mockk<DodoPaymentsDataClient>()
    val oneOff = mockk<OneOffBillingHandler>(relaxed = true)
    val subscription = mockk<SubscriptionBillingHandler>(relaxed = true)
    val secret = "whsec_test_secret_123"
    val env = createTestEnv(webhookKey = secret)
    val auditRepository = mockk<BillingAuditRepositoryApi>(relaxed = true)
    coEvery { auditRepository.tryRecordOnce(any()) } returns true
    val service = DodoPaymentsService(dodoClient, oneOff, subscription, env, auditRepository)

    val payload =
            """{"type": "payment.cancelled", "data": {"payment_id": "p1", "status": "cancelled", "total_amount": 100, "currency": "usd", "created_at": "2025-01-01T00:00:00Z", "metadata": {"userId": "u1"}}}"""
    val id = "msg_c1"
    val timestamp = "1700000002"
    val signature = calculateSignature(secret, id, timestamp, payload)

    service.handleWebhook(payload, signature, id, timestamp)

    // DodoEventHandler explicitly logs that no action is taken for cancelled payments

    verify(exactly = 0) {
      oneOff.recordOneOffPayment(any<DodoPaymentsPaymentCompletedWrite>())
    }
  }

  private fun calculateSignature(
          secret: String,
          id: String,
          timestamp: String,
          payload: String
  ): String {
    val signedMessage = "$id.$timestamp.$payload"
    val hmac = javax.crypto.Mac.getInstance("HmacSHA256")
    val cleanSecret = if (secret.startsWith("whsec_")) secret.substring(6) else secret
    val secretBytes =
            try {
              java.util.Base64.getDecoder().decode(cleanSecret)
            } catch (e: Exception) {
              cleanSecret.toByteArray(Charsets.UTF_8)
            }
    val secretKey = javax.crypto.spec.SecretKeySpec(secretBytes, "HmacSHA256")
    hmac.init(secretKey)
    val hash = hmac.doFinal(signedMessage.toByteArray(Charsets.UTF_8))
    val encodedHash = java.util.Base64.getEncoder().encodeToString(hash)
    return "v1,$encodedHash"
  }
  @Test
  fun `webhook with invalid signature fails`() {
    val dodoClient = mockk<DodoPaymentsDataClient>()
    val oneOff = mockk<OneOffBillingHandler>()
    val subscription = mockk<SubscriptionBillingHandler>()
    val env = createTestEnv(webhookKey = "secret")

    val auditRepository = mockk<BillingAuditRepositoryApi>(relaxed = true)
    coEvery { auditRepository.tryRecordOnce(any()) } returns true
    val service = DodoPaymentsService(dodoClient, oneOff, subscription, env, auditRepository)

    assertFailsWith<IllegalArgumentException> {
      service.handleWebhook(
              payload = "{}",
              signature = "invalid",
              webhookId = "msg_1",
              timestamp = "123456"
      )
    }
  }

  @Test
  fun `payment success event records payment`() {
    val dodoClient = mockk<DodoPaymentsDataClient>()
    val oneOff = mockk<OneOffBillingHandler>(relaxed = true)
    val subscription = mockk<SubscriptionBillingHandler>(relaxed = true)
    val env = createTestEnv(webhookKey = null)

    val auditRepository = mockk<BillingAuditRepositoryApi>(relaxed = true)
    coEvery { auditRepository.tryRecordOnce(any()) } returns true
    val service = DodoPaymentsService(dodoClient, oneOff, subscription, env, auditRepository)

    val payload =
            """
        {
          "data": {
            "payment_id": "pay_2IjeQm4hqU6RA4Z4kwDee",
            "status": "succeeded",
            "total_amount": 400,
            "currency": "USD",
            "created_at": "2025-08-04T05:30:31.152232Z",
            "metadata": {
                "userId": "user_123",
                "canonicalProductId": "pro_monthly"
            }
          },
          "type": "payment.succeeded"
        }
        """.trimIndent()

    service.handleWebhook(payload, "any", "id", "ts")

    verify {
      oneOff.recordOneOffPayment(
              match<DodoPaymentsPaymentCompletedWrite> {
                it.userId == "user_123" &&
                        it.totalAmount == 400L &&
                        it.orderId == "pay_2IjeQm4hqU6RA4Z4kwDee" &&
                        it.status == "succeeded" &&
                        it.currency == "usd"
              }
      )
    }
  }

  @Test
  fun `payment success with full exact message`() {
    val dodoClient = mockk<DodoPaymentsDataClient>()
    val oneOff = mockk<OneOffBillingHandler>(relaxed = true)
    val subscription = mockk<SubscriptionBillingHandler>(relaxed = true)
    val env = createTestEnv(webhookKey = null)

    val auditRepository = mockk<BillingAuditRepositoryApi>(relaxed = true)
    coEvery { auditRepository.tryRecordOnce(any()) } returns true
    val service = DodoPaymentsService(dodoClient, oneOff, subscription, env, auditRepository)

    val fullPayload =
            """
        {
          "business_id": "bus_P3SXLcppjXgagmHS",
          "data": {
            "billing": {
              "city": "New York",
              "country": "US",
              "state": "New York",
              "street": "New York, New York",
              "zipcode": "0"
            },
            "brand_id": "bus_P3SXLcppjXgagmHS",
            "business_id": "bus_P3SXLcppjXgagmHS",
            "card_issuing_country": "GB",
            "card_last_four": "4242",
            "card_network": "VISA",
            "card_type": "CREDIT",
            "checkout_session_id": "cks_stst1231",
            "created_at": "2025-08-04T05:30:31.152232Z",
            "currency": "USD",
            "customer": {
              "customer_id": "cus_8VbC6JDZzPEqfB",
              "email": "test@acme.com",
              "metadata": {},
              "name": "Test user",
              "phone_number": "+15555550100"
            },
            "digital_products_delivered": false,
            "discount_id": null,
            "disputes": [],
            "error_code": null,
            "error_message": null,
            "invoice_id": "inv_2IsUnWGtRKFLxk7xAQeyt",
            "metadata": {
                "userId": "user_full_123",
                "canonicalProductId": "pro_monthly"
            },
            "payload_type": "Payment",
            "payment_id": "pay_full_999",
            "payment_link": "https://test.checkout.dodopayments.com/cbq",
            "payment_method": "card",
            "payment_method_type": null,
            "product_cart": [
              {
                "product_id": "pdt_e9mUw084cWnu0tz",
                "quantity": 1
              }
            ],
            "refunds": [],
            "settlement_amount": 400,
            "settlement_currency": "USD",
            "settlement_tax": null,
            "status": "succeeded",
            "subscription_id": null,
            "tax": null,
            "total_amount": 400,
            "updated_at": null
          },
          "timestamp": "2025-08-04T05:30:45.182629Z",
          "type": "payment.succeeded"
        }
        """.trimIndent()

    service.handleWebhook(fullPayload, "any", "id", "ts")

    verify {
      oneOff.recordOneOffPayment(
              match<DodoPaymentsPaymentCompletedWrite> {
                it.userId == "user_full_123" &&
                        it.totalAmount == 400L &&
                        it.orderId == "pay_full_999" &&
                        it.status == "succeeded" &&
                        it.currency == "usd"
              }
      )
    }
  }

  @Test
  fun `subscription active webhook records subscription payment`() {
    val dodoClient = mockk<DodoPaymentsDataClient>()
    val oneOff = mockk<OneOffBillingHandler>(relaxed = true)
    val subscription = mockk<SubscriptionBillingHandler>(relaxed = true)
    val secret = "whsec_test_secret_123"
    val env = createTestEnv(webhookKey = secret)

    val auditRepository = mockk<BillingAuditRepositoryApi>(relaxed = true)
    coEvery { auditRepository.tryRecordOnce(any()) } returns true
    val service = DodoPaymentsService(dodoClient, oneOff, subscription, env, auditRepository)

    val payload =
            """{"type":"subscription.active","data":{"subscription_id":"sub_1","product_id":"pdt_1","status":"active","recurring_pre_tax_amount":1000,"currency":"USD","created_at":"2025-01-01T00:00:00Z","payment_frequency_count":1,"payment_frequency_interval":"Month","metadata":{"userId":"u1","canonicalProductId":"pro_monthly"}}}"""
    val id = "msg_sub_1"
    val timestamp = "1700000100"
    val signature = calculateSignature(secret, id, timestamp, payload)

    service.handleWebhook(payload, signature, id, timestamp)

    verify(exactly = 1) { subscription.recordSubscriptionPayment(any<DodoPaymentsPaymentCompletedWrite>()) }
    verify(exactly = 0) { oneOff.recordOneOffPayment(any<DodoPaymentsPaymentCompletedWrite>()) }
  }

  @Test
  fun `subscription on hold webhook marks past due`() {
    val dodoClient = mockk<DodoPaymentsDataClient>()
    val oneOff = mockk<OneOffBillingHandler>(relaxed = true)
    val subscription = mockk<SubscriptionBillingHandler>(relaxed = true)
    val secret = "whsec_test_secret_123"
    val env = createTestEnv(webhookKey = secret)

    val auditRepository = mockk<BillingAuditRepositoryApi>(relaxed = true)
    coEvery { auditRepository.tryRecordOnce(any()) } returns true
    val service = DodoPaymentsService(dodoClient, oneOff, subscription, env, auditRepository)

    val payload =
            """{"type":"subscription.on_hold","data":{"subscription_id":"sub_2","product_id":"pdt_2","status":"on_hold","recurring_pre_tax_amount":1000,"currency":"USD","created_at":"2025-01-01T00:00:00Z","payment_frequency_count":1,"payment_frequency_interval":"Month","metadata":{"userId":"u2","canonicalProductId":"pro_monthly"}}}"""
    val id = "msg_sub_2"
    val timestamp = "1700000101"
    val signature = calculateSignature(secret, id, timestamp, payload)

    service.handleWebhook(payload, signature, id, timestamp)

    verify(exactly = 1) { subscription.markSubscriptionOnHold("u2", "pro_monthly") }
  }

  private fun createTestEnv(webhookKey: String?) =
          AppEnv(
                  runtime = RuntimeOptions(8080, "localhost", emptyList(), "development", "http://localhost:8080", "test-secret", false),
                  session = SessionOptions(false, "session", 14, "Lax"),
                  dodoPayments = DodoPaymentsOptions("key", webhookKey, "https://test.com"),
                  firebase = FirebaseOptions("test", "test", "test", null, null, null),
                  uploads = UploadOptions("bucket", "test-secret", 600, 1000L),
                  openai = OpenAIOptions("key"),
                  email = EmailOptions("test", "https://test.com", "test@test.com", "test"),
                  database = com.appforge.server.config.options.DatabaseOptions(com.appforge.server.infrastructure.DatabaseProvider.SQL, null, "", "", "", 10),
                  billing = BillingOptions(
                          trialDurationDays = 14L,
                          dodoProductIds = mapOf(
                                  "pro_monthly" to "p_pro_monthly",
                                  "pro_annual" to "p_pro_annual",
                          )
                  ),
          )
}

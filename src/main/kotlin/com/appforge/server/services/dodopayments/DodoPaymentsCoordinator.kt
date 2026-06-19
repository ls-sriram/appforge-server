package com.appforge.server.services.dodopayments

import com.appforge.server.api.CheckoutPaymentType
import com.appforge.server.api.CheckoutRequest
import com.appforge.server.api.CheckoutResponse
import com.appforge.server.clients.DodoPaymentsDataClient
import com.appforge.server.services.billing.models.BillingAuditRecord
import com.appforge.server.services.billing.repository.BillingAuditRepositoryApi
import com.appforge.server.services.billing.catalog.BillingCatalog
import com.appforge.server.services.billing.catalog.ProductBillingType
import com.dodopayments.api.core.JsonValue
import com.dodopayments.api.models.checkoutsessions.CheckoutSessionRequest
import java.time.Clock
import com.appforge.server.infrastructure.time.*
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

class DodoPaymentsCoordinator(
        private val dodoPaymentsClient: DodoPaymentsDataClient,
        private val verifier: DodoWebhookVerifier,
        private val parser: DodoWebhookParser,
        private val eventHandler: DodoEventHandler,
        private val auditRepository: BillingAuditRepositoryApi,
        private val billingOptions: com.appforge.server.config.options.BillingOptions,
        private val clock: Clock = Clock.systemUTC(),
) {
	        private val logger = LoggerFactory.getLogger(DodoPaymentsCoordinator::class.java)

	        private val productIdMap = billingOptions.dodoProductIds

	        private fun expectedPaymentType(canonicalProductId: String): CheckoutPaymentType =
	                when (BillingCatalog.getById(canonicalProductId).billingType) {
	                        ProductBillingType.SUBSCRIPTION -> CheckoutPaymentType.SUBSCRIPTION
	                        ProductBillingType.ONE_TIME -> CheckoutPaymentType.ONE_TIME
	                }

        fun createCheckoutSession(
                request: CheckoutRequest,
                userId: String,
                origin: String
        ): CheckoutResponse {
                logger.info("Creating DodoPayments checkout session for user: {}, plan: {}, paymentType: {}", userId, request.priceId, request.paymentType)
                val customerEmail = request.customerEmail
                require(customerEmail.isNotBlank()) { "Customer email is required" }

	                val canonicalProductId =
	                        request.priceId ?: throw IllegalArgumentException("priceId is required")
	                val derivedPaymentType = expectedPaymentType(canonicalProductId)
	                // Ignore request.paymentType if wrong; canonical priceId determines billing mode.
	                val dodoProductId = productIdMap[canonicalProductId] ?: canonicalProductId

                logger.info("Resolving product for Dodo: canonicalId={}, resolvedDodoId={}", canonicalProductId, dodoProductId)

                val product = try {
                        dodoPaymentsClient.retrieveProduct(dodoProductId)
                } catch (e: Exception) {
                        logger.error("Failed to retrieve product from Dodo: {}. Error: {}", dodoProductId, e.message)
                        throw IllegalStateException("Product not found in Dodo: $dodoProductId. Please check if the API key and Product ID match the environment (Test/Live).", e)
                }

	                val amountCents = request.amountCents ?: product.price()
	                val currencyValue =
	                        request.currency?.trim()?.takeIf { it.isNotEmpty() }
	                val metadataMap = request.metadata.toMutableMap()
                metadataMap["userId"] = userId
	                metadataMap["amountCents"] = amountCents.toString()
	                currencyValue?.let { metadataMap["currency"] = it }
	                metadataMap["canonicalProductId"] = canonicalProductId
	                metadataMap["customerEmail"] = customerEmail
	                metadataMap["paymentType"] = derivedPaymentType.name.lowercase()

                val returnUrl =
                        request.successUrl ?: request.cancelUrl ?: "$origin/web/upgrade"

                val checkoutRequest =
                        CheckoutSessionRequest.builder()
                                .addProductCart(
                                        CheckoutSessionRequest.ProductCart.builder()
                                                .productId(dodoProductId)
                                                .quantity(1)
                                                .build()
                                )
                                .returnUrl(returnUrl)
                                .metadata(
                                        CheckoutSessionRequest.Metadata.builder()
                                                .additionalProperties(
                                                        metadataMap.mapValues { entry ->
                                                                JsonValue.from(entry.value)
                                                        }
                                                )
                                                .build()
                                )
                                .build()

                val session = dodoPaymentsClient.createCheckoutSession(checkoutRequest)

                return CheckoutResponse(
                        sessionId = session.sessionId(),
                        url = session.checkoutUrl()
                )
        }

        fun cancelSubscriptionAtPeriodEnd(subscriptionId: String) {
                logger.info("Requesting cancel-at-period-end for Dodo subscription {}", subscriptionId)
                dodoPaymentsClient.cancelSubscriptionAtPeriodEnd(subscriptionId)
        }

        fun handleWebhook(
                payload: String,
                signature: String,
                webhookId: String?,
                timestamp: String?
        ) {
                logger.info("DodoPayments webhook received. ID: $webhookId")

                if (!verifier.verify(payload, signature, webhookId, timestamp)) {
                        throw IllegalArgumentException("Invalid signature")
                }

                // Idempotency: record the first time we see this webhook id; ignore duplicates.
                val recorded =
                        runBlocking {
                                auditRepository.tryRecordOnce(
                                        BillingAuditRecord(
                                                payload = payload,
                                                timestamp = clock.nowTimestamp(),
                                                webhookId = webhookId,
                                                source = "dodopayments"
                                        )
                                )
                        }
                if (!recorded) {
                        logger.info("Duplicate DodoPayments webhook received, ignoring. ID={}", webhookId)
                        return
                }

                val event = parser.parse(payload)
                if (event == null) {
                        logger.error("Failed to parse DodoPayments webhook payload")
                        return
                }

                eventHandler.handle(event)
        }
}

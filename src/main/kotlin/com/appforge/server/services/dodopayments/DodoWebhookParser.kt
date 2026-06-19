package com.appforge.server.services.dodopayments

import com.appforge.server.api.DodoWebhookPayload
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import com.appforge.server.infrastructure.time.*

class DodoWebhookParser(
    canonicalToDodoProductIds: Map<String, String> = emptyMap(),
) {
    private val logger = LoggerFactory.getLogger(DodoWebhookParser::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val dodoToCanonicalProductIds: Map<String, String> =
        canonicalToDodoProductIds.entries.associate { (canonical, dodo) -> dodo to canonical }

    fun parse(payload: String): DodoDomainEvent? {
        val apiModel =
                try {
                    json.decodeFromString<DodoWebhookPayload>(payload)
                } catch (e: Exception) {
                    logger.error("Failed to parse DodoPayments webhook payload: ${e.message}", e)
                    return null
                }

        return when (apiModel.type) {
            "checkout.session.completed", "payment.succeeded" -> mapToPaymentSucceeded(apiModel)
            "payment.failed" -> mapToPaymentFailed(apiModel)
            "payment.processing" -> mapToPaymentProcessing(apiModel)
            "payment.cancelled" -> mapToPaymentCancelled(apiModel)
            "subscription.active",
            "subscription.renewed",
            "subscription.updated",
            "subscription.on_hold",
            "subscription.cancelled",
            "subscription.canceled",
            "subscription.expired",
            "subscription.failed",
            -> mapToSubscriptionStatusChanged(apiModel)
            else -> {
                logger.info("Received unsupported DodoPayments event type: ${apiModel.type}")
                null
            }
        }
    }

    private fun mapToPaymentSucceeded(
            model: DodoWebhookPayload
    ): DodoDomainEvent.PaymentSucceeded? {
        val data = model.data
        val metadata = data.metadata
        val userId = metadata["userId"]
        val planId = metadata["canonicalProductId"]

        if (userId.isNullOrBlank()) {
            logger.warn("DodoPayments event ${data.paymentId} missing userId in metadata.")
            return null
        }
        val paymentId = data.paymentId
        if (paymentId.isNullOrBlank()) {
            logger.warn("DodoPayments event missing paymentId for userId=$userId")
            return null
        }
        if (planId.isNullOrBlank()) {
            logger.warn("DodoPayments payment event missing canonicalProductId for userId=$userId paymentId=$paymentId")
            return null
        }
        val paymentType = requirePaymentType(metadata, "payment.succeeded", paymentId) ?: return null

        val createdAtTimestamp = parseCreatedAtTimestamp(data.createdAt, "payment.succeeded", paymentId) ?: return null
        return DodoDomainEvent.PaymentSucceeded(
                paymentId = paymentId,
                userId = userId,
                totalAmount = data.totalAmount,
                currency = data.currency,
                planId = planId,
                paymentType = paymentType,
                status = data.status,
                createdAtTimestamp = createdAtTimestamp
        )
    }

    private fun mapToPaymentFailed(model: DodoWebhookPayload): DodoDomainEvent.PaymentFailed? {
        val userId = model.data.metadata["userId"] ?: return null
        val paymentId = model.data.paymentId ?: return null
        val paymentType = requirePaymentType(model.data.metadata, "payment.failed", paymentId) ?: return null
        val createdAtTimestamp = parseCreatedAtTimestamp(model.data.createdAt, "payment.failed", paymentId) ?: return null
        return DodoDomainEvent.PaymentFailed(
                paymentId = paymentId,
                userId = userId,
                paymentType = paymentType,
                status = model.data.status,
                createdAtTimestamp = createdAtTimestamp
        )
    }

    private fun mapToPaymentProcessing(
            model: DodoWebhookPayload
    ): DodoDomainEvent.PaymentProcessing? {
        val userId = model.data.metadata["userId"] ?: return null
        val paymentId = model.data.paymentId ?: return null
        val paymentType = requirePaymentType(model.data.metadata, "payment.processing", paymentId) ?: return null
        val createdAtTimestamp = parseCreatedAtTimestamp(model.data.createdAt, "payment.processing", paymentId) ?: return null
        return DodoDomainEvent.PaymentProcessing(
                paymentId = paymentId,
                userId = userId,
                paymentType = paymentType,
                status = model.data.status,
                createdAtTimestamp = createdAtTimestamp
        )
    }

    private fun mapToPaymentCancelled(
            model: DodoWebhookPayload
    ): DodoDomainEvent.PaymentCancelled? {
        val userId = model.data.metadata["userId"] ?: return null
        val paymentId = model.data.paymentId ?: return null
        val paymentType = requirePaymentType(model.data.metadata, "payment.cancelled", paymentId) ?: return null
        val createdAtTimestamp = parseCreatedAtTimestamp(model.data.createdAt, "payment.cancelled", paymentId) ?: return null
        return DodoDomainEvent.PaymentCancelled(
                paymentId = paymentId,
                userId = userId,
                paymentType = paymentType,
                status = model.data.status,
                createdAtTimestamp = createdAtTimestamp
        )
    }

    private fun mapToSubscriptionStatusChanged(
            model: DodoWebhookPayload
    ): DodoDomainEvent.SubscriptionStatusChanged? {
        val userId = model.data.metadata["userId"]
        val subscriptionId = model.data.subscriptionId
        if (subscriptionId.isNullOrBlank()) {
            logger.warn(
                "DodoPayments subscription event missing subscriptionId type=${model.type} productId=${model.data.productId} customerEmail=${model.data.customer?.email}"
            )
            return null
        }
        if (userId.isNullOrBlank()) {
            logger.warn(
                "DodoPayments subscription event missing userId metadata type=${model.type} subscriptionId=$subscriptionId productId=${model.data.productId} customerEmail=${model.data.customer?.email}"
            )
            return null
        }
        val planId =
            model.data.metadata["canonicalProductId"]
                ?: model.data.productId?.let { dodoToCanonicalProductIds[it] }
        val amount = model.data.recurringPreTaxAmount ?: model.data.totalAmount
        if (planId.isNullOrBlank()) {
            logger.warn("DodoPayments subscription event missing canonicalProductId for userId=$userId subscriptionId=$subscriptionId productId=${model.data.productId}")
            return null
        }
        val createdAtTimestamp = parseCreatedAtTimestamp(model.data.createdAt, model.type, subscriptionId) ?: return null
        return DodoDomainEvent.SubscriptionStatusChanged(
                subscriptionId = subscriptionId,
                userId = userId,
                totalAmount = amount,
                currency = model.data.currency,
                planId = planId,
                productId = model.data.productId,
                status = model.data.status,
                createdAtTimestamp = createdAtTimestamp
        )
    }

    private fun parseCreatedAtTimestamp(createdAt: String, eventType: String, eventId: String): Long? {
        return try {
            parseTimestamp(createdAt).toEpochMilli()
        } catch (e: Exception) {
            logger.warn("DodoPayments event has invalid created_at: type=$eventType id=$eventId createdAt=$createdAt", e)
            null
        }
    }

    private fun requirePaymentType(
        metadata: Map<String, String>,
        eventType: String,
        eventId: String,
    ): String? {
        val paymentType = metadata["paymentType"]?.trim()
        if (paymentType.isNullOrBlank()) {
            logger.warn("DodoPayments $eventType event missing paymentType in metadata: id=$eventId")
            return null
        }
        return paymentType
    }
}

package com.appforge.server.services.dodopayments

import com.appforge.server.services.billing.SubscriptionBillingHandler
import com.appforge.server.services.dodopayments.models.DodoPaymentsPaymentCompletedWrite
import org.slf4j.LoggerFactory

class DodoSubscriptionHandler(private val billingService: SubscriptionBillingHandler) {
    private val logger = LoggerFactory.getLogger(DodoSubscriptionHandler::class.java)

    fun handle(event: DodoDomainEvent) {
        when (event) {
            is DodoDomainEvent.SubscriptionCancelled -> handleSubscriptionCancelled(event)
            is DodoDomainEvent.SubscriptionStatusChanged -> handleSubscriptionStatusChanged(event)
            else -> Unit
        }
    }

    private fun handleSubscriptionCancelled(event: DodoDomainEvent.SubscriptionCancelled) {
        logger.warn(
            "DodoPayments subscription cancelled for user: ${event.userId}, subscriptionId: ${event.subscriptionId}, status: ${event.status}"
        )
        billingService.cancelSubscription(event.userId, event.planId)
    }

    private fun handleSubscriptionStatusChanged(event: DodoDomainEvent.SubscriptionStatusChanged) {
        when (event.status.lowercase()) {
            "active", "renewed" -> {
                logger.info(
                    "Recording active subscription for user: ${event.userId}, subscriptionId: ${event.subscriptionId}"
                )
                billingService.recordSubscriptionPayment(
                    DodoPaymentsPaymentCompletedWrite(
                        status = event.status,
                        totalAmount = event.totalAmount,
                        currency = event.currency.lowercase(),
                        userId = event.userId,
                        orderId = event.subscriptionId,
                        createdAtTimestamp = event.createdAtTimestamp,
                        planId = event.planId,
                        paymentType = "subscription",
                    )
                )
            }
            "on_hold" -> {
                logger.warn(
                    "Subscription on hold for user: ${event.userId}, subscriptionId: ${event.subscriptionId}"
                )
                billingService.markSubscriptionOnHold(event.userId, event.planId)
            }
            "cancelled", "canceled", "expired", "failed" -> {
                logger.warn(
                    "Subscription ended for user: ${event.userId}, subscriptionId: ${event.subscriptionId}, status: ${event.status}"
                )
                billingService.cancelSubscription(event.userId, event.planId)
            }
            else -> {
                logger.info(
                    "Unhandled subscription status ${event.status} for user: ${event.userId}"
                )
            }
        }
    }
}

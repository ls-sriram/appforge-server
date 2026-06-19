package com.appforge.server.services.dodopayments

import com.appforge.server.services.billing.OneOffBillingHandler
import com.appforge.server.services.dodopayments.models.DodoPaymentsPaymentCompletedWrite
import org.slf4j.LoggerFactory

class DodoOneOffPaymentHandler(private val billingService: OneOffBillingHandler) {
    private val logger = LoggerFactory.getLogger(DodoOneOffPaymentHandler::class.java)

    fun handle(event: DodoDomainEvent) {
        when (event) {
            is DodoDomainEvent.PaymentSucceeded -> handlePaymentSucceeded(event)
            is DodoDomainEvent.PaymentFailed -> handlePaymentFailed(event)
            is DodoDomainEvent.PaymentProcessing -> handlePaymentProcessing(event)
            is DodoDomainEvent.PaymentCancelled -> handlePaymentCancelled(event)
            else -> Unit
        }
    }

    private fun handlePaymentFailed(event: DodoDomainEvent.PaymentFailed) {
        if (event.paymentType == "subscription") return
        logger.error(
            "DodoPayments payment failed for user: ${event.userId}, paymentId: ${event.paymentId}, status: ${event.status}"
        )
    }

    private fun handlePaymentProcessing(event: DodoDomainEvent.PaymentProcessing) {
        if (event.paymentType == "subscription") return
        logger.info(
            "DodoPayments payment processing for user: ${event.userId}, paymentId: ${event.paymentId}, status: ${event.status}"
        )
    }

    private fun handlePaymentCancelled(event: DodoDomainEvent.PaymentCancelled) {
        if (event.paymentType == "subscription") {
            return
        }
        logger.warn(
            "DodoPayments payment cancelled for user: ${event.userId}, paymentId: ${event.paymentId}, status: ${event.status}. No action taken (one-off payment)."
        )
    }

    private fun handlePaymentSucceeded(event: DodoDomainEvent.PaymentSucceeded) {
        if (event.paymentType == "subscription") return
        logger.info(
            "Recording DodoPayments payment for user: ${event.userId}, paymentId: ${event.paymentId}"
        )

        val write =
            DodoPaymentsPaymentCompletedWrite(
                status = event.status,
                totalAmount = event.totalAmount,
                currency = event.currency.lowercase(),
                userId = event.userId,
                orderId = event.paymentId,
                createdAtTimestamp = event.createdAtTimestamp,
                planId = event.planId,
                paymentType = event.paymentType
            )

        billingService.recordOneOffPayment(write)
    }
}

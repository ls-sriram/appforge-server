package com.appforge.server.services.dodopayments

import com.appforge.server.services.billing.OneOffBillingHandler
import com.appforge.server.services.billing.SubscriptionBillingHandler

class DodoEventHandler(
    oneOffBillingHandler: OneOffBillingHandler,
    subscriptionBillingHandler: SubscriptionBillingHandler,
) {
    private val paymentHandler = DodoOneOffPaymentHandler(oneOffBillingHandler)
    private val subscriptionHandler = DodoSubscriptionHandler(subscriptionBillingHandler)

    fun handle(event: DodoDomainEvent) {
        when (event) {
            is DodoDomainEvent.PaymentSucceeded,
            is DodoDomainEvent.PaymentFailed,
            is DodoDomainEvent.PaymentProcessing,
            is DodoDomainEvent.PaymentCancelled,
            -> paymentHandler.handle(event)
            is DodoDomainEvent.SubscriptionCancelled,
            is DodoDomainEvent.SubscriptionStatusChanged,
            -> subscriptionHandler.handle(event)
        }
    }
}

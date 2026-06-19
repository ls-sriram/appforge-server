package com.appforge.server.services.billing

import com.appforge.server.services.billing.models.BillingSource
import com.appforge.server.services.dodopayments.models.DodoPaymentsPaymentCompletedWrite
import kotlinx.coroutines.runBlocking

interface OneOffBillingHandler {
    fun recordOneOffPayment(write: DodoPaymentsPaymentCompletedWrite)
    fun revokeOneOffPayment(userId: String)
}

interface SubscriptionBillingHandler {
    fun recordSubscriptionPayment(write: DodoPaymentsPaymentCompletedWrite)
    fun cancelSubscription(userId: String, planId: String? = null)
    fun markSubscriptionOnHold(userId: String, planId: String? = null)
}

class OneOffBillingService(
    private val coordinator: BillingCoordinator,
) : OneOffBillingHandler {

    override fun recordOneOffPayment(write: DodoPaymentsPaymentCompletedWrite) {
        runBlocking {
            coordinator.handleOneOffPayment(
                userId = write.userId,
                externalCustomerId = null, // Dodo customerId not in the restricted set
                externalReferenceId = write.orderId,
                amountCents = write.totalAmount,
                currency = write.currency,
                source = BillingSource.DODO_PAYMENTS,
                planId = write.planId
            )
        }
    }

    override fun revokeOneOffPayment(userId: String) {
        runBlocking { coordinator.revokeOneOffPayment(userId) }
    }
}

class SubscriptionBillingService(
    private val coordinator: BillingCoordinator,
) : SubscriptionBillingHandler {
    override fun recordSubscriptionPayment(write: DodoPaymentsPaymentCompletedWrite) {
        runBlocking {
            coordinator.handleSubscriptionPayment(
                userId = write.userId,
                externalCustomerId = null,
                externalReferenceId = write.orderId,
                amountCents = write.totalAmount,
                currency = write.currency,
                source = BillingSource.DODO_PAYMENTS,
                planId = write.planId
            )
        }
    }

    override fun cancelSubscription(userId: String, planId: String?) {
        runBlocking { coordinator.cancelSubscription(userId, planId) }
    }

    override fun markSubscriptionOnHold(userId: String, planId: String?) {
        runBlocking { coordinator.markSubscriptionOnHold(userId, planId) }
    }
}

package com.appforge.server.services.dodopayments

import com.appforge.server.api.CheckoutRequest
import com.appforge.server.api.CheckoutResponse
import com.appforge.server.clients.DodoPaymentsDataClient
import com.appforge.server.config.AppEnv
import com.appforge.server.services.billing.OneOffBillingHandler
import com.appforge.server.services.billing.SubscriptionBillingHandler
import com.appforge.server.services.billing.repository.BillingAuditRepositoryApi
import java.time.Clock

class DodoPaymentsService(
        dodoPaymentsClient: DodoPaymentsDataClient,
        oneOffBillingHandler: OneOffBillingHandler,
        subscriptionBillingHandler: SubscriptionBillingHandler,
        env: AppEnv,
        auditRepository: BillingAuditRepositoryApi,
        clock: Clock = Clock.systemUTC(),
) {
    private val verifier = DodoWebhookVerifier(env.dodoPayments.dodoPaymentsWebhookKey)
    private val parser = DodoWebhookParser(env.billing.dodoProductIds)
    private val eventHandler = DodoEventHandler(oneOffBillingHandler, subscriptionBillingHandler)

    private val coordinator =
            DodoPaymentsCoordinator(
                    dodoPaymentsClient = dodoPaymentsClient,
                    verifier = verifier,
                    parser = parser,
                    eventHandler = eventHandler,
                    auditRepository = auditRepository,
                    billingOptions = env.billing,
                    clock = clock,
            )

    fun createCheckoutSession(
            request: CheckoutRequest,
            userId: String,
            origin: String
    ): CheckoutResponse {
        return coordinator.createCheckoutSession(request, userId, origin)
    }

    fun cancelSubscriptionAtPeriodEnd(subscriptionId: String) {
        coordinator.cancelSubscriptionAtPeriodEnd(subscriptionId)
    }

    fun handleWebhook(payload: String, signature: String, webhookId: String?, timestamp: String?) {
        coordinator.handleWebhook(payload, signature, webhookId, timestamp)
    }
}

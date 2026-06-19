package com.appforge.server.services.billing

import com.appforge.server.api.CheckoutRequest
import com.appforge.server.api.CheckoutResponse
import com.appforge.server.api.BillingSourceDto
import com.appforge.server.api.BillingStatusDto
import com.appforge.server.api.EntitlementFeatureDto
import com.appforge.server.api.EntitlementSnapshotResponse
import com.appforge.server.api.PlanDto
import com.appforge.server.api.PricingCardsResponse
import com.appforge.server.api.WebhookResponse
import com.appforge.server.infrastructure.time.instantToProtoTimestamp
import com.appforge.server.services.billing.models.BillingPaymentType
import com.appforge.server.services.billing.models.BillingSource
import com.appforge.server.services.billing.models.BillingStatus
import kotlinx.coroutines.runBlocking

interface BillingUseCases {
    fun listPricingCards(): PricingCardsResponse
    fun entitlement(userId: String): EntitlementSnapshotResponse
    fun checkout(userId: String, request: CheckoutRequest, origin: String): CheckoutResponse
    fun cancelSubscription(userId: String)
    fun handleWebhook(payload: String, signature: String?, webhookId: String?, timestamp: String?): WebhookResponse
}

class BillingUseCasesImpl(
    private val billingCoordinator: BillingCoordinator,
    private val dodoPaymentsService: com.appforge.server.services.dodopayments.DodoPaymentsService,
    private val ensureUserExists: suspend (String) -> Unit,
) : BillingUseCases {
    override fun listPricingCards(): PricingCardsResponse {
        return billingCoordinator.listPricingCards()
    }

    override fun entitlement(userId: String): EntitlementSnapshotResponse {
        val entitlement = runBlocking {
            ensureUserExists(userId)
            billingCoordinator.getOrCreateDefaultEntitlement(userId)
        }

        val features = entitlement.features.map { (key, value) ->
            EntitlementFeatureDto(
                key = key,
                title = titleForFeature(key),
                unlocked = value.unlocked,
                used = value.used,
                limit = value.limit,
            )
        }.sortedBy { it.key }

        return EntitlementSnapshotResponse(
            userId = userId,
            plan = when (entitlement.plan.wire) {
                "free" -> PlanDto.FREE
                "trial" -> PlanDto.TRIAL
                else -> PlanDto.PRO
            },
            status = when (entitlement.status.wire) {
                "active" -> BillingStatusDto.ACTIVE
                "trialing" -> BillingStatusDto.TRIALING
                "cancel_pending" -> BillingStatusDto.CANCEL_PENDING
                "past_due" -> BillingStatusDto.PAST_DUE
                else -> BillingStatusDto.CANCELED
            },
            source = when (entitlement.source.wire) {
                "manual" -> BillingSourceDto.MANUAL
                "trial" -> BillingSourceDto.TRIAL
                else -> BillingSourceDto.DODO_PAYMENTS
            },
            startedAt = instantToProtoTimestamp(entitlement.startedAt)!!,
            expiresAt = instantToProtoTimestamp(entitlement.expiresAt),
            updatedAt = instantToProtoTimestamp(entitlement.updatedAt)!!,
            features = features,
        )
    }

    override fun checkout(userId: String, request: CheckoutRequest, origin: String): CheckoutResponse {
        return dodoPaymentsService.createCheckoutSession(request, userId, origin)
    }

    override fun cancelSubscription(userId: String) {
        runBlocking {
            val entitlement = billingCoordinator.getEntitlement(userId)
            val subscriptionId = entitlement?.externalReferenceId
            val isActiveDodoEntitlement =
                entitlement?.source == BillingSource.DODO_PAYMENTS &&
                    entitlement.billingType == BillingPaymentType.SUBSCRIPTION &&
                    entitlement.status == BillingStatus.ACTIVE
            require(isActiveDodoEntitlement) { "No active subscription found to cancel." }
            require(!subscriptionId.isNullOrBlank()) { "No subscription reference found to cancel." }
            dodoPaymentsService.cancelSubscriptionAtPeriodEnd(subscriptionId)
            billingCoordinator.markSubscriptionCancelPending(userId)
        }
    }

    override fun handleWebhook(
        payload: String,
        signature: String?,
        webhookId: String?,
        timestamp: String?
    ): WebhookResponse {
        if (signature.isNullOrBlank()) {
            throw IllegalArgumentException("Missing signature")
        }
        dodoPaymentsService.handleWebhook(payload, signature, webhookId, timestamp)
        return WebhookResponse(received = true)
    }

    private fun titleForFeature(featureKey: String): String =
        when (featureKey) {
            "review_submissions" -> "Review Submissions"
            "entity_creations" -> "Entity Creations"
            "api_requests" -> "API Requests"
            "shared_links" -> "Shared Links"
            "storage_bytes" -> "Storage Bytes"
            else -> featureKey.replace("_", " ").split(" ").joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
        }
}

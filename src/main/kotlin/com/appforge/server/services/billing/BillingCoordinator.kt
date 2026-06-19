package com.appforge.server.services.billing

import com.appforge.server.api.PricingCardDto
import com.appforge.server.api.PricingCardsResponse
import com.appforge.server.services.billing.catalog.BillingCatalog
import com.appforge.server.services.billing.catalog.BillingFeatureCatalog
import com.appforge.server.services.billing.models.BillingEntitlement
import com.appforge.server.services.billing.models.BillingPaymentType
import com.appforge.server.services.billing.models.BillingSource
import com.appforge.server.services.billing.models.BillingStatus
import com.appforge.server.services.billing.models.PaymentRecord
import com.appforge.server.services.billing.models.Plan
import com.appforge.server.services.billing.repository.BillingRepositoryApi
import java.time.Clock
import com.appforge.server.infrastructure.time.*
import org.slf4j.LoggerFactory

class BillingCoordinator(
        private val repository: BillingRepositoryApi,
        private val emailCoordinator: BillingEmailCoordinator? = null,
        private val clock: Clock = Clock.systemUTC(),
) {
        private val logger = LoggerFactory.getLogger(BillingCoordinator::class.java)

        suspend fun getEntitlement(userId: String): BillingEntitlement? = repository.get(userId)

        suspend fun getOrCreateDefaultEntitlement(userId: String): BillingEntitlement {
                repository.get(userId)?.let { return it }

                val now = clock.nowTimestamp()
                val entitlement =
                        BillingEntitlement(
                                customerId = userId,
                                plan = Plan.FREE,
                                status = BillingStatus.ACTIVE,
                                expiresAt = null,
                                startedAt = now,
                                source = BillingSource.MANUAL,
                                features = BillingFeatureCatalog.defaultForPlan(Plan.FREE),
                                createdAt = now,
                                updatedAt = now,
                        )
                logger.info("No entitlement found for user {}; creating default FREE entitlement", userId)
                repository.upsert(userId, entitlement)
                return entitlement
        }

        fun listPricingCards(): PricingCardsResponse {
                val cards =
                        BillingCatalog.PRODUCTS.map { product ->
                                val price = product.pricing.amountCents / 100.0
                                val originalPrice =
                                        product.originalPricing?.let { it.amountCents / 100.0 }
                                val totalSavings =
                                        product.originalPricing?.let {
                                                (it.amountCents - product.pricing.amountCents) /
                                                        100.0
                                        }

                                val durationLabel =
                                        when (product.productId) {
                                                BillingCatalog.PRO_MONTHLY -> "Monthly"
                                                BillingCatalog.PRO_ANNUAL -> "Annual"
                                                else -> "${product.plan.validityDays} Days"
                                        }

                                val savingsLabel =
                                        totalSavings?.let { String.format("Save $%.0f", it) }

                                val monthlyPriceLabel =
                                        when (product.productId) {
                                                BillingCatalog.PRO_MONTHLY ->
                                                        String.format("≈ $%.0f/month", price)
                                                BillingCatalog.PRO_ANNUAL ->
                                                        String.format("≈ $%.0f/month", price / 12.0)
                                                else -> null
                                        }

                                PricingCardDto(
                                        id = product.productId,
                                        priceId = product.productId,
                                        name = product.name,
                                        duration = durationLabel,
                                        price = String.format("%.0f", price),
                                        originalPrice =
                                                originalPrice?.let { String.format("%.0f", it) },
                                        savings = savingsLabel,
                                        description = product.description,
                                        featured = product.featured,
                                        monthlyPrice = monthlyPriceLabel,
                                        features = product.features
                                )
                        }
                return PricingCardsResponse(cards = cards)
        }

        suspend fun revokeOneOffPayment(userId: String) {
                val now = clock.nowTimestamp()
                val current = repository.get(userId) ?: return
                val previousPlan = current.plan

                val entitlement =
                        current.copy(
                                plan = Plan.FREE,
                                status = BillingStatus.CANCELED,
                                updatedAt = now,
                                features = BillingFeatureCatalog.defaultForPlan(Plan.FREE),
                        )

                logger.info("Revoking one-off payment: downgrading user {} to FREE plan", userId)
                repository.upsert(userId, entitlement)
                emailCoordinator?.sendPlanChangedIfNeeded(
                        userId = userId,
                        previousPlan = previousPlan,
                        newPlan = entitlement.plan,
                        source = entitlement.source,
                )
        }

        suspend fun handleOneOffPayment(
                userId: String,
                externalCustomerId: String?,
                externalReferenceId: String?,
                amountCents: Long,
                currency: String,
                source: BillingSource,
                planId: String? = null,
        ) {
                val now = clock.nowTimestamp()
                val current = repository.get(userId)
                val previousPlan = current?.plan
	                val existingExpiresAt = current?.expiresAt
	                val base = existingExpiresAt?.takeIf { it.isAfter(now) } ?: now
	                require(!planId.isNullOrBlank()) { "planId (canonical productId) is required" }
	                val canonicalProductId = planId
	                val expiry = BillingCatalog.resolveExpiry(base = base, canonicalProductId = canonicalProductId)

                val resolvedPlan = resolvePlan(planId, current?.plan ?: Plan.FREE)

                val entitlement =
                        BillingEntitlement(
                                customerId = externalCustomerId ?: current?.customerId ?: userId,
                                plan = resolvedPlan,
                                status = BillingStatus.ACTIVE,
                                expiresAt = expiry,
                                startedAt = current?.startedAt ?: now,
                                externalCustomerId = externalCustomerId
                                                ?: current?.externalCustomerId,
                                externalReferenceId = externalReferenceId
                                                ?: current?.externalReferenceId,
                                billingType = BillingPaymentType.ONE_TIME,
                                lastPaymentAmountCents = amountCents,
                                lastPaymentCurrency = currency,
                                source = source,
                                features = BillingFeatureCatalog.defaultForPlan(resolvedPlan),
                                createdAt = current?.createdAt ?: now,
                                updatedAt = now,
                        )

                logger.info(
                        "{} one-off payment: upserting entitlement for user {} with plan {}",
                        source,
                        userId,
                        resolvedPlan.wire
                )
                repository.upsert(userId, entitlement)
                emailCoordinator?.sendPlanChangedIfNeeded(
                        userId = userId,
                        previousPlan = previousPlan,
                        newPlan = entitlement.plan,
                        source = entitlement.source,
                )

                val emailSentAt =
                        emailCoordinator?.sendPaymentConfirmationIfNeeded(
                                userId = userId,
                                externalReferenceId = externalReferenceId,
                                amountCents = amountCents,
                                currency = currency,
                                planName = resolvedPlan.displayName,
                                now = now,
                        )

                // Record payment history
                repository.recordPayment(
                        userId,
	                        PaymentRecord(
	                                date = now,
	                                amountCents = amountCents,
	                                currency = currency,
	                                planId = canonicalProductId,
	                                emailSentAt = emailSentAt
	                        ),
	                        recordId = externalReferenceId
	                )
        }

        suspend fun handleSubscriptionPayment(
                userId: String,
                externalCustomerId: String?,
                externalReferenceId: String?,
                amountCents: Long,
                currency: String,
                source: BillingSource,
                planId: String? = null,
        ) {
                val now = clock.nowTimestamp()
                val current = repository.get(userId)
                val previousPlan = current?.plan
	                val existingExpiresAt = current?.expiresAt
	                val base = existingExpiresAt?.takeIf { it.isAfter(now) } ?: now
	                require(!planId.isNullOrBlank()) { "planId (canonical productId) is required" }
	                val canonicalProductId = planId
	                val expiry = BillingCatalog.resolveExpiry(base = base, canonicalProductId = canonicalProductId)

                val resolvedPlan = resolvePlan(planId, current?.plan ?: Plan.FREE)

                val entitlement =
                        BillingEntitlement(
                                customerId = externalCustomerId ?: current?.customerId ?: userId,
                                plan = resolvedPlan,
                                status = BillingStatus.ACTIVE,
                                expiresAt = expiry,
                                startedAt = current?.startedAt ?: now,
                                externalCustomerId = externalCustomerId
                                                ?: current?.externalCustomerId,
                                externalReferenceId = externalReferenceId
                                                ?: current?.externalReferenceId,
                                billingType = BillingPaymentType.SUBSCRIPTION,
                                lastPaymentAmountCents = amountCents,
                                lastPaymentCurrency = currency,
                                source = source,
                                features = BillingFeatureCatalog.defaultForPlan(resolvedPlan),
                                createdAt = current?.createdAt ?: now,
                                updatedAt = now,
                        )

                logger.info(
                        "{} subscription payment: upserting entitlement for user {} with plan {}",
                        source,
                        userId,
                        resolvedPlan.wire
                )
                repository.upsert(userId, entitlement)
                emailCoordinator?.sendPlanChangedIfNeeded(
                        userId = userId,
                        previousPlan = previousPlan,
                        newPlan = entitlement.plan,
                        source = entitlement.source,
                )

                val emailSentAt =
                        emailCoordinator?.sendPaymentConfirmationIfNeeded(
                                userId = userId,
                                externalReferenceId = externalReferenceId,
                                amountCents = amountCents,
                                currency = currency,
                                planName = resolvedPlan.displayName,
                                now = now,
                        )

                repository.recordPayment(
                        userId,
	                        PaymentRecord(
	                                date = now,
	                                amountCents = amountCents,
	                                currency = currency,
	                                planId = canonicalProductId,
	                                emailSentAt = emailSentAt
	                        ),
	                        recordId = externalReferenceId
	                )
        }

        suspend fun cancelSubscription(userId: String, planId: String? = null) {
                val now = clock.nowTimestamp()
                val current = repository.get(userId) ?: return
                val resolvedPlan = resolvePlan(planId, current.plan)
                val entitlement =
                        current.copy(
                                plan = resolvedPlan,
                                status = BillingStatus.CANCELED,
                                updatedAt = now,
                                features = BillingFeatureCatalog.defaultForPlan(resolvedPlan),
                        )
                logger.info("Subscription canceled: updating entitlement for user {}", userId)
                repository.upsert(userId, entitlement)
        }

        suspend fun markSubscriptionCancelPending(userId: String, planId: String? = null) {
                val now = clock.nowTimestamp()
                val current = repository.get(userId) ?: return
                val resolvedPlan = resolvePlan(planId, current.plan)
                val entitlement =
                        current.copy(
                                plan = resolvedPlan,
                                status = BillingStatus.CANCEL_PENDING,
                                updatedAt = now,
                                features = BillingFeatureCatalog.defaultForPlan(resolvedPlan),
                        )
                logger.info("Subscription cancel pending: updating entitlement for user {}", userId)
                repository.upsert(userId, entitlement)
        }

        suspend fun markSubscriptionOnHold(userId: String, planId: String? = null) {
                val now = clock.nowTimestamp()
                val current = repository.get(userId) ?: return
                val resolvedPlan = resolvePlan(planId, current.plan)
                val entitlement =
                        current.copy(
                                plan = resolvedPlan,
                                status = BillingStatus.PAST_DUE,
                                updatedAt = now,
                                features = BillingFeatureCatalog.defaultForPlan(resolvedPlan),
                        )
                logger.info("Subscription on hold: marking past due for user {}", userId)
                repository.upsert(userId, entitlement)
        }

        private fun resolvePlan(planId: String?, fallback: Plan): Plan =
                when (planId) {
                        BillingCatalog.PRO_MONTHLY, BillingCatalog.PRO_ANNUAL -> Plan.PRO
                        null -> fallback
                        else -> throw IllegalArgumentException("Unsupported canonical productId: $planId")
                }
}

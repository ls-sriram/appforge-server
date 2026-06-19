package com.appforge.server.services.billing

import com.appforge.server.api.UsageFeatureResponse
import com.appforge.server.api.UserPlanDto
import com.appforge.server.api.UserPlanResponse
import com.appforge.server.api.UserPlanSourceDto
import com.appforge.server.api.UserPlanStatusDto
import com.appforge.server.api.UserUsageResponse
import com.appforge.server.infrastructure.time.instantToProtoTimestamp
import com.appforge.server.services.billing.catalog.BillingFeatureCatalog
import com.appforge.server.services.billing.models.BillingEntitlement

interface EntitlementService {
    fun toPlan(entitlement: BillingEntitlement?): UserPlanResponse?
    fun toUsage(entitlement: BillingEntitlement?): UserUsageResponse?
}

class EntitlementServiceImpl : EntitlementService {
    override fun toPlan(entitlement: BillingEntitlement?): UserPlanResponse? {
        val value = entitlement ?: return null
        return UserPlanResponse(
            name = when (value.plan.wire) {
                "free" -> UserPlanDto.FREE
                "trial" -> UserPlanDto.TRIAL
                else -> UserPlanDto.PRO
            },
            status = when (value.status.wire) {
                "active" -> UserPlanStatusDto.ACTIVE
                "trialing" -> UserPlanStatusDto.TRIALING
                "cancel_pending" -> UserPlanStatusDto.CANCEL_PENDING
                "past_due" -> UserPlanStatusDto.PAST_DUE
                else -> UserPlanStatusDto.CANCELED
            },
            expiresAt = instantToProtoTimestamp(value.expiresAt),
            startedAt = instantToProtoTimestamp(value.startedAt),
            source = when (value.source.wire) {
                "manual" -> UserPlanSourceDto.MANUAL
                "trial" -> UserPlanSourceDto.TRIAL
                else -> UserPlanSourceDto.DODO_PAYMENTS
            },
            cancelAtPeriodEnd = value.status.wire == "cancel_pending",
            checkoutUrl = null,
        )
    }

    override fun toUsage(entitlement: BillingEntitlement?): UserUsageResponse? {
        val value = entitlement ?: return null
        val features = value.features
        fun usageFor(key: String): UsageFeatureResponse {
            val feature = features[key]
            return UsageFeatureResponse(
                used = feature?.used ?: 0L,
                limit = feature?.limit ?: 0L,
                unlocked = feature?.unlocked ?: false,
            )
        }
        return UserUsageResponse(
            reviewSubmissions = usageFor(BillingFeatureCatalog.Keys.REVIEW_SUBMISSIONS),
            entityCreations = usageFor(BillingFeatureCatalog.Keys.ENTITY_CREATIONS),
            apiRequests = usageFor(BillingFeatureCatalog.Keys.API_REQUESTS),
            sharedLinks = usageFor(BillingFeatureCatalog.Keys.SHARED_LINKS),
            storageBytes = usageFor(BillingFeatureCatalog.Keys.STORAGE_BYTES),
        )
    }
}


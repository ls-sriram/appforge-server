package com.appforge.server.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class CheckoutPaymentType {
        @SerialName("subscription")
        SUBSCRIPTION,
        @SerialName("one_time")
        ONE_TIME,
}

@Serializable
enum class PlanDto {
        @SerialName("free")
        FREE,
        @SerialName("trial")
        TRIAL,
        @SerialName("pro")
        PRO,
}

@Serializable
enum class BillingStatusDto {
        @SerialName("active")
        ACTIVE,
        @SerialName("trialing")
        TRIALING,
        @SerialName("cancel_pending")
        CANCEL_PENDING,
        @SerialName("past_due")
        PAST_DUE,
        @SerialName("canceled")
        CANCELED,
}

@Serializable
enum class BillingSourceDto {
        @SerialName("manual")
        MANUAL,
        @SerialName("trial")
        TRIAL,
        @SerialName("dodo_payments")
        DODO_PAYMENTS,
}

@Serializable
data class CheckoutRequest(
        val priceId: String? = null,
        val paymentType: CheckoutPaymentType = CheckoutPaymentType.SUBSCRIPTION,
        val amountCents: Long? = null,
        val currency: String? = null,
        val customerEmail: String,
        val successUrl: String? = null,
        val cancelUrl: String? = null,
        val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class CheckoutResponse(
        val sessionId: String,
        val url: String?,
)

@Serializable
data class PricingCardDto(
        val id: String,
        val priceId: String,
        val name: String,
        val duration: String,
        val price: String,
        val originalPrice: String? = null,
        val savings: String? = null,
        val description: String,
        val featured: Boolean = false,
        val monthlyPrice: String? = null,
        val features: List<String>,
)

@Serializable
data class PricingCardsResponse(
        val cards: List<PricingCardDto>,
)

@Serializable
data class EntitlementFeatureDto(
        val key: String,
        val title: String,
        val unlocked: Boolean,
        val used: Long,
        val limit: Long? = null,
)

@Serializable
data class EntitlementSnapshotResponse(
        val userId: String,
        val plan: PlanDto,
        val status: BillingStatusDto,
        val source: BillingSourceDto,
        val startedAt: ProtoTimestamp,
        val expiresAt: ProtoTimestamp? = null,
        val updatedAt: ProtoTimestamp,
        val features: List<EntitlementFeatureDto>,
)

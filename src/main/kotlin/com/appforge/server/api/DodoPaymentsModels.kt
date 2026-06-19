package com.appforge.server.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DodoPriceDto(
        val id: String,
        val productId: String,
        val amount: Long,
        val currency: String,
        val paymentTerm: String, // e.g. "one_time" or "monthly"
)

@Serializable
data class DodoProductDto(
        val id: String,
        val name: String,
        val description: String?,
        val image: String?,
)

@Serializable
data class DodoProductWithPriceDto(
        val product: DodoProductDto,
        val price: DodoPriceDto,
)

@Serializable
data class DodoProductsResponse(
        val products: List<DodoProductWithPriceDto>,
)

@Serializable
data class DodoWebhookPayload(
        val type: String,
        val data: DodoWebhookData,
)

@Serializable
data class DodoWebhookData(
        @SerialName("payment_id") val paymentId: String? = null,
        @SerialName("subscription_id") val subscriptionId: String? = null,
        @SerialName("product_id") val productId: String? = null,
        val status: String,
        @SerialName("total_amount") val totalAmount: Long = 0,
        @SerialName("recurring_pre_tax_amount") val recurringPreTaxAmount: Long? = null,
        val currency: String,
        @SerialName("created_at") val createdAt: String,
        @SerialName("payment_frequency_count") val paymentFrequencyCount: Int? = null,
        @SerialName("payment_frequency_interval") val paymentFrequencyInterval: String? = null,
        @SerialName("subscription_period_count") val subscriptionPeriodCount: Int? = null,
        @SerialName("subscription_period_interval") val subscriptionPeriodInterval: String? = null,
        val customer: DodoWebhookCustomer? = null,
        val metadata: Map<String, String> = emptyMap(),
)

@Serializable
data class DodoWebhookCustomer(
        @SerialName("customer_id") val customerId: String,
        val email: String,
        val name: String? = null,
)

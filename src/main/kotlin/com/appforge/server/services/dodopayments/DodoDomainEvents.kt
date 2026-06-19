package com.appforge.server.services.dodopayments

sealed class DodoDomainEvent {
        data class PaymentSucceeded(
                val paymentId: String,
                val userId: String,
                val totalAmount: Long,
                val currency: String,
                val planId: String?,
                val paymentType: String,
                val status: String,
                val createdAtTimestamp: Long
        ) : DodoDomainEvent()

        data class PaymentFailed(
                val paymentId: String,
                val userId: String,
                val paymentType: String,
                val status: String,
                val createdAtTimestamp: Long
        ) : DodoDomainEvent()

        data class PaymentProcessing(
                val paymentId: String,
                val userId: String,
                val paymentType: String,
                val status: String,
                val createdAtTimestamp: Long
        ) : DodoDomainEvent()

        data class PaymentCancelled(
                val paymentId: String,
                val userId: String,
                val paymentType: String,
                val status: String,
                val createdAtTimestamp: Long
        ) : DodoDomainEvent()

        data class SubscriptionCancelled(
                val subscriptionId: String,
                val userId: String,
                val status: String,
                val createdAtTimestamp: Long,
                val planId: String?,
        ) : DodoDomainEvent()

        data class SubscriptionStatusChanged(
                val subscriptionId: String,
                val userId: String,
                val totalAmount: Long,
                val currency: String,
                val planId: String?,
                val productId: String?,
                val status: String,
                val createdAtTimestamp: Long
        ) : DodoDomainEvent()
}

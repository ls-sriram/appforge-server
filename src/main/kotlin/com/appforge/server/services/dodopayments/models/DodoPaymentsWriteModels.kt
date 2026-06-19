package com.appforge.server.services.dodopayments.models

data class DodoPaymentsPaymentCompletedWrite(
        val provider: String = "dodopayments",
        val status: String,
        val totalAmount: Long,
        val currency: String,
        val userId: String,
        val orderId: String,
        val createdAtTimestamp: Long,
        val planId: String? = null,
        val paymentType: String = "one_time",
)

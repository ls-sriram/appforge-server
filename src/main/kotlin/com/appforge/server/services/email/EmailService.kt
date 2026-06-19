package com.appforge.server.services.email

interface EmailService {
    suspend fun sendPaymentConfirmation(
        to: String,
        amount: String,
        currency: String,
        planName: String,
        transactionId: String
    )

    suspend fun sendEmail(
        to: String,
        subject: String,
        content: String,
        isHtml: Boolean = true
    )
}

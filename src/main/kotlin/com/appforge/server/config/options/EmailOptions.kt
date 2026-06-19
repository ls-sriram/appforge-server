package com.appforge.server.config.options

import com.appforge.server.config.ConfigReader
import com.appforge.server.config.ProductDetails

data class EmailOptions(
    val enabled: Boolean,
    val zeptoMailSendToken: String,
    val zeptoMailApiUrl: String,
    val fromEmail: String,
    val fromName: String
) {
    companion object {
        fun load(reader: ConfigReader): EmailOptions {
            val enabled = reader.bool("EMAIL_ENABLED") ?: true
            return EmailOptions(
                enabled = enabled,
                zeptoMailSendToken = if (enabled) {
                    reader.requiredString("ZEPTOMAIL_SEND_MAIL_TOKEN")
                } else {
                    reader.string("ZEPTOMAIL_SEND_MAIL_TOKEN") ?: ""
                },
                zeptoMailApiUrl = reader.string("ZEPTOMAIL_API_URL") ?: "https://api.zeptomail.com/v1.1/email",
                fromEmail = reader.string("EMAIL_FROM_ADDRESS") ?: ProductDetails.defaultFromAddress,
                fromName = reader.string("EMAIL_FROM_NAME") ?: ProductDetails.productName
            )
        }
    }
}

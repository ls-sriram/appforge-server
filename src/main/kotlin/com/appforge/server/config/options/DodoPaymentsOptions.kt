package com.appforge.server.config.options

import com.appforge.server.config.ConfigReader

data class DodoPaymentsOptions(
    val enabled: Boolean,
    val dodoPaymentsApiKey: String,
    val dodoPaymentsWebhookKey: String?,
    val dodoPaymentsBaseUrl: String,
) {
    companion object {
        fun load(reader: ConfigReader): DodoPaymentsOptions {
            val enabled = reader.bool("DODO_PAYMENTS_ENABLED") ?: true
            return DodoPaymentsOptions(
                enabled = enabled,
                dodoPaymentsApiKey = if (enabled) {
                    reader.requiredString("DODO_PAYMENTS_API_KEY")
                } else {
                    reader.string("DODO_PAYMENTS_API_KEY") ?: ""
                },
                dodoPaymentsWebhookKey = reader.string("DODO_PAYMENTS_WEBHOOK_KEY"),
                dodoPaymentsBaseUrl = reader.string("DODO_PAYMENTS_BASE_URL") ?: "https://live.dodopayments.com",
            )
        }
    }
}

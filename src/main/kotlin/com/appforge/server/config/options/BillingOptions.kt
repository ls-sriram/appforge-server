package com.appforge.server.config.options

import com.appforge.server.config.ConfigReader

data class BillingOptions(
    val trialDurationDays: Long,
    val dodoProductIds: Map<String, String>,
) {
    companion object {
        private const val DEFAULT_TRIAL_DURATION_DAYS = 7L

        fun load(reader: ConfigReader): BillingOptions =
            BillingOptions(
                trialDurationDays = reader.long("TRIAL_DURATION_DAYS")
                    ?: DEFAULT_TRIAL_DURATION_DAYS,
                dodoProductIds = mapOf(
                    "pro_monthly" to (reader.string("DODO_PRODUCT_ID_PRO_MONTHLY") ?: "pdt_pro_monthly"),
                    "pro_annual" to (reader.string("DODO_PRODUCT_ID_PRO_ANNUAL") ?: "pdt_pro_annual"),
                )
            )
    }
}

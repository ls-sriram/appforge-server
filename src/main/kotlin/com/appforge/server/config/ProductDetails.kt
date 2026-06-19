package com.appforge.server.config

/**
 * Central product branding/details for template customization.
 *
 * Keep product-specific names/URLs here so re-branding does not require
 * touching service/template files.
 */
object ProductDetails {
    const val productName = "AppForge"
    const val teamName = "AppForge Team"
    const val supportTagline = "Helping you get the most out of the platform."

    const val webBaseUrl = "https://yourdomain.com/web"
    const val dashboardPath = "/home"
    const val billingSettingsPath = "/settings"
    const val signupPath = "/signin?tab=register"

    val dashboardUrl: String
        get() = "$webBaseUrl$dashboardPath"

    val billingSettingsUrl: String
        get() = "$webBaseUrl$billingSettingsPath"

    val earlyAccessSignupUrl: String
        get() = "$webBaseUrl$signupPath"

    val defaultFromAddress: String
        get() = "noreply@yourdomain.com"
}

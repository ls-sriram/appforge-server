package com.appforge.server.services.billing.catalog

import com.appforge.server.infrastructure.time.*
import java.time.ZoneOffset

/* ---------- Tier ---------- */

enum class Tier {
    TIER1,
    TIER2,
    TIER3
}

/* ---------- Pricing (money) ---------- */

data class Pricing(val amountCents: Int, val currency: String)

enum class ProductBillingType {
    SUBSCRIPTION,
    ONE_TIME
}

/* ---------- Plan (entitlement policy) ---------- */

data class Plan(val planId: String, val tier: Tier, val validityDays: Int, val revocable: Boolean)

/* ---------- Product (what user buys) ---------- */

data class Product(
        val productId: String,
        val name: String,
        val description: String,
        val billingType: ProductBillingType,
        val features: List<String>,
        val color: String,
        val featured: Boolean,
        val pricing: Pricing,
        val originalPricing: Pricing? = null,
        val plan: Plan
)

/* ---------- Canonical catalog ---------- */

/**
 * SOURCE OF TRUTH: Billing Catalog
 *
 * This object defines the pricing plans displayed to the user on the frontend.
 *
 * IMPORTANT:
 * 1. The 'productId' here must match the alias used in DodoPaymentsCoordinator's productIdMap.
 * 2. The 'amountCents' MUST be manually synchronized with the price configured in the Dodo Payments dashboard.
 * 3. The frontend fetches this catalog via the /billing/pricing-cards endpoint to construct the UI.
 * 4. If these values are out of sync with Dodo, the user will see one price but be charged another.
 */
object BillingCatalog {

    const val PRO_MONTHLY = "pro_monthly"
    const val PRO_ANNUAL = "pro_annual"

    val PRO_MONTHLY_PRODUCT =
            Product(
                    productId = PRO_MONTHLY,
                    name = "Pro Monthly",
                    description = "$19/month, renews automatically",
                    billingType = ProductBillingType.SUBSCRIPTION,
                    features =
                            listOf(
                                    "100 API requests/month",
                                    "100 entities/month",
                                    "Unlimited sharing",
                                    "Priority support",
                            ),
                    color = "slate",
                    featured = true,
                    pricing = Pricing(amountCents = 19_00, currency = "USD"),
                    originalPricing = null,
                    plan =
                            Plan(
                                    planId = "pro",
                                    tier = Tier.TIER1,
                                    validityDays = 30,
                                    revocable = true
                            )
            )

    val PRO_ANNUAL_PRODUCT =
            Product(
                    productId = PRO_ANNUAL,
                    name = "Pro Annual",
                    description = "$199 upfront for 1 year access",
                    billingType = ProductBillingType.ONE_TIME,
                    features =
                            listOf(
                                    "100 API requests/month",
                                    "100 entities/month",
                                    "Unlimited sharing",
                                    "Priority support",
                            ),
                    color = "slate",
                    featured = false,
                    pricing = Pricing(amountCents = 199_00, currency = "USD"),
                    originalPricing = null,
                    plan =
                            Plan(
                                    planId = "pro",
                                    tier = Tier.TIER2,
                                    validityDays = 365,
                                    revocable = true
                            )
            )

    val PRODUCTS: List<Product> = listOf(PRO_MONTHLY_PRODUCT, PRO_ANNUAL_PRODUCT)

    fun getById(productId: String): Product =
            PRODUCTS.firstOrNull { it.productId == productId }
                    ?: error("No product defined for productId=$productId")

    fun resolveExpiry(
            base: AppTimestamp,
            canonicalProductId: String?,
            fallbackDurationDays: Long? = null,
    ): AppTimestamp {
            val baseZdt = base.atZone(ZoneOffset.UTC)
            val expiry =
                    when (canonicalProductId) {
                            PRO_MONTHLY -> baseZdt.plusMonths(1).toInstant()
                            PRO_ANNUAL -> baseZdt.plusYears(1).toInstant()
                            else -> {
                                    val days = fallbackDurationDays
                                            ?: error("Cannot resolve expiry without canonicalProductId")
                                    base.plusSeconds(days * 86_400)
                            }
                    }
            return expiry
    }
}

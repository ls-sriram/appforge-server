package com.appforge.server.services.billing.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BillingCatalogTest {
    @Test
    fun `catalog exposes only pro monthly and yearly`() {
        assertEquals(2, BillingCatalog.PRODUCTS.size)

        val proSub = BillingCatalog.getById("pro_monthly")
        val proAnnual = BillingCatalog.getById("pro_annual")

        assertNotNull(proSub)
        assertNotNull(proAnnual)

        assertEquals("pro_monthly", proSub.productId)
        assertEquals("pro_annual", proAnnual.productId)

        assertEquals("pro", proSub.plan.planId)
        assertEquals("pro", proAnnual.plan.planId)
        assertEquals(30, proSub.plan.validityDays)
        assertEquals(365, proAnnual.plan.validityDays)
    }
}

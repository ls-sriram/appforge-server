package com.appforge.server.services.billing

import com.appforge.server.services.billing.catalog.BillingFeatureCatalog
import com.appforge.server.services.billing.catalog.BillingFeatureCatalog.Keys as FeatureKeys
import com.appforge.server.services.billing.models.Plan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BillingFeatureCatalogTest {
    @Test
    fun `trial plan exposes quotas`() {
        val features = BillingFeatureCatalog.defaultForPlan(Plan.TRIAL)

        assertEquals(5, features.size)
        assertEquals(3L, features[FeatureKeys.REVIEW_SUBMISSIONS]?.limit)
        assertEquals(5L, features[FeatureKeys.ENTITY_CREATIONS]?.limit)
        assertEquals(50L, features[FeatureKeys.API_REQUESTS]?.limit)
        assertEquals(3L, features[FeatureKeys.SHARED_LINKS]?.limit)

        assertTrue(features[FeatureKeys.ENTITY_CREATIONS]?.unlocked == true)
    }

    @Test
    fun `paid plans have 100 monthly quotas`() {
        val features = BillingFeatureCatalog.defaultForPlan(Plan.PRO)

        assertEquals(100L, features[FeatureKeys.REVIEW_SUBMISSIONS]?.limit)
        assertEquals(100L, features[FeatureKeys.ENTITY_CREATIONS]?.limit)
        assertEquals(1000L, features[FeatureKeys.API_REQUESTS]?.limit)
        assertEquals(100L, features[FeatureKeys.SHARED_LINKS]?.limit)
    }
}

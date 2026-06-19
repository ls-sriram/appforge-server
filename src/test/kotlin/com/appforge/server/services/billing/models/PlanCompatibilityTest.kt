package com.appforge.server.services.billing.models

import kotlin.test.Test
import kotlin.test.assertEquals

class PlanCompatibilityTest {
    @Test
    fun `only canonical plan wires are accepted`() {
        assertEquals(Plan.FREE, BillingEntitlementMapper.planFromRaw("free"))
        assertEquals(Plan.TRIAL, BillingEntitlementMapper.planFromRaw("trial"))
        assertEquals(Plan.PRO, BillingEntitlementMapper.planFromRaw("pro"))
    }
}

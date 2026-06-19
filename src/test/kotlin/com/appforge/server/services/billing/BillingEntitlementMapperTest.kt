package com.appforge.server.services.billing

import com.appforge.server.services.billing.catalog.BillingFeatureCatalog
import com.appforge.server.services.billing.models.BillingEntitlement
import com.appforge.server.services.billing.models.BillingEntitlementMapper
import com.appforge.server.services.billing.models.BillingFeature
import com.appforge.server.services.billing.models.BillingPaymentType
import com.appforge.server.services.billing.models.BillingSource
import com.appforge.server.services.billing.models.BillingStatus
import com.appforge.server.services.billing.models.Plan
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BillingEntitlementMapperTest {
    @Test
    fun `toDoc and fromDoc round trip`() {
        val features = mapOf(
                BillingFeatureCatalog.Keys.REVIEW_SUBMISSIONS to BillingFeature(limit = 5L),
        )

	        val entitlement = BillingEntitlement(
	            customerId = "cus_123",
	            plan = Plan.PRO,
	            status = BillingStatus.ACTIVE,
	            expiresAt = Instant.parse("2024-01-01T00:00:00Z"),
	            startedAt = Instant.parse("2023-11-01T00:00:00Z"),
	            externalCustomerId = "cus_123",
	            externalReferenceId = "pi_123",
	            billingType = BillingPaymentType.ONE_TIME,
	            lastPaymentAmountCents = 5000L,
	            lastPaymentCurrency = "usd",
	            source = BillingSource.DODO_PAYMENTS,
	            features = features,
	            createdAt = Instant.parse("2023-11-01T00:00:00Z"),
	            updatedAt = Instant.parse("2023-12-01T00:00:00Z"),
	        )

        val doc = BillingEntitlementMapper.toDoc(entitlement)
        val decoded = BillingEntitlementMapper.fromDoc("entitlement", doc)

        assertEquals(entitlement, decoded)
    }

    @Test
    fun `updateForOneOff writes wire values`() {
	        val update = BillingEntitlementMapper.updateForOneOff(
	            expiresAt = Instant.parse("2024-02-01T00:00:00Z"),
	            updatedAt = Instant.parse("2024-01-15T00:00:00Z"),
	            externalReferenceId = "pi_789",
	            lastPaymentAmountCents = 6000L,
	            lastPaymentCurrency = "usd",
	            source = BillingSource.DODO_PAYMENTS,
	        )

        assertEquals("active", update[BillingEntitlementMapper.Fields.Status])
        assertEquals(Instant.parse("2024-02-01T00:00:00Z").toEpochMilli(), update[BillingEntitlementMapper.Fields.ExpiresAtTimestamp])
	        assertEquals("pi_789", update[BillingEntitlementMapper.Fields.ExternalReferenceId])
	        assertEquals(6000L, update[BillingEntitlementMapper.Fields.LastPaymentAmountCents])
	        assertEquals("usd", update[BillingEntitlementMapper.Fields.LastPaymentCurrency])
	    }

    @Test
    fun `fromDoc fails when feature used is missing`() {
        val doc = mapOf(
            BillingEntitlementMapper.Fields.CustomerId to "cus_123",
            BillingEntitlementMapper.Fields.Plan to "pro",
            BillingEntitlementMapper.Fields.Status to "active",
            BillingEntitlementMapper.Fields.StartedAtTimestamp to Instant.parse("2023-11-01T00:00:00Z").toEpochMilli(),
            BillingEntitlementMapper.Fields.Features to mapOf(
                BillingFeatureCatalog.Keys.REVIEW_SUBMISSIONS to mapOf(
                    "unlocked" to true,
                ),
            ),
            BillingEntitlementMapper.Fields.Source to "dodo_payments",
            BillingEntitlementMapper.Fields.CreatedAtTimestamp to Instant.parse("2023-11-01T00:00:00Z").toEpochMilli(),
            BillingEntitlementMapper.Fields.UpdatedAtTimestamp to Instant.parse("2023-12-01T00:00:00Z").toEpochMilli(),
        )

        assertFailsWith<IllegalArgumentException> {
            BillingEntitlementMapper.fromDoc("entitlement", doc)
        }
    }

    @Test
    fun `fromDoc fails when feature unlocked is missing`() {
        val doc = mapOf(
            BillingEntitlementMapper.Fields.CustomerId to "cus_123",
            BillingEntitlementMapper.Fields.Plan to "pro",
            BillingEntitlementMapper.Fields.Status to "active",
            BillingEntitlementMapper.Fields.StartedAtTimestamp to Instant.parse("2023-11-01T00:00:00Z").toEpochMilli(),
            BillingEntitlementMapper.Fields.Features to mapOf(
                BillingFeatureCatalog.Keys.REVIEW_SUBMISSIONS to mapOf(
                    "used" to 0,
                ),
            ),
            BillingEntitlementMapper.Fields.Source to "dodo_payments",
            BillingEntitlementMapper.Fields.CreatedAtTimestamp to Instant.parse("2023-11-01T00:00:00Z").toEpochMilli(),
            BillingEntitlementMapper.Fields.UpdatedAtTimestamp to Instant.parse("2023-12-01T00:00:00Z").toEpochMilli(),
        )

        assertFailsWith<IllegalArgumentException> {
            BillingEntitlementMapper.fromDoc("entitlement", doc)
        }
    }
}

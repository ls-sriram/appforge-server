package com.appforge.server.integration

import com.appforge.server.config.options.BillingOptions
import com.appforge.server.services.billing.SignupEntitlementCoordinator
import com.appforge.server.services.billing.catalog.BillingFeatureCatalog
import com.appforge.server.services.billing.models.BillingAuditRecord
import com.appforge.server.services.billing.models.BillingEntitlement
import com.appforge.server.services.billing.models.BillingSource
import com.appforge.server.services.billing.models.BillingStatus
import com.appforge.server.services.billing.models.PaymentRecord
import com.appforge.server.services.billing.models.Plan
import com.appforge.server.services.billing.repository.BillingAuditRepositoryApi
import com.appforge.server.services.billing.repository.BillingRepositoryApi
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class DefaultEntitlementPlanTest {

    @Test
    fun `initializeDefaultEntitlement creates free active entitlement`() {
        val captured = mutableMapOf<String, BillingEntitlement>()
        val repository = object : BillingRepositoryApi {
            override suspend fun upsert(userId: String, entitlement: BillingEntitlement) {
                captured[userId] = entitlement
            }

            override suspend fun get(userId: String): BillingEntitlement? = captured[userId]

            override suspend fun getPayment(userId: String, recordId: String): PaymentRecord? = null

            override suspend fun recordPayment(userId: String, record: PaymentRecord, recordId: String?) = Unit
        }

        val auditRecords = mutableListOf<BillingAuditRecord>()
        val audit = object : BillingAuditRepositoryApi {
            override suspend fun record(record: BillingAuditRecord) {
                auditRecords += record
            }

            override suspend fun tryRecordOnce(record: BillingAuditRecord): Boolean {
                auditRecords += record
                return true
            }
        }

        val coordinator = SignupEntitlementCoordinator(
            repository = repository,
            auditRepository = audit,
            billingOptions = BillingOptions(
                trialDurationDays = 7,
                dodoProductIds = emptyMap(),
            ),
            clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)
        )

        val result = kotlinx.coroutines.runBlocking {
            coordinator.initializeDefaultEntitlement("uid-1")
        }

        assertEquals(Plan.FREE, result.plan)
        assertEquals(BillingStatus.ACTIVE, result.status)
        assertEquals(BillingSource.MANUAL, result.source)
        assertEquals(null, result.expiresAt)

        val stored = captured["uid-1"]
        assertNotNull(stored)
        assertEquals(Plan.FREE, stored.plan)
        assertEquals(BillingFeatureCatalog.defaultForPlan(Plan.FREE), stored.features)
        assertEquals(1, auditRecords.size)
    }
}

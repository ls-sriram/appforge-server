package com.appforge.server.services.billing

import com.appforge.server.config.options.BillingOptions
import com.appforge.server.services.billing.catalog.BillingFeatureCatalog
import com.appforge.server.services.billing.models.BillingAuditRecord
import com.appforge.server.services.billing.models.BillingEntitlement
import com.appforge.server.services.billing.models.BillingSource
import com.appforge.server.services.billing.models.BillingStatus
import com.appforge.server.services.billing.models.Plan
import com.appforge.server.services.billing.repository.BillingAuditRepositoryApi
import com.appforge.server.services.billing.repository.BillingRepositoryApi
import java.time.Clock
import com.appforge.server.infrastructure.time.*
import org.slf4j.LoggerFactory

class SignupEntitlementCoordinator(
        private val repository: BillingRepositoryApi,
        private val auditRepository: BillingAuditRepositoryApi,
        private val billingOptions: BillingOptions,
        private val clock: Clock = Clock.systemUTC()
) {
    private val logger = LoggerFactory.getLogger(SignupEntitlementCoordinator::class.java)

    suspend fun initializeDefaultEntitlement(userId: String): BillingEntitlement {
                repository.get(userId)?.let { return it }

                logger.info("Initializing default free entitlement for user {}", userId)
                val now = clock.nowTimestamp()
                val entitlement = BillingEntitlement(
                        customerId = userId,
                        plan = Plan.FREE,
                        status = BillingStatus.ACTIVE,
                        expiresAt = null,
                        startedAt = now,
                        source = BillingSource.MANUAL,
                        features = BillingFeatureCatalog.defaultForPlan(Plan.FREE),
                        createdAt = now,
                        updatedAt = now,
                )

                repository.upsert(userId, entitlement)
                auditRepository.record(
                        BillingAuditRecord(
                                payload =
                                        """{"event":"signup_init","plan":"free","userId":"$userId"}""",
                                timestamp = now,
                                webhookId = null,
                                source = "signup-init",
                        )
                )

                return entitlement
        }

    suspend fun initializeTrial(userId: String): BillingEntitlement {
        return initializeDefaultEntitlement(userId)
    }
}

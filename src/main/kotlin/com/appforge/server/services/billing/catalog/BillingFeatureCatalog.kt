package com.appforge.server.services.billing.catalog

import com.appforge.server.services.billing.models.BillingFeature
import com.appforge.server.services.billing.models.Plan

object BillingFeatureCatalog {
        object Keys {
                const val REVIEW_SUBMISSIONS = "review_submissions"
                const val ENTITY_CREATIONS = "entity_creations"
                const val API_REQUESTS = "api_requests"
                const val SHARED_LINKS = "shared_links"
                const val STORAGE_BYTES = "storage_bytes"
        }

        private fun feature(limit: Long?, unlocked: Boolean = true) =
                BillingFeature(limit = limit, used = 0L, unlocked = unlocked)

        fun defaultForPlan(plan: Plan): Map<String, BillingFeature> =
                when (plan) {
                        Plan.FREE -> mapOf(
                                Keys.REVIEW_SUBMISSIONS to feature(limit = 1L),
                                Keys.ENTITY_CREATIONS to feature(limit = 0L, unlocked = false),
                                Keys.API_REQUESTS to feature(limit = 0L, unlocked = false),
                                Keys.SHARED_LINKS to feature(limit = 1L),
                                Keys.STORAGE_BYTES to feature(limit = 0L, unlocked = false),
                        )
                        Plan.TRIAL -> mapOf(
                                Keys.REVIEW_SUBMISSIONS to feature(limit = 3L),
                                Keys.ENTITY_CREATIONS to feature(limit = 5L),
                                Keys.API_REQUESTS to feature(limit = 50L),
                                Keys.SHARED_LINKS to feature(limit = 3L),
                                Keys.STORAGE_BYTES to feature(limit = 10_485_760L), // 10MB
                        )
                        Plan.PRO -> mapOf(
                                Keys.REVIEW_SUBMISSIONS to feature(limit = 100L),
                                Keys.ENTITY_CREATIONS to feature(limit = 100L),
                                Keys.API_REQUESTS to feature(limit = 1000L),
                                Keys.SHARED_LINKS to feature(limit = 100L),
                                Keys.STORAGE_BYTES to feature(limit = 1_073_741_824L), // 1GB
                        )
                }
}

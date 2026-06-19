package com.appforge.server.db

/**
 * Centralized helpers for database collection paths.
 *
 * Supports multi-tenant path resolution: when an `appId` is provided,
 * all user data is scoped under `apps/{appId}/users/{userId}/...`.
 * Without `appId`, paths fall back to the flat `users/{userId}/...` layout.
 *
 * This enables a single AppForge instance to serve multiple frontend apps
 * with full data isolation.
 */
object DbPaths {

        object RootCollections {
                const val USERS = "users"
                const val ADMIN = "admin"
                const val SHARED = "shared"
                const val APPS = "apps"
        }

        /**
         * Build the root prefix for a given context.
         *
         * - Multi-tenant mode: `apps/{appId}/users/{userId}`
         * - Single-app mode: `users/{userId}`
         */
        fun userRoot(appId: String?, userId: String): String =
                if (appId != null) "${RootCollections.APPS}/$appId/${RootCollections.USERS}/$userId"
                else "${RootCollections.USERS}/$userId"

        private fun adminRoot() = RootCollections.ADMIN
        private fun sharedRoot() = "${RootCollections.SHARED}/global"

        object Collections {
                // ─── Per-user collections (appId-aware) ─────────────────────

                fun billing(userId: String, appId: String? = null) =
                        "${userRoot(appId, userId)}/billing"

                fun paymentHistory(userId: String, appId: String? = null) =
                        "${userRoot(appId, userId)}/billing-history"

                /** Generic entities — frontends define their own types. */
                fun entities(userId: String, appId: String? = null) =
                        "${userRoot(appId, userId)}/entities"

                fun entityVersions(userId: String, entityId: String, appId: String? = null) =
                        "${entities(userId, appId)}/$entityId/versions"

                /** Reviews (per-user, linked to an entity via fields). */
                fun reviews(userId: String, appId: String? = null) =
                        "${userRoot(appId, userId)}/reviews"

                /** Generic user profile. */
                fun profile(userId: String, appId: String? = null) =
                        "${userRoot(appId, userId)}/profile"

                /** Generic dashboard storage. */
                fun dashboard(userId: String, appId: String? = null) =
                        "${userRoot(appId, userId)}/dashboard"

                fun dashboardTasks(userId: String, appId: String? = null) =
                        "${dashboard(userId, appId)}/tasks"

                fun dashboardBilling(userId: String, appId: String? = null) =
                        "${dashboard(userId, appId)}/billing"

                // ─── Shared collections (global, not appId-scoped) ─────────

                fun shares() = "${sharedRoot()}/shares"
                fun profiles() = "${sharedRoot()}/profiles"

                // ─── Admin collections (global) ────────────────────────────

                fun billingAudit() = "${adminRoot()}/audit/billing"
                fun uploadsMetadata() = "${adminRoot()}/uploads/metadata"
                fun uploadsProcessed() = "${adminRoot()}/uploads/processed"
                fun earlyAccess() = "${adminRoot()}/access/early-access"
        }
}

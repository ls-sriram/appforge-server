package com.appforge.server.services

import com.appforge.server.extensions.ExtensionRegistry
import com.appforge.server.extensions.HookEngine
import com.appforge.server.infrastructure.Database
import com.appforge.server.providers.config.ConfigProvider
import com.appforge.server.providers.featureflag.FeatureFlagProvider
import com.appforge.server.providers.transaction.TransactionProvider
import com.appforge.server.infrastructure.RepositoryFactory
import com.appforge.server.services.uploads.SignedGetUrlIssuer
import com.appforge.server.services.uploads.SignedUploadUrlIssuer
import com.appforge.server.services.uploads.UploadMetadataRepository
import com.appforge.server.services.uploads.UploadOwnershipAuthorizer
import com.google.cloud.storage.Storage
import com.google.firebase.auth.FirebaseAuth
import com.appforge.server.clients.DodoPaymentsClient

/**
 * Core services shared across all Providers.
 *
 * All repositories use the Database abstraction (PostgreSQL JSONB).
 * No Firestore dependency.
 */
data class CoreServices(
    /** Typed environment/config access provider. */
    val configProvider: ConfigProvider,

    /** Firebase Auth client for token verification. */
    val firebaseAuth: FirebaseAuth,

    /** GCS Storage client for uploads/downloads. */
    val storage: Storage,

    /** Factory for creating typed repositories backed by SQL. */
    val repositoryFactory: RepositoryFactory,

    /** Primary database abstraction (PostgreSQL). */
    val database: Database,

    /** Standard transaction provider for SQL work. */
    val transactionProvider: TransactionProvider,

    /** Standard feature flag provider. */
    val featureFlagProvider: FeatureFlagProvider,

    /** Hook engine for platform event dispatch. */
    val hookEngine: HookEngine,

    /** Registry of registered platform extensions. */
    val extensionRegistry: ExtensionRegistry,

    // ─── Upload infrastructure ────────────────────────────────────────────

    /** Authorizer for upload ownership validation. */
    val uploadOwnershipAuthorizer: UploadOwnershipAuthorizer,

    /** Repository for upload metadata tracking. */
    val uploadMetadataRepository: UploadMetadataRepository,

    /** Issuer for signed PUT URLs (direct-to-GCS uploads). */
    val uploadSignedUrlIssuer: SignedUploadUrlIssuer,

    /** Issuer for signed GET URLs (download redirects). */
    val uploadAccessUrlIssuer: SignedGetUrlIssuer,

    // ─── External payment provider ────────────────────────────────────────

    /** Dodo Payments client for checkout and subscription management. */
    val dodoPaymentsClient: DodoPaymentsClient,
)

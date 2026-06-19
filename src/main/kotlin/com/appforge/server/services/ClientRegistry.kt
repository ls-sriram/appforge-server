package com.appforge.server.services

import com.appforge.server.clients.DodoPaymentsClient
import com.appforge.server.clients.FirebaseAdminClient
import com.appforge.server.clients.OpenAIClient
import com.appforge.server.config.AppEnv
import com.appforge.server.extensions.ExtensionRegistry
import com.appforge.server.extensions.HookEngine
import com.appforge.server.extensions.PlatformExtension
import com.appforge.server.extensions.PlatformServices
import com.appforge.server.infrastructure.Database
import com.appforge.server.infrastructure.DatabaseRepositoryFactory
import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.providers.config.AppConfigProvider
import com.appforge.server.providers.config.ConfigProvider
import com.appforge.server.providers.featureflag.FeatureFlagProvider
import com.appforge.server.providers.featureflag.RuntimeFeatureFlagProvider
import com.appforge.server.providers.transaction.SqlTransactionProvider
import com.appforge.server.providers.transaction.TransactionProvider
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.uploads.SignedGetUrlIssuer
import com.appforge.server.services.uploads.SignedUploadUrlIssuer
import com.appforge.server.services.uploads.UploadMetadataRepository
import com.appforge.server.services.uploads.UploadOwnershipAuthorizer
import com.appforge.server.services.uploads.UploadType
import com.appforge.server.services.documents.repository.SqlDocumentRepository
import com.appforge.server.services.recordings.repository.SqlRecordingRepository
import com.appforge.server.services.uploads.repository.EntityOwnershipResolverRegistry
import com.appforge.server.services.uploads.repository.DocumentOwnershipResolver
import com.appforge.server.services.uploads.repository.GcsSignedGetUrlIssuer
import com.appforge.server.services.uploads.repository.GcsSignedUploadUrlIssuer
import com.appforge.server.services.uploads.repository.RecordingOwnershipResolver
import com.appforge.server.services.uploads.repository.UploadMetadataRepositoryImpl
import com.appforge.server.services.uploads.repository.UploadOwnershipAuthorizerImpl
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import com.google.firebase.auth.FirebaseAuth
import org.slf4j.LoggerFactory
import java.time.Clock

/**
 * Central registry of all platform services and infrastructure.
 *
 * Database: PostgreSQL only. No Firestore, no caching decorator.
 */
object ClientRegistry {
    private var initialized = false
    private lateinit var env: AppEnv
    private lateinit var firebase: FirebaseAdminClient
    private lateinit var dodoPayments: DodoPaymentsClient
    private lateinit var openai: OpenAIClient
    private lateinit var storage: Storage
    private lateinit var database: Database
    private lateinit var repositoryFactoryInstance: DatabaseRepositoryFactory
    private lateinit var hookEngineInstance: HookEngine
    private val extensionRegistryInstance = ExtensionRegistry()

    private val logger = LoggerFactory.getLogger(ClientRegistry::class.java)

    fun initialize(env: AppEnv) {
        if (initialized) return
        this.env = env

        // External service clients
        dodoPayments = DodoPaymentsClient.getInstance(env)
        openai = OpenAIClient.getInstance(env)

        if (env.firebase.enabled) {
            firebase = FirebaseAdminClient.getInstance(env)
            storage = StorageOptions.newBuilder()
                .setCredentials(firebase.credentials)
                .setProjectId(env.firebase.firebaseProjectId)
                .build()
                .service
        } else {
            logger.info("Firebase bootstrap disabled for appId={}", env.runtime.appId)
        }

        // Hook engine
        hookEngineInstance = HookEngine()

        // Build SQL-only database
        database = buildDatabase(env)
        repositoryFactoryInstance = DatabaseRepositoryFactory(database)

        // Run Flyway migrations and initialize schema
        val exposedDb = database as? ExposedDatabase
        exposedDb?.runMigrations()

        logger.info(
            "AppForge database initialized: primary=sql, pool_size={}",
            env.database.sqlPoolSize,
        )

        initialized = true
    }

    private fun buildDatabase(env: AppEnv): Database {
        require(env.database.sqlUrl.isNotEmpty()) { "POSTGRES_HOST/POSTGRES_PORT/POSTGRES_DB are required" }
        require(env.database.sqlUser.isNotEmpty()) { "POSTGRES_USER is required" }
        require(env.database.sqlPassword.isNotEmpty()) { "POSTGRES_PASSWORD is required" }
        return ExposedDatabase.getInstance(
            env.database.sqlUrl,
            env.database.sqlUser,
            env.database.sqlPassword,
            env.database.sqlPoolSize,
        )
    }

    /**
     * Register a client extension. Must be called after `initialize()`.
     */
    fun registerExtension(extension: PlatformExtension) {
        require(initialized) { "ClientRegistry must be initialized before registering extensions" }
        extensionRegistryInstance.register(extension)

        // Create extension tables via Flyway migrations
        val ddlStatements = extension.defineTables()
        if (ddlStatements.isNotEmpty()) {
            // For extensions, we execute DDL directly (they're not part of main migrations)
            val exposedDb = database as? ExposedDatabase
            if (exposedDb != null) {
                exposedDb.getDataSource().connection.use { conn ->
                    conn.createStatement().use { stmt ->
                        for (ddl in ddlStatements) {
                            stmt.execute(ddl)
                        }
                    }
                }
                logger.info("Created ${ddlStatements.size} table(s) for extension '${extension.appId}'")
            }
        }

        // Register hooks
        val hooks = extension.defineHooks()
        for (hook in hooks) {
            hookEngineInstance.register(extension.appId, hook)
        }

        // Initialize extension
        if (env.firebase.enabled) {
            val services = PlatformServices(
                database = database,
                authService = AuthService.getInstance(firebase.auth, env),
                hookEngine = hookEngineInstance,
                extensions = extensionRegistryInstance.extensions,
            )
            extension.onInitialize(services)
        } else {
            logger.info(
                "Skipping extension onInitialize for appId={} because Firebase is disabled",
                extension.appId,
            )
        }

        logger.info("Extension registered: ${extension.appId}")
    }

    // ─── Accessors ───────────────────────────────────────────────────────

    val firebaseClient: FirebaseAdminClient
        get() {
            require(env.firebase.enabled) { "Firebase client requested while Firebase is disabled" }
            return firebase
        }
    val firebaseAuthOrNull: FirebaseAuth?
        get() = if (env.firebase.enabled) firebase.auth else null
    val dodoPaymentsClient: DodoPaymentsClient get() = dodoPayments
    val openAiClient: OpenAIClient get() = openai
    val databaseInstance: Database get() = database
    val repositoryFactory: DatabaseRepositoryFactory get() = repositoryFactoryInstance
    val hookEngine: HookEngine get() = hookEngineInstance
    val extensionRegistry: ExtensionRegistry get() = extensionRegistryInstance
    val storageClient: Storage
        get() {
            require(env.firebase.enabled) { "Storage client requested while Firebase is disabled" }
            return storage
        }
    val appEnv: AppEnv get() = env
    val configProvider: ConfigProvider by lazy { AppConfigProvider(env) }
    val featureFlagProvider: FeatureFlagProvider by lazy { RuntimeFeatureFlagProvider(env.runtime) }
    val transactionProvider: TransactionProvider by lazy {
        val relationalDb = database as? ExposedDatabase
            ?: error("Transaction provider requires SQL database")
        SqlTransactionProvider(relationalDb)
    }

    // ─── Upload dependencies (uses Database abstraction — SQL in prod) ──

    val uploadMetadataRepository: UploadMetadataRepository by lazy {
        val relationalDb = database as? ExposedDatabase
            ?: error("Upload metadata repository requires SQL database")
        UploadMetadataRepositoryImpl(relationalDb)
    }

    val uploadOwnershipAuthorizer: UploadOwnershipAuthorizer by lazy {
        val relationalDb = database as? ExposedDatabase
            ?: error("Upload ownership authorizer requires SQL database")
        val recordingRepository = SqlRecordingRepository(relationalDb)
        val documentRepository = SqlDocumentRepository(relationalDb)
        val ownershipRegistry = EntityOwnershipResolverRegistry(
            resolversByType = mapOf(
                UploadType.AUDIO to RecordingOwnershipResolver(recordingRepository),
                UploadType.DOCUMENT to DocumentOwnershipResolver(documentRepository),
            )
        )
        UploadOwnershipAuthorizerImpl(ownershipRegistry)
    }

    val uploadSignedUrlIssuer: SignedUploadUrlIssuer by lazy {
        require(env.firebase.enabled) { "Upload signing requested while Firebase is disabled" }
        GcsSignedUploadUrlIssuer(storage, env, Clock.systemUTC())
    }

    val uploadAccessUrlIssuer: SignedGetUrlIssuer by lazy {
        require(env.firebase.enabled) { "Upload access URL requested while Firebase is disabled" }
        GcsSignedGetUrlIssuer(storage)
    }
}

package com.appforge.server.services.billing

import com.appforge.server.config.AppEnv
import com.appforge.server.services.CoreServices
import com.appforge.server.services.auth.AuthService
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.providers.identity.IdentityProvider
import com.appforge.server.services.billing.repository.BillingAuditRepository
import com.appforge.server.services.billing.repository.BillingRepositoryApi
import com.appforge.server.services.billing.repository.SqlBillingRepository
import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.providers.time.UtcTimestampProvider
import com.appforge.server.services.billing.BillingServices
import com.appforge.server.services.auth.repository.SqlUserRepository
import com.appforge.server.services.auth.repository.UserRepositoryApi
import com.appforge.server.services.dodopayments.DodoPaymentsService
import com.appforge.server.services.email.EmailService
import com.appforge.server.services.email.ZeptoMailEmailService

class BillingProvider(
    private val core: CoreServices,
    private val env: AppEnv,
) : BillingServices {

    override val authService: AuthService by lazy {
        AuthService.getInstance(core.firebaseAuth, env)
    }
    override val requestIdentityProvider: IdentityProvider by lazy {
        ExternalIdentityProvider(authService)
    }

    private val emailService: EmailService by lazy {
        ZeptoMailEmailService(env.email)
    }

    /**
     * Billing repository — always SQL (PostgreSQL JSONB).
     */
    private val billingRepository: BillingRepositoryApi by lazy {
        SqlBillingRepository(core.database)
    }
    private val userRepository: UserRepositoryApi by lazy {
        SqlUserRepository(core.database)
    }

    private val billingAuditRepository by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Billing audit repository requires SQL database")
        BillingAuditRepository(relationalDb)
    }

    private val billingCoordinator: BillingCoordinator by lazy {
        val emailCoordinator =
            if (env.runtime.nodeEnv == "development") {
                null
            } else {
                BillingEmailCoordinator(
                    repository = billingRepository,
                    authService = authService,
                    emailService = emailService,
                )
            }
        BillingCoordinator(
            repository = billingRepository,
            emailCoordinator = emailCoordinator,
        )
    }

    private val dodoPaymentsService: DodoPaymentsService by lazy {
        DodoPaymentsService(
            dodoPaymentsClient = core.dodoPaymentsClient,
            oneOffBillingHandler = OneOffBillingService(coordinator = billingCoordinator),
            subscriptionBillingHandler = SubscriptionBillingService(coordinator = billingCoordinator),
            env = env,
            auditRepository = billingAuditRepository,
        )
    }

    override val billingUseCases: BillingUseCases by lazy {
        BillingUseCasesImpl(
            billingCoordinator = billingCoordinator,
            dodoPaymentsService = dodoPaymentsService,
            ensureUserExists = { uid ->
                if (userRepository.getUser(uid) == null) {
                    val email = authService.getUserEmail(uid)
                    if (!email.isNullOrBlank()) {
                        val now = UtcTimestampProvider.now()
                        userRepository.upsertUser(uid = uid, email = email, displayName = null, lastLoginAt = now)
                        userRepository.upsertProfile(uid = uid, email = email, displayName = null, lastSeenAt = now)
                    }
                }
            },
        )
    }
}

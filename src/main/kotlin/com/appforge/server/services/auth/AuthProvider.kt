package com.appforge.server.services.auth

import com.appforge.server.config.AppEnv
import com.appforge.server.config.options.RuntimeOptions
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.providers.identity.IdentityProvider
import com.appforge.server.providers.time.UtcTimestampProvider
import com.appforge.server.services.CoreServices
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.auth.AuthServices
import com.appforge.server.services.earlyaccess.repository.EarlyAccessRepository
import com.appforge.server.services.auth.repository.SqlUserRepository
import com.appforge.server.services.auth.repository.UserRepositoryApi
import com.appforge.server.services.billing.SignupEntitlementCoordinator
import com.appforge.server.services.billing.repository.BillingAuditRepository
import com.appforge.server.services.billing.repository.BillingRepositoryApi
import com.appforge.server.services.billing.repository.SqlBillingRepository
import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.services.onboarding.repository.OnboardingRepositoryApi
import com.appforge.server.services.onboarding.repository.SqlOnboardingRepository
import com.appforge.server.services.onboarding.OnboardingFlowUseCases
import com.appforge.server.services.onboarding.OnboardingFlowUseCasesImpl
import com.appforge.server.services.onboarding.OnboardingQaService
import com.appforge.server.services.onboarding.OnboardingQaServiceImpl
import com.appforge.server.services.earlyaccess.EarlyAccessService
import com.appforge.server.services.earlyaccess.EarlyAccessServiceImpl
import com.appforge.server.services.earlyaccess.EarlyAccessAppService
import com.appforge.server.services.earlyaccess.EarlyAccessAppServiceImpl
import com.appforge.server.services.login.LoginService
import com.appforge.server.services.login.LoginServiceImpl
import com.appforge.server.services.registration.RegistrationService
import com.appforge.server.services.registration.RegistrationServiceImpl
import com.appforge.server.services.useraccount.UserAccountService
import com.appforge.server.services.useraccount.UserAccountServiceImpl
import com.appforge.server.services.useraccount.AccountDeletionAuditRepository
import com.appforge.server.services.useraccount.AccountDeletionAuditRepositoryApi
import com.appforge.server.services.useraccount.AccountDeletionService
import com.appforge.server.services.userprofile.UserProfileService
import com.appforge.server.services.userprofile.UserProfileServiceImpl
import com.appforge.server.services.usage.UsageProvider
import com.appforge.server.services.usage.UsageMetricsService
import com.appforge.server.services.usage.UsageServices

class AuthProvider(
    private val core: CoreServices,
    private val env: AppEnv,
) : AuthServices {
    override val authService: AuthService by lazy {
        AuthService.getInstance(core.firebaseAuth, env)
    }
    override val requestIdentityProvider: IdentityProvider by lazy {
        ExternalIdentityProvider(authService)
    }

    private val billingRepository: BillingRepositoryApi by lazy {
        SqlBillingRepository(core.database)
    }

    private val earlyAccessRepository by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Early access repository requires SQL database")
        EarlyAccessRepository(sqlDatabase = relationalDb, timestampProvider = UtcTimestampProvider)
    }
    private val earlyAccessService: EarlyAccessService by lazy {
        EarlyAccessServiceImpl(
            repository = earlyAccessRepository,
            runtimeOptions = runtimeOptions,
        )
    }

    private val billingAuditRepository by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Billing audit repository requires SQL database")
        BillingAuditRepository(relationalDb)
    }

    private val userRepository: UserRepositoryApi by lazy {
        SqlUserRepository(core.database)
    }

    private val accountDeletionAuditRepository: AccountDeletionAuditRepositoryApi by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Account deletion audit repository requires SQL database")
        AccountDeletionAuditRepository(relationalDb)
    }

    private val onboardingRepository: OnboardingRepositoryApi by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Onboarding repository requires SQL database")
        SqlOnboardingRepository(relationalDb)
    }

    private val signupEntitlementCoordinator: SignupEntitlementCoordinator by lazy {
        SignupEntitlementCoordinator(
            repository = billingRepository,
            auditRepository = billingAuditRepository,
            billingOptions = env.billing,
        )
    }

    private val userLifecycleCoordinator: UserLifecycleCoordinator by lazy {
        UserLifecycleCoordinator(
            repository = userRepository,
            onboardingRepository = onboardingRepository,
            timestampProvider = UtcTimestampProvider,
        )
    }

    override val runtimeOptions: RuntimeOptions
        get() = env.runtime

    private val usageServices: UsageServices by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Usage provider requires SQL database")
        UsageProvider(
            sqlDatabase = relationalDb,
            billingRepository = billingRepository,
        )
    }

    override val loginService: LoginService by lazy {
        LoginServiceImpl(
            authService = authService,
            userLifecycleCoordinator = userLifecycleCoordinator,
        )
    }

    override val registrationService: RegistrationService by lazy {
        RegistrationServiceImpl(
            authService = authService,
            earlyAccessService = earlyAccessService,
            signupEntitlementCoordinator = signupEntitlementCoordinator,
            userLifecycleCoordinator = userLifecycleCoordinator,
        )
    }

    override val onboardingQaService: OnboardingQaService by lazy {
        OnboardingQaServiceImpl(
            userLifecycleCoordinator = userLifecycleCoordinator,
        )
    }

    override val userProfileService: UserProfileService by lazy {
        UserProfileServiceImpl(
            userRepository = userRepository,
            billingAccountService = usageServices.billingAccountService,
            entitlementService = usageServices.entitlementService,
        )
    }

    override val userAccountService: UserAccountService by lazy {
        UserAccountServiceImpl(
            userRepository = userRepository,
            accountDeletionService = AccountDeletionService(
                userRepository = userRepository,
                firebaseAuth = core.firebaseAuth,
                auditRepository = accountDeletionAuditRepository,
            ),
            timestampProvider = UtcTimestampProvider,
        )
    }

    override val usageMetricsService: UsageMetricsService by lazy {
        usageServices.usageMetricsService
    }

    override val earlyAccessAppService: EarlyAccessAppService by lazy {
        EarlyAccessAppServiceImpl(
            earlyAccessService = earlyAccessService,
        )
    }

    override val onboardingFlowUseCases: OnboardingFlowUseCases by lazy {
        OnboardingFlowUseCasesImpl(onboardingRepository = onboardingRepository)
    }
}

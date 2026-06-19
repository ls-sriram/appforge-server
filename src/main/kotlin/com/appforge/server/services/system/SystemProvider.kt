package com.appforge.server.services.system

import com.appforge.server.config.AppEnv
import com.appforge.server.config.options.RuntimeOptions
import com.appforge.server.services.CoreServices
import com.appforge.server.services.auth.EarlyAccessCoordinator
import com.appforge.server.services.earlyaccess.repository.EarlyAccessRepository
import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.services.system.SystemServices
import com.appforge.server.services.email.EmailService
import com.appforge.server.services.email.ZeptoMailEmailService

class SystemProvider(
    private val core: CoreServices,
    private val env: AppEnv,
) : SystemServices {

    private val emailService: EmailService by lazy {
        ZeptoMailEmailService(env.email)
    }

    private val earlyAccessRepository by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Early access repository requires SQL database")
        EarlyAccessRepository(relationalDb)
    }

    private val earlyAccessCoordinator: EarlyAccessCoordinator by lazy {
        EarlyAccessCoordinator(
            repository = earlyAccessRepository,
            emailService = emailService,
            runtimeOptions = env.runtime
        )
    }

    override val healthUseCases: HealthUseCases by lazy {
        HealthUseCasesImpl()
    }

    override val systemUseCases: SystemUseCases by lazy {
        SystemUseCasesImpl(
            earlyAccessCoordinator = earlyAccessCoordinator
        )
    }

    override val runtimeOptions: RuntimeOptions
        get() = env.runtime
}

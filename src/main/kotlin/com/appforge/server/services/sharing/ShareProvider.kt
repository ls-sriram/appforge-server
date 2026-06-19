package com.appforge.server.services.sharing

import com.appforge.server.config.AppEnv
import com.appforge.server.services.CoreServices
import com.appforge.server.services.auth.AuthService
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.providers.identity.IdentityProvider
import com.appforge.server.services.email.EmailService
import com.appforge.server.services.email.ZeptoMailEmailService
import com.appforge.server.services.sharing.repository.ShareRepository
import com.appforge.server.services.sharing.services.ShareService
import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.providers.time.UtcTimestampProvider

class ShareProvider(
    private val core: CoreServices,
    private val env: AppEnv,
) : ShareServices {
    override val authService: AuthService by lazy {
        AuthService.getInstance(core.firebaseAuth, env)
    }
    override val requestIdentityProvider: IdentityProvider by lazy {
        ExternalIdentityProvider(authService)
    }

    private val shareRepository by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Share repository requires SQL database")
        ShareRepository(relationalDb)
    }

    private val shareService: ShareService by lazy {
        ShareService(
            shareRepository = shareRepository,
            timestampProvider = UtcTimestampProvider,
        )
    }

    private val emailService: EmailService by lazy {
        ZeptoMailEmailService(env.email)
    }

    private val publicBaseUrl: String
        get() = env.runtime.publicBaseUrl

    override val shareUseCases: ShareUseCases by lazy {
        ShareUseCasesImpl(
            shareService = shareService,
            emailService = emailService,
            publicBaseUrl = publicBaseUrl
        )
    }
}

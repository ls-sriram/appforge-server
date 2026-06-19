package com.appforge.server.services.uploads

import com.appforge.server.config.AppEnv
import com.appforge.server.infrastructure.Repository
import com.appforge.server.services.CoreServices
import com.appforge.server.services.auth.AuthService
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.providers.identity.IdentityProvider
import com.appforge.server.services.uploads.UploadServices
import com.appforge.server.services.uploads.UploadInitService
import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.services.uploads.repository.ProcessedUploadEventsRepository

class UploadProvider(
    private val core: CoreServices,
    private val env: AppEnv,
) : UploadServices {
    override val uploadEventSharedSecret: String
        get() = env.uploads.uploadEventSharedSecret

    override val authService: AuthService by lazy {
        AuthService.getInstance(core.firebaseAuth, env)
    }
    override val requestIdentityProvider: IdentityProvider by lazy {
        ExternalIdentityProvider(authService)
    }

    private val processedUploadEventsRepository: Repository<Map<String, Any?>> by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Processed upload events repository requires SQL database")
        ProcessedUploadEventsRepository(relationalDb)
    }

    private val uploadInitService: UploadInitService by lazy {
        UploadInitService(
            env = env,
            authorizer = core.uploadOwnershipAuthorizer,
            metadataRepository = core.uploadMetadataRepository,
            processedEventsRepository = processedUploadEventsRepository,
            signedPutUrlIssuer = core.uploadSignedUrlIssuer,
            signedGetUrlIssuer = core.uploadAccessUrlIssuer,
        )
    }

    override val uploadUseCases: UploadUseCases by lazy {
        UploadUseCasesImpl(uploadInitService = uploadInitService)
    }
}

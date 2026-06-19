package com.appforge.server.services.recordings

import com.appforge.server.config.AppEnv
import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.providers.identity.IdentityProvider
import com.appforge.server.services.CoreServices
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.recordings.repository.SqlRecordingRepository

class RecordingProvider(
    private val core: CoreServices,
    private val env: AppEnv,
) : RecordingServices {
    override val authService: AuthService by lazy {
        AuthService.getInstance(core.firebaseAuth, env)
    }

    override val requestIdentityProvider: IdentityProvider by lazy {
        ExternalIdentityProvider(authService)
    }

    override val recordingService: RecordingService by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Recording repository requires SQL database")
        RecordingServiceImpl(
            repository = SqlRecordingRepository(relationalDb),
        )
    }
}

package com.appforge.server.services.collections

import com.appforge.server.config.AppEnv
import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.providers.identity.IdentityProvider
import com.appforge.server.services.CoreServices
import com.appforge.server.services.auth.AuthService

class CollectionProvider(
    private val core: CoreServices,
    private val env: AppEnv,
) : CollectionServices {

    override val authService: AuthService by lazy {
        AuthService.getInstance(core.firebaseAuth, env)
    }

    override val requestIdentityProvider: IdentityProvider by lazy {
        ExternalIdentityProvider(authService)
    }

    override val collectionService: CollectionService by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Collection repository requires SQL database")
        CollectionServiceImpl(SqlCollectionRepository(relationalDb))
    }
}

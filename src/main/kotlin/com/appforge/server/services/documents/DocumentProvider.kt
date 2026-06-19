package com.appforge.server.services.documents

import com.appforge.server.config.AppEnv
import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.providers.identity.IdentityProvider
import com.appforge.server.services.CoreServices
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.documents.repository.SqlDocumentRepository

class DocumentProvider(
    private val core: CoreServices,
    private val env: AppEnv,
) : DocumentServices {
    override val authService: AuthService by lazy {
        AuthService.getInstance(core.firebaseAuth, env)
    }

    override val requestIdentityProvider: IdentityProvider by lazy {
        ExternalIdentityProvider(authService)
    }

    override val documentService: DocumentService by lazy {
        val relationalDb = core.database as? ExposedDatabase
            ?: error("Document repository requires SQL database")
        DocumentServiceImpl(
            repository = SqlDocumentRepository(relationalDb),
            maxContentChars = env.runtime.documentMaxContentChars,
        )
    }
}

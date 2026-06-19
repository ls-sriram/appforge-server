package com.appforge.server.services.documents

import com.appforge.server.providers.identity.IdentityProvider
import com.appforge.server.services.auth.AuthService

interface DocumentServices {
    val authService: AuthService
    val requestIdentityProvider: IdentityProvider
    val documentService: DocumentService
}

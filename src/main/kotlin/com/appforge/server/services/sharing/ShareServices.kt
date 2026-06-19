package com.appforge.server.services.sharing

import com.appforge.server.services.auth.AuthService
import com.appforge.server.providers.identity.IdentityProvider

interface ShareServices {
    val authService: AuthService
    val requestIdentityProvider: IdentityProvider
    val shareUseCases: ShareUseCases
}

package com.appforge.server.services.reviews

import com.appforge.server.services.auth.AuthService
import com.appforge.server.providers.identity.IdentityProvider

interface ReviewServices {
    val authService: AuthService
    val requestIdentityProvider: IdentityProvider
    val reviewUseCases: ReviewUseCases
}

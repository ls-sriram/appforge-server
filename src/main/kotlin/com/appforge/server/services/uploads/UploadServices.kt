package com.appforge.server.services.uploads

import com.appforge.server.services.auth.AuthService
import com.appforge.server.providers.identity.IdentityProvider
import com.appforge.server.services.uploads.UploadInitService

interface UploadServices {
    val authService: AuthService
    val requestIdentityProvider: IdentityProvider
    val uploadEventSharedSecret: String
    val uploadUseCases: UploadUseCases
}

package com.appforge.server.services.collections

import com.appforge.server.providers.identity.IdentityProvider
import com.appforge.server.services.auth.AuthService

interface CollectionServices {
    val authService: AuthService
    val requestIdentityProvider: IdentityProvider
    val collectionService: CollectionService
}

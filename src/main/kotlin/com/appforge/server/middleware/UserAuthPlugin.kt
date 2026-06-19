package com.appforge.server.middleware

import com.appforge.server.api.ErrorResponse
import com.appforge.server.services.auth.AuthService
import com.appforge.server.providers.identity.IdentityProvider
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.call
import io.ktor.server.response.respond

class UserAuthConfig {
    lateinit var authService: AuthService
    lateinit var requestIdentityProvider: IdentityProvider
}

val UserAuthPlugin = createRouteScopedPlugin("UserAuthPlugin", ::UserAuthConfig) {
    val authService = pluginConfig.authService
    val requestIdentityProvider = pluginConfig.requestIdentityProvider

    onCall { call ->
        val ctx = call.resolveRequestContext(authService, requestIdentityProvider)
        if (ctx == null) {
            call.respond(io.ktor.http.HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
            throw UnauthorizedException()
        }
    }
}

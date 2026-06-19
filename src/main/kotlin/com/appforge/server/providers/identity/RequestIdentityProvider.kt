package com.appforge.server.providers.identity

import com.appforge.server.services.auth.AuthService
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header

data class IdentityContext(
    val externalUserId: String,
    val userId: String,
)

interface IdentityProvider {
    suspend fun resolve(call: ApplicationCall): IdentityContext?
}

class ExternalIdentityProvider(
    private val authService: AuthService,
) : IdentityProvider {
    override suspend fun resolve(call: ApplicationCall): IdentityContext? {
        val authHeader = call.request.header("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            val idToken = authHeader.removePrefix("Bearer ").trim()
            authService.verifyIdToken(idToken)?.uid?.let {
                return IdentityContext(externalUserId = it, userId = it)
            }
        }

        val sessionCookie = call.request.cookies[authService.sessionCookieName]
        if (sessionCookie != null) {
            authService.verifySessionCookie(sessionCookie)?.uid?.let {
                return IdentityContext(externalUserId = it, userId = it)
            }
        }

        val tokenQueryParam = call.request.queryParameters["token"]
        if (tokenQueryParam != null) {
            authService.verifyIdToken(tokenQueryParam)?.uid?.let {
                return IdentityContext(externalUserId = it, userId = it)
            }
        }

        return null
    }
}

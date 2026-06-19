package com.appforge.server.services.auth

import com.google.firebase.auth.FirebaseToken

data class AuthenticatedIdentity(
    val uid: String,
    val email: String,
    val name: String?,
)

class IdentityProviderUserResolver(
    private val authService: AuthService,
) {
    fun fromIdToken(idToken: String): AuthResponse<AuthenticatedIdentity> {
        val decoded = authService.verifyIdToken(idToken) ?: return AuthResponse.Unauthorized()
        return resolveIdentity(decoded, "Authenticated identity is missing email.")
    }

    fun fromSessionCookie(sessionCookie: String): AuthResponse<AuthenticatedIdentity> {
        val decoded = authService.verifySessionCookie(sessionCookie) ?: return AuthResponse.Unauthorized()
        return resolveIdentity(decoded, "Authenticated session is missing email.")
    }

    private fun resolveIdentity(
        token: FirebaseToken,
        missingEmailMessage: String,
    ): AuthResponse<AuthenticatedIdentity> {
        val email = token.email?.takeIf { it.isNotBlank() } ?: authService.getUserEmail(token.uid)?.takeIf { it.isNotBlank() }
        if (email == null) {
            return AuthResponse.Unauthorized(missingEmailMessage)
        }
        return AuthResponse.Ok(
            AuthenticatedIdentity(
                uid = token.uid,
                email = email,
                name = token.name,
            )
        )
    }
}

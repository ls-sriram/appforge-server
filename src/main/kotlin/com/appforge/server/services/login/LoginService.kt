package com.appforge.server.services.login

import com.appforge.server.api.PasswordResetLinkRequest
import com.appforge.server.api.PasswordResetLinkResponse
import com.appforge.server.api.SessionLoginRequest
import com.appforge.server.api.SessionLoginResponse
import com.appforge.server.api.SessionLogoutResponse
import com.appforge.server.api.SessionMeResponse
import com.appforge.server.services.auth.AuthResponse
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.auth.IdentityProviderUserResolver
import com.appforge.server.services.auth.SessionCookieSpec
import com.appforge.server.services.auth.UserLifecycleCoordinator

interface LoginService {
    val sessionCookieName: String
    suspend fun sessionMe(sessionCookie: String?): AuthResponse<SessionMeResponse>
    suspend fun sessionLogin(request: SessionLoginRequest): AuthResponse<SessionLoginResponse>
    suspend fun sessionLogout(sessionCookie: String?): AuthResponse<SessionLogoutResponse>
    suspend fun sendPasswordResetLink(request: PasswordResetLinkRequest): AuthResponse<PasswordResetLinkResponse>
}

class LoginServiceImpl(
    private val authService: AuthService,
    private val userLifecycleCoordinator: UserLifecycleCoordinator,
    private val identityResolver: IdentityProviderUserResolver = IdentityProviderUserResolver(authService),
) : LoginService {
    override val sessionCookieName: String
        get() = authService.sessionCookieName

    override suspend fun sessionMe(sessionCookie: String?): AuthResponse<SessionMeResponse> {
        if (sessionCookie.isNullOrBlank()) return AuthResponse.Unauthorized()
        val identity = when (val resolved = identityResolver.fromSessionCookie(sessionCookie)) {
            is AuthResponse.Ok -> resolved.data
            is AuthResponse.Unauthorized -> return resolved
            is AuthResponse.Forbidden -> return AuthResponse.Unauthorized()
            is AuthResponse.BadRequest -> return AuthResponse.Unauthorized()
        }
        return AuthResponse.Ok(
            SessionMeResponse(
                uid = identity.uid,
                email = identity.email,
                name = identity.name,
                onboardingCompleted = userLifecycleCoordinator.hasCompletedOnboarding(identity.uid),
            )
        )
    }

    override suspend fun sessionLogin(request: SessionLoginRequest): AuthResponse<SessionLoginResponse> {
        val identity = when (val resolved = identityResolver.fromIdToken(request.idToken)) {
            is AuthResponse.Ok -> resolved.data
            is AuthResponse.Unauthorized -> return resolved
            is AuthResponse.Forbidden -> return AuthResponse.Unauthorized()
            is AuthResponse.BadRequest -> return AuthResponse.Unauthorized()
        }
        userLifecycleCoordinator.ensureUserCreated(
            uid = identity.uid,
            email = identity.email,
            displayName = identity.name,
        )
        val sessionCookie = authService.createSessionCookie(request.idToken)
        return AuthResponse.Ok(
            SessionLoginResponse(success = true, uid = identity.uid),
            cookie = SessionCookieSpec(
                name = authService.sessionCookieName,
                value = sessionCookie,
                maxAge = authService.sessionExpirySeconds(),
                secure = authService.cookieSecure,
                sameSite = authService.cookieSameSite,
            ),
        )
    }

    override suspend fun sessionLogout(sessionCookie: String?): AuthResponse<SessionLogoutResponse> {
        val uid = if (!sessionCookie.isNullOrBlank()) authService.verifySessionCookie(sessionCookie)?.uid else null
        if (sessionCookie != null) {
            authService.revokeSession(sessionCookie)
        }
        return AuthResponse.Ok(
            SessionLogoutResponse(success = true, uid = uid),
            cookie = SessionCookieSpec(
                name = authService.sessionCookieName,
                value = "",
                maxAge = 0,
                secure = authService.cookieSecure,
                sameSite = authService.cookieSameSite,
            ),
        )
    }

    override suspend fun sendPasswordResetLink(request: PasswordResetLinkRequest): AuthResponse<PasswordResetLinkResponse> {
        val email = request.email.trim()
        if (email.isBlank()) {
            return AuthResponse.BadRequest("Email is required.")
        }
        authService.generatePasswordResetLink(email)
        return AuthResponse.Ok(PasswordResetLinkResponse(success = true))
    }
}

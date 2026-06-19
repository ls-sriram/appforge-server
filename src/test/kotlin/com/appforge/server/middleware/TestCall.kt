package com.appforge.server.middleware

import com.appforge.server.services.auth.AuthService
import org.slf4j.MDC

/**
 * Minimal test double for auth resolution without depending on Ktor's ApplicationCall.
 * Tests verify the auth logic and RequestContext construction, not Ktor plumbing.
 */
class TestCall(
    val authHeader: String?,
    val requestCookies: Map<String, String>,
    val requestQueryParams: Map<String, String>,
    val requestHeaders: Map<String, String>,
) {
    var resolvedContext: RequestContext? = null
}

/**
 * Resolves auth and builds RequestContext from this test call.
 * Mirrors the logic in AuthMiddleware.resolveRequestContext() but without Ktor dependencies.
 */
suspend fun TestCall.resolveRequestContext(authService: AuthService): RequestContext? {
    MDC.clear()

    val uid = resolveUserId(authService) ?: return null

    val appId = requestHeaders["X-App-Id"]?.takeIf { it.isNotBlank() }
        ?: return null  // appId is mandatory
    val teamId = requestHeaders["X-Team-Id"]?.takeIf { it.isNotBlank() }

    val roles = mutableSetOf(PlatformRole.OWNER)
    val adminSecret = requestHeaders["X-Internal-Secret"]
    if (adminSecret != null && adminSecret == authService.internalSecret) {
        roles.add(PlatformRole.ADMIN)
    }

    val ctx = RequestContext(
        userId = uid,
        appId = appId,
        teamId = teamId,
        roles = roles,
    )

    resolvedContext = ctx
    MDC.put("userId", uid)
    MDC.put("appId", appId)
    if (teamId != null) MDC.put("teamId", teamId)

    return ctx
}

/**
 * Resolves just the user ID (legacy helper).
 */
suspend fun TestCall.resolveUserId(authService: AuthService): String? {
    // Bearer token
    val header = authHeader
    if (header != null && header.startsWith("Bearer ")) {
        val idToken = header.removePrefix("Bearer ").trim()
        return authService.verifyIdToken(idToken)?.uid
    }

    // Session cookie
    val sessionCookie = requestCookies[authService.sessionCookieName]
    if (sessionCookie != null) {
        return authService.verifySessionCookie(sessionCookie)?.uid
    }

    // Query parameter
    val tokenQueryParam = requestQueryParams["token"]
    if (tokenQueryParam != null) {
        return authService.verifyIdToken(tokenQueryParam)?.uid
    }

    return null
}

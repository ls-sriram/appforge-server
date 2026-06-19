package com.appforge.server.middleware

import com.appforge.server.services.auth.AuthService
import com.appforge.server.providers.identity.IdentityProvider
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import org.slf4j.MDC

/**
 * Resolves a full [RequestContext] from the incoming request.
 *
 * Tries three auth mechanisms in order:
 * 1. Bearer token — `Authorization: Bearer <firebase-id-token>`
 * 2. Session cookie — HTTP-only cookie named by [AuthService.sessionCookieName]
 * 3. Query parameter — `?token=<firebase-id-token>`
 *
 * Additionally extracts the app context from:
 * - `X-App-Id` header (explicit app identification)
 * - `X-Team-Id` header (optional team context)
 *
 * Returns `null` if no valid auth is found.
 */
suspend fun ApplicationCall.resolveRequestContext(
    authService: AuthService,
    identityProvider: IdentityProvider,
): RequestContext? {
    // Step 1: Resolve user identity
    val uid = resolveUserId(identityProvider) ?: return null

    // Step 2: Resolve app/tenant context — APP IS MANDATORY
    val appId = request.header("X-App-Id")
        ?.takeIf { it.isNotBlank() }
        ?: return null  // reject: no app identification

    val teamId = request.header("X-Team-Id")
        ?.takeIf { it.isNotBlank() }

    // Step 3: Build context
    val roles = mutableSetOf(PlatformRole.OWNER)
    val adminSecret = request.header("X-Internal-Secret")
    if (adminSecret != null && adminSecret == authService.internalSecret) {
        roles.add(PlatformRole.ADMIN)
    }

    val ctx = RequestContext(
        userId = uid,
        appId = appId,
        teamId = teamId,
        roles = roles,
    )

    // Step 4: Populate MDC for logging
    attributes.put(RequestContextKey, ctx)
    MDC.put("userId", uid)
    MDC.put("appId", appId)
    if (teamId != null) MDC.put("teamId", teamId)

    return ctx
}

/**
 * Legacy helper: resolves just the user ID string.
 * Kept for backward compatibility with code that hasn't migrated to RequestContext yet.
 */
suspend fun ApplicationCall.resolveUserId(identityProvider: IdentityProvider): String? {
    return identityProvider.resolve(this)?.userId
}

/** Backward-compat alias for the old attribute key. Points to RequestContext, not String. */
@Deprecated("Use RequestContextKey and extract userId from the context", ReplaceWith("RequestContextKey"))
val UserIdKey = RequestContextKey

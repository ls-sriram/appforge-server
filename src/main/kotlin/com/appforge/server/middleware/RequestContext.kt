package com.appforge.server.middleware

import io.ktor.util.AttributeKey

/**
 * Rich request context carried through every authenticated request.
 *
 * Multi-app is mandatory: `appId` is always present.
 * Users and data are scoped to the app — there is no cross-app visibility.
 */
data class RequestContext(
    /** The authenticated user's Firebase UID */
    val userId: String,

    /** Which application initiated this request — always present. */
    val appId: String,

    /** Optional team/organization context. Null = operating as individual. */
    val teamId: String? = null,

    /** Roles this user has in the current context (app + team scope). */
    val roles: Set<PlatformRole> = setOf(PlatformRole.OWNER),
) {
    val isAdmin: Boolean get() = roles.contains(PlatformRole.ADMIN)
}

/** Roles a user can have within a request context. */
enum class PlatformRole {
    /** Default role — user owns the resource or is the requestor */
    OWNER,

    /** Platform-level admin — can access system routes and cross-user data */
    ADMIN,
}

/** Ktor attribute key for RequestContext. Replaces [UserIdKey]. */
val RequestContextKey = AttributeKey<RequestContext>("requestContext")

package com.appforge.server.routing

import com.appforge.server.api.EarlyAccessCheckRequest
import com.appforge.server.api.EarlyAccessJoinRequest
import com.appforge.server.api.EarlyAccessStatusResponse
import com.appforge.server.api.ErrorResponse
import com.appforge.server.api.PasswordResetLinkRequest
import com.appforge.server.api.SessionLoginRequest
import com.appforge.server.api.OnboardingSubmitRequest
import com.appforge.server.api.SignupInitRequest
import com.appforge.server.api.UpdateUserProfileRequest
import com.appforge.server.api.UserUsageBucketResponse
import com.appforge.server.api.UserUsageSeriesResponse
import com.appforge.server.api.UserUsageSummaryResponse
import com.appforge.server.middleware.AnalyticsMiddleware.setAnalyticsUserId
import com.appforge.server.middleware.RequestContext
import com.appforge.server.middleware.resolveUserId
import com.appforge.server.infrastructure.time.instantToProtoTimestamp
import com.appforge.server.services.auth.AuthResponse
import com.appforge.server.services.auth.AuthServices
import com.appforge.server.services.auth.SessionCookieSpec
import com.appforge.server.services.login.LoginService
import com.appforge.server.services.registration.RegistrationService
import com.appforge.server.services.onboarding.OnboardingQaService
import com.appforge.server.services.useraccount.UserAccountService
import com.appforge.server.services.userprofile.UserProfileService
import com.appforge.server.services.earlyaccess.EarlyAccessAppService
import com.appforge.server.services.usage.UsageGranularity
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.log
import io.ktor.server.request.header
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import java.time.Instant

fun Route.authRoutes(services: AuthServices) {
    val loginService = services.loginService
    val registrationService = services.registrationService
    val onboardingQaService = services.onboardingQaService
    val userProfileService = services.userProfileService
    val userAccountService = services.userAccountService
    val usageMetricsService = services.usageMetricsService
    val earlyAccessAppService = services.earlyAccessAppService
    val runtimeOptions = services.runtimeOptions

    route("/api/v1/session") {
        post("/early-access/check") {
            val request = call.receive<EarlyAccessCheckRequest>()
            call.respondAuth(earlyAccessAppService.check(request))
        }

        post("/early-access/join") {
            val request = call.receive<EarlyAccessJoinRequest>()
            call.respondAuth(earlyAccessAppService.join(request))
        }

        get("/early-access/status") {
            call.respond(EarlyAccessStatusResponse(enabled = runtimeOptions.earlyAccessEnabled))
        }

        get("/me") {
            call.application.log.info("Received GET /session/me")
            val appId = call.request.header("X-App-Id")
            if (appId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("X-App-Id header is required"))
                return@get
            }
            val sessionCookie = call.request.cookies[loginService.sessionCookieName]
            val result = loginService.sessionMe(sessionCookie)
            if (result is AuthResponse.Ok) {
                call.setAnalyticsUserId(result.data.uid)
            }
            call.respondAuth(result)
        }

        post("/login") {
            call.application.log.info("Received POST /session/login")
            val appId = call.request.header("X-App-Id")
            if (appId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("X-App-Id header is required"))
                return@post
            }
            val request = call.receive<SessionLoginRequest>()
            val result = loginService.sessionLogin(request)
            if (result is AuthResponse.Ok) {
                call.applyCookie(result.cookie)
                result.data.uid?.let { uid ->
                    call.setAnalyticsUserId(uid)
                }
            }
            call.respondAuth(result)
        }

        post("/logout") {
            call.application.log.info("Received POST /session/logout")
            val appId = call.request.header("X-App-Id")
            if (appId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("X-App-Id header is required"))
                return@post
            }
            val sessionCookie = call.request.cookies[loginService.sessionCookieName]
            val result = loginService.sessionLogout(sessionCookie)
            if (result is AuthResponse.Ok) {
                call.applyCookie(result.cookie)
                result.data.uid?.let { uid ->
                    call.setAnalyticsUserId(uid)
                }
            }
            call.respondAuth(result)
        }

        post("/password/reset-link") {
            val appId = call.request.header("X-App-Id")
            if (appId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("X-App-Id header is required"))
                return@post
            }
            val request = call.receive<PasswordResetLinkRequest>()
            call.respondAuth(loginService.sendPasswordResetLink(request))
        }
    }

    route("/api/v1/signup") {
        post("/init") {
            call.application.log.info("Received POST /signup/init")
            val appId = call.request.header("X-App-Id")
            if (appId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("X-App-Id header is required"))
                return@post
            }
            val request = call.receive<SignupInitRequest>()
            val result = registrationService.signupInit(request)
            if (result is AuthResponse.Ok) {
                result.data.uid?.let { uid ->
                    call.setAnalyticsUserId(uid)
                }
            }
            call.respondAuth(result)
        }

    }

    route("/api/v1/onboarding") {
        get("/flow") {
            val appId = call.request.header("X-App-Id")
            if (appId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("X-App-Id header is required"))
                return@get
            }
            call.respond(services.onboardingFlowUseCases.getActiveFlow())
        }

        post("/submit") {
            val appId = call.request.header("X-App-Id")
            if (appId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("X-App-Id header is required"))
                return@post
            }
            val userId = call.resolveUserId(services.requestIdentityProvider)
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
                return@post
            }
            val request = call.receive<OnboardingSubmitRequest>()
            withRouteSqlUserContext(RequestContext(userId = userId, appId = appId)) {
                call.respondAuth(onboardingQaService.submitOnboarding(userId, request))
            }
        }
    }

    route("/api/v1/users") {
        get("/me") {
            val userId = call.resolveUserId(services.requestIdentityProvider)
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
                return@get
            }
            val appId = call.request.header("X-App-Id")
            if (appId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("X-App-Id header is required"))
                return@get
            }
            withRouteSqlUserContext(RequestContext(userId = userId, appId = appId)) {
                call.respond(userProfileService.userProfile(userId))
            }
        }

        put("/me") {
            val userId = call.resolveUserId(services.requestIdentityProvider)
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
                return@put
            }
            val appId = call.request.header("X-App-Id")
            if (appId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("X-App-Id header is required"))
                return@put
            }
            val request = call.receive<UpdateUserProfileRequest>()
            withRouteSqlUserContext(RequestContext(userId = userId, appId = appId)) {
                call.respondAuth(userAccountService.updateUserProfile(userId, request))
            }
        }

        delete("/me") {
            val userId = call.resolveUserId(services.requestIdentityProvider)
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
                return@delete
            }
            val appId = call.request.header("X-App-Id")
            if (appId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("X-App-Id header is required"))
                return@delete
            }
            withRouteSqlUserContext(RequestContext(userId = userId, appId = appId)) {
                call.respondAuth(userAccountService.deleteUserAccount(userId))
            }
        }

        get("/me/usage") {
            val userId = call.resolveUserId(services.requestIdentityProvider)
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Unauthorized"))
                return@get
            }
            val appId = call.request.header("X-App-Id")
            if (appId.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("X-App-Id header is required"))
                return@get
            }
            val granularity = UsageGranularity.fromWire(call.request.queryParameters["granularity"])
                ?: UsageGranularity.DAY
            val from = parseInstantQuery(call.request.queryParameters["from"])
            if (call.request.queryParameters["from"] != null && from == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid from timestamp. Use ISO-8601 UTC."))
                return@get
            }
            val to = parseInstantQuery(call.request.queryParameters["to"])
            if (call.request.queryParameters["to"] != null && to == null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid to timestamp. Use ISO-8601 UTC."))
                return@get
            }
            withRouteSqlUserContext(RequestContext(userId = userId, appId = appId)) {
                val summary = usageMetricsService.usageSummary(
                    userId = userId,
                    granularity = granularity,
                    from = from,
                    to = to,
                )
                call.respond(
                    UserUsageSummaryResponse(
                        granularity = summary.granularity.wire,
                        from = instantToProtoTimestamp(summary.from),
                        to = instantToProtoTimestamp(summary.to),
                        series = summary.series.map { series ->
                            UserUsageSeriesResponse(
                                metric = series.metric.wire,
                                total = series.total,
                                buckets = series.buckets.map { bucket ->
                                    UserUsageBucketResponse(
                                        windowStart = instantToProtoTimestamp(bucket.windowStart)
                                            ?: error("Usage bucket windowStart cannot be null"),
                                        count = bucket.count,
                                    )
                                },
                            )
                        },
                    )
                )
            }
        }
    }
}

private fun parseInstantQuery(value: String?): Instant? {
    if (value == null) return null
    return try {
        Instant.parse(value)
    } catch (_: Exception) {
        null
    }
}

private fun ApplicationCall.applyCookie(cookie: SessionCookieSpec?) {
    if (cookie == null) return
    response.cookies.append(
        Cookie(
            name = cookie.name,
            value = cookie.value,
            httpOnly = true,
            secure = cookie.secure,
            path = cookie.path,
            maxAge = cookie.maxAge,
            extensions = mapOf("SameSite" to cookie.sameSite),
        )
    )
}

private suspend fun <T> ApplicationCall.respondAuth(result: AuthResponse<T>) {
    when (result) {
        is AuthResponse.Ok -> respond(result.data as Any)
        is AuthResponse.Unauthorized -> respond(HttpStatusCode.Unauthorized, ErrorResponse(result.message))
        is AuthResponse.Forbidden -> respond(HttpStatusCode.Forbidden, ErrorResponse(result.message))
        is AuthResponse.BadRequest -> respond(HttpStatusCode.BadRequest, ErrorResponse(result.message))
    }
}

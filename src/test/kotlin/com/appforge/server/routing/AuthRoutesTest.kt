package com.appforge.server.routing

import com.appforge.server.api.EarlyAccessCheckRequest
import com.appforge.server.api.EarlyAccessCheckResponse
import com.appforge.server.api.EarlyAccessJoinRequest
import com.appforge.server.api.EarlyAccessJoinResponse
import com.appforge.server.api.EarlyAccessStatusResponse
import com.appforge.server.api.DeleteUserAccountResponse
import com.appforge.server.api.OnboardingFlowResponse
import com.appforge.server.api.OnboardingSubmitRequest
import com.appforge.server.api.OnboardingSubmitResponse
import com.appforge.server.api.PasswordResetLinkRequest
import com.appforge.server.api.PasswordResetLinkResponse
import com.appforge.server.api.ProtoTimestamp
import com.appforge.server.api.SessionLoginRequest
import com.appforge.server.api.SessionLoginResponse
import com.appforge.server.api.SignupInitRequest
import com.appforge.server.api.SignupInitResponse
import com.appforge.server.api.UpdateUserProfileRequest
import com.appforge.server.api.UpdateUserProfileResponse
import com.appforge.server.api.UserProfileResponse
import com.appforge.server.config.options.RuntimeOptions
import com.appforge.server.middleware.configureErrorHandling
import com.appforge.server.services.auth.AuthResponse
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.auth.AuthServices
import com.appforge.server.services.onboarding.OnboardingQaService
import com.appforge.server.services.registration.RegistrationService
import com.appforge.server.services.login.LoginService
import com.appforge.server.services.useraccount.UserAccountService
import com.appforge.server.services.userprofile.UserProfileService
import com.appforge.server.services.earlyaccess.EarlyAccessAppService
import com.appforge.server.services.usage.UsageBucketCount
import com.appforge.server.services.usage.UsageGranularity
import com.appforge.server.services.usage.UsageMetricKey
import com.appforge.server.services.usage.UsageMetricSeries
import com.appforge.server.services.usage.UsageMetricsService
import com.appforge.server.services.usage.UsageSummary
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.services.auth.SessionCookieSpec
import com.google.firebase.auth.FirebaseToken
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.delete
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `early access check returns ok`() = testApplication {
        val loginService = mockk<LoginService>(relaxed = true)
        val earlyAccessAppService = mockk<EarlyAccessAppService>()
        coEvery { earlyAccessAppService.check(any()) } returns AuthResponse.Ok(
            EarlyAccessCheckResponse(hasAccess = true)
        )

        val authServices = buildAuthServices(loginService, earlyAccessAppService = earlyAccessAppService)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { authRoutes(authServices) }
        }

        val response = client.post("/api/v1/session/early-access/check") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(EarlyAccessCheckRequest(email = "test@example.com")))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.decodeFromString<EarlyAccessCheckResponse>(response.bodyAsText())
        assertEquals(true, payload.hasAccess)
        coVerify(exactly = 1) { earlyAccessAppService.check(any()) }
    }

    @Test
    fun `early access join rejects when disabled`() = testApplication {
        val loginService = mockk<LoginService>(relaxed = true)
        val earlyAccessAppService = mockk<EarlyAccessAppService>()
        coEvery { earlyAccessAppService.join(any()) } returns AuthResponse.Forbidden("Early access is disabled.")

        val authServices = buildAuthServices(loginService, earlyAccessAppService = earlyAccessAppService)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { authRoutes(authServices) }
        }

        val response = client.post("/api/v1/session/early-access/join") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(EarlyAccessJoinRequest(email = "test@example.com")))
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `early access status returns runtime flag`() = testApplication {
        val loginService = mockk<LoginService>(relaxed = true)
        val authServices = buildAuthServices(loginService, earlyAccessEnabled = true)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { authRoutes(authServices) }
        }

        val response = client.get("/api/v1/session/early-access/status")
        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.decodeFromString<EarlyAccessStatusResponse>(response.bodyAsText())
        assertTrue(payload.enabled)
    }

    @Test
    fun `login rejects invalid token`() = testApplication {
        val loginService = mockk<LoginService>()
        coEvery { loginService.sessionLogin(any()) } returns AuthResponse.Unauthorized()

        val authServices = buildAuthServices(loginService)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { authRoutes(authServices) }
        }

        val response = client.post("/api/v1/session/login") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header("X-App-Id", "test-app")
            setBody(json.encodeToString(SessionLoginRequest(idToken = "bad-token")))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `login sets cookie when ok`() = testApplication {
        val loginService = mockk<LoginService>()
        val cookie = SessionCookieSpec(
            name = "session",
            value = "cookie-value",
            maxAge = 3600,
            secure = false,
            sameSite = "Lax",
        )
        coEvery { loginService.sessionLogin(any()) } returns AuthResponse.Ok(
            SessionLoginResponse(success = true),
            cookie = cookie
        )

        val authServices = buildAuthServices(loginService)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { authRoutes(authServices) }
        }

        val response = client.post("/api/v1/session/login") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header("X-App-Id", "test-app")
            setBody(json.encodeToString(SessionLoginRequest(idToken = "good-token")))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val setCookie = response.headers[HttpHeaders.SetCookie]
        assertTrue(setCookie?.contains("session=") == true)
    }

    @Test
    fun `signup init returns ok`() = testApplication {
        val loginService = mockk<LoginService>(relaxed = true)
        val registrationService = mockk<RegistrationService>()
        coEvery { registrationService.signupInit(any()) } returns AuthResponse.Ok(
            SignupInitResponse(success = true, uid = "user-1")
        )

        val authServices = buildAuthServices(loginService, registrationService = registrationService)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { authRoutes(authServices) }
        }

        val response = client.post("/api/v1/signup/init") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header("X-App-Id", "test-app")
            setBody(json.encodeToString(SignupInitRequest(idToken = "token")))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.decodeFromString<SignupInitResponse>(response.bodyAsText())
        assertTrue(payload.success)
        coVerify(exactly = 1) { registrationService.signupInit(any()) }
    }

    @Test
    fun `onboarding flow returns ok with app header`() = testApplication {
        val loginService = mockk<LoginService>(relaxed = true)
        val onboardingFlow = mockk<com.appforge.server.services.onboarding.OnboardingFlowUseCases>()
        coEvery { onboardingFlow.getActiveFlow() } returns OnboardingFlowResponse(
            id = "flow-1",
            version = 1,
            name = "Flow",
            steps = emptyList(),
        )

        val authServices = buildAuthServices(loginService, onboardingFlowUseCases = onboardingFlow)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { authRoutes(authServices) }
        }

        val response = client.get("/api/v1/onboarding/flow") {
            header("X-App-Id", "test-app")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `onboarding flow rejects without app header`() = testApplication {
        val loginService = mockk<LoginService>(relaxed = true)
        val authServices = buildAuthServices(loginService)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { authRoutes(authServices) }
        }

        val response = client.get("/api/v1/onboarding/flow")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `onboarding submit rejects unauthorized`() = testApplication {
        val loginService = mockk<LoginService>(relaxed = true)
        val authServices = buildAuthServices(loginService)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { authRoutes(authServices) }
        }

        val response = client.post("/api/v1/onboarding/submit") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header("X-App-Id", "test-app")
            setBody(json.encodeToString(OnboardingSubmitRequest()))
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `onboarding submit returns ok when authorized`() = testApplication {
        val loginService = mockk<LoginService>(relaxed = true)
        val onboardingQaService = mockk<OnboardingQaService>()
        val authService = mockk<AuthService>()
        val token = mockk<FirebaseToken>()

        every { authService.sessionCookieName } returns "session"
        every { token.uid } returns "user-1"
        every { authService.verifySessionCookie("valid-session") } returns token
        coEvery { onboardingQaService.submitOnboarding("user-1", any()) } returns AuthResponse.Ok(
            OnboardingSubmitResponse(success = true, uid = "user-1")
        )

        val authServices = buildAuthServices(
            loginService = loginService,
            onboardingQaService = onboardingQaService,
            authService = authService,
        )

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { authRoutes(authServices) }
        }

        val response = client.post("/api/v1/onboarding/submit") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            header("X-App-Id", "test-app")
            header(HttpHeaders.Cookie, "session=valid-session")
            setBody(json.encodeToString(OnboardingSubmitRequest()))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.decodeFromString<OnboardingSubmitResponse>(response.bodyAsText())
        assertTrue(payload.success)
        coVerify(exactly = 1) { onboardingQaService.submitOnboarding("user-1", any()) }
    }

    @org.junit.jupiter.api.Disabled("Flaky mock wiring in legacy route test; covered by integration suite.")
    @Test
    fun `logout returns ok when app header exists`() = testApplication {
        val loginService = mockk<LoginService>()
        coEvery { loginService.sessionLogout(any()) } returns AuthResponse.Ok(
            com.appforge.server.api.SessionLogoutResponse(success = true, uid = "user-1"),
            cookie = SessionCookieSpec(
                name = "session",
                value = "",
                maxAge = 0,
                secure = false,
                sameSite = "Lax",
            )
        )
        val authServices = buildAuthServices(loginService)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { authRoutes(authServices) }
        }

        val response = client.post("/api/v1/session/logout") {
            header("X-App-Id", "test-app")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `users me rejects unauthorized`() = testApplication {
        val loginService = mockk<LoginService>(relaxed = true)
        val authServices = buildAuthServices(loginService = loginService)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { authRoutes(authServices) }
        }

        val response = client.get("/api/v1/users/me")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `users me returns profile when authorized`() = testApplication {
        val loginService = mockk<LoginService>(relaxed = true)
        val userProfileService = mockk<UserProfileService>()
        val authService = mockk<AuthService>()
        val token = mockk<FirebaseToken>()

        every { token.uid } returns "user-1"
        every { authService.verifyIdToken("good-token") } returns token
        coEvery { userProfileService.userProfile("user-1") } returns UserProfileResponse(
            uid = "user-1",
            email = "user@test.com",
            name = "User One",
            createdAt = ProtoTimestamp(seconds = 1_767_225_600L),
            lastLoginAt = ProtoTimestamp(seconds = 1_767_312_000L),
            plan = null,
            usage = null,
        )

        val authServices = buildAuthServices(
            loginService = loginService,
            userProfileService = userProfileService,
            authService = authService,
        )

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { authRoutes(authServices) }
        }

        val response = client.get("/api/v1/users/me") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "test-app")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.decodeFromString<UserProfileResponse>(response.bodyAsText())
        assertEquals("user-1", payload.uid)
        assertEquals("user@test.com", payload.email)
        coVerify(exactly = 1) { userProfileService.userProfile("user-1") }
    }

    @Test
    fun `users me update rejects unauthorized`() = testApplication {
        val loginService = mockk<LoginService>(relaxed = true)
        val authServices = buildAuthServices(loginService = loginService)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { authRoutes(authServices) }
        }

        val response = client.put("/api/v1/users/me") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(UpdateUserProfileRequest(name = "Updated Name")))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `users me update returns ok when authorized`() = testApplication {
        val loginService = mockk<LoginService>(relaxed = true)
        val userAccountService = mockk<UserAccountService>()
        val authService = mockk<AuthService>()
        val token = mockk<FirebaseToken>()

        every { token.uid } returns "user-1"
        every { authService.verifyIdToken("good-token") } returns token
        coEvery {
            userAccountService.updateUserProfile(
                "user-1",
                UpdateUserProfileRequest(name = "Updated Name")
            )
        } returns AuthResponse.Ok(UpdateUserProfileResponse(success = true))

        val authServices = buildAuthServices(
            loginService = loginService,
            userAccountService = userAccountService,
            authService = authService,
        )

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { authRoutes(authServices) }
        }

        val response = client.put("/api/v1/users/me") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "test-app")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(UpdateUserProfileRequest(name = "Updated Name")))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.decodeFromString<UpdateUserProfileResponse>(response.bodyAsText())
        assertTrue(payload.success)
        coVerify(exactly = 1) {
            userAccountService.updateUserProfile("user-1", UpdateUserProfileRequest(name = "Updated Name"))
        }
    }

    @Test
    fun `password reset link rejects without app header`() = testApplication {
        val loginService = mockk<LoginService>(relaxed = true)
        val authServices = buildAuthServices(loginService = loginService)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { authRoutes(authServices) }
        }

        val response = client.post("/api/v1/session/password/reset-link") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(PasswordResetLinkRequest(email = "user@test.com")))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `password reset link returns ok with app header`() = testApplication {
        val loginService = mockk<LoginService>()
        coEvery { loginService.sendPasswordResetLink(any()) } returns AuthResponse.Ok(
            PasswordResetLinkResponse(success = true)
        )
        val authServices = buildAuthServices(loginService = loginService)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { authRoutes(authServices) }
        }

        val response = client.post("/api/v1/session/password/reset-link") {
            header("X-App-Id", "test-app")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(PasswordResetLinkRequest(email = "user@test.com")))
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.decodeFromString<PasswordResetLinkResponse>(response.bodyAsText())
        assertTrue(payload.success)
        coVerify(exactly = 1) {
            loginService.sendPasswordResetLink(PasswordResetLinkRequest(email = "user@test.com"))
        }
    }

    @Test
    fun `users me delete rejects unauthorized`() = testApplication {
        val loginService = mockk<LoginService>(relaxed = true)
        val authServices = buildAuthServices(loginService = loginService)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { authRoutes(authServices) }
        }

        val response = client.delete("/api/v1/users/me")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `users me delete returns ok when authorized`() = testApplication {
        val loginService = mockk<LoginService>(relaxed = true)
        val userAccountService = mockk<UserAccountService>()
        val authService = mockk<AuthService>()
        val token = mockk<FirebaseToken>()

        every { token.uid } returns "user-1"
        every { authService.verifyIdToken("good-token") } returns token
        coEvery { userAccountService.deleteUserAccount("user-1") } returns AuthResponse.Ok(
            DeleteUserAccountResponse(success = true)
        )

        val authServices = buildAuthServices(
            loginService = loginService,
            userAccountService = userAccountService,
            authService = authService,
        )

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { authRoutes(authServices) }
        }

        val response = client.delete("/api/v1/users/me") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "test-app")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.decodeFromString<DeleteUserAccountResponse>(response.bodyAsText())
        assertTrue(payload.success)
        coVerify(exactly = 1) { userAccountService.deleteUserAccount("user-1") }
    }

    @Test
    fun `users me usage returns summary when authorized`() = testApplication {
        val loginService = mockk<LoginService>(relaxed = true)
        val usageMetricsService = mockk<UsageMetricsService>()
        val authService = mockk<AuthService>()
        val token = mockk<FirebaseToken>()

        every { token.uid } returns "user-1"
        every { authService.verifyIdToken("good-token") } returns token
        coEvery {
            usageMetricsService.usageSummary(
                userId = "user-1",
                granularity = UsageGranularity.DAY,
                from = null,
                to = null,
            )
        } returns UsageSummary(
            granularity = UsageGranularity.DAY,
            from = null,
            to = null,
            series = listOf(
                UsageMetricSeries(
                    metric = UsageMetricKey.REVIEWS,
                    total = 5,
                    buckets = listOf(
                        UsageBucketCount(
                            windowStart = Instant.parse("2026-05-01T00:00:00Z"),
                            count = 2,
                        )
                    ),
                )
            ),
        )

        val authServices = buildAuthServices(
            loginService = loginService,
            usageMetricsService = usageMetricsService,
            authService = authService,
        )

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { authRoutes(authServices) }
        }

        val response = client.get("/api/v1/users/me/usage") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "test-app")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"granularity\":\"day\""))
        assertTrue(response.bodyAsText().contains("\"metric\":\"reviews\""))
    }

    private fun buildAuthServices(
        loginService: LoginService,
        authService: AuthService = mockk(relaxed = true),
        registrationService: RegistrationService = mockk(relaxed = true),
        onboardingQaService: OnboardingQaService = mockk(relaxed = true),
        userProfileService: UserProfileService = mockk(relaxed = true),
        userAccountService: UserAccountService = mockk(relaxed = true),
        usageMetricsService: UsageMetricsService = mockk(relaxed = true),
        earlyAccessAppService: EarlyAccessAppService = mockk(relaxed = true),
        onboardingFlowUseCases: com.appforge.server.services.onboarding.OnboardingFlowUseCases = mockk(relaxed = true),
        earlyAccessEnabled: Boolean = false,
    ): AuthServices {
        return object : AuthServices {
            override val authService = authService
            override val requestIdentityProvider = ExternalIdentityProvider(authService)
            override val loginService = loginService
            override val registrationService = registrationService
            override val onboardingQaService = onboardingQaService
            override val userProfileService = userProfileService
            override val userAccountService = userAccountService
            override val usageMetricsService = usageMetricsService
            override val earlyAccessAppService = earlyAccessAppService
            override val onboardingFlowUseCases = onboardingFlowUseCases
            override val runtimeOptions = RuntimeOptions(
                appId = "test-app",
                port = 8080,
                host = "localhost",
                corsAllowedOrigins = emptyList(),
                nodeEnv = "test",
                publicBaseUrl = "http://localhost:8080",
                internalSecret = "test-secret",
                earlyAccessEnabled = earlyAccessEnabled,
                documentMaxContentChars = 20_000,
            )
        }
    }
}

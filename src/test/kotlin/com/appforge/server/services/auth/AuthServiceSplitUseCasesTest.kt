package com.appforge.server.services.auth

import com.appforge.server.api.OnboardingSubmitAnswerRequest
import com.appforge.server.api.OnboardingSubmitRequest
import com.appforge.server.api.ProtoTimestamp
import com.appforge.server.api.SessionLoginRequest
import com.appforge.server.api.SignupInitRequest
import com.appforge.server.providers.time.TimestampProvider
import com.appforge.server.services.billing.SignupEntitlementCoordinator
import com.appforge.server.services.earlyaccess.EarlyAccessDecision
import com.appforge.server.services.earlyaccess.EarlyAccessService
import com.appforge.server.services.login.LoginServiceImpl
import com.appforge.server.services.onboarding.OnboardingQaServiceImpl
import com.appforge.server.services.registration.RegistrationServiceImpl
import com.google.firebase.auth.FirebaseToken
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthServiceSplitUseCasesTest {
    @Test
    fun `identity resolver returns unauthorized when id token verification fails`() {
        val authService = mockk<AuthService>()
        every { authService.verifyIdToken("bad-token") } returns null

        val resolver = IdentityProviderUserResolver(authService)
        val result = resolver.fromIdToken("bad-token")

        assertTrue(result is AuthResponse.Unauthorized)
        assertEquals("Unauthorized", result.message)
    }

    @Test
    fun `identity resolver falls back to provider email when token email missing`() {
        val authService = mockk<AuthService>()
        val token = mockk<FirebaseToken>()
        every { authService.verifyIdToken("token") } returns token
        every { token.uid } returns "uid-fallback"
        every { token.email } returns null
        every { token.name } returns "Fallback User"
        every { authService.getUserEmail("uid-fallback") } returns "fallback@test.com"

        val resolver = IdentityProviderUserResolver(authService)
        val result = resolver.fromIdToken("token")

        assertTrue(result is AuthResponse.Ok)
        assertEquals("uid-fallback", result.data.uid)
        assertEquals("fallback@test.com", result.data.email)
        assertEquals("Fallback User", result.data.name)
    }

    @Test
    fun `identity resolver falls back to provider email when token email blank`() {
        val authService = mockk<AuthService>()
        val token = mockk<FirebaseToken>()
        every { authService.verifyIdToken("token") } returns token
        every { token.uid } returns "uid-blank"
        every { token.email } returns "   "
        every { token.name } returns "Blank Email User"
        every { authService.getUserEmail("uid-blank") } returns "resolved@test.com"

        val resolver = IdentityProviderUserResolver(authService)
        val result = resolver.fromIdToken("token")

        assertTrue(result is AuthResponse.Ok)
        assertEquals("resolved@test.com", result.data.email)
    }

    @Test
    fun `identity resolver returns unauthorized when token and provider email both missing`() {
        val authService = mockk<AuthService>()
        val token = mockk<FirebaseToken>()
        every { authService.verifySessionCookie("cookie") } returns token
        every { token.uid } returns "uid-missing"
        every { token.email } returns null
        every { token.name } returns null
        every { authService.getUserEmail("uid-missing") } returns null

        val resolver = IdentityProviderUserResolver(authService)
        val result = resolver.fromSessionCookie("cookie")

        assertTrue(result is AuthResponse.Unauthorized)
        assertEquals("Authenticated session is missing email.", result.message)
    }

    @Test
    fun `session login succeeds regardless of early access`() = kotlinx.coroutines.runBlocking {
        val authService = mockk<AuthService>()
        val userLifecycleCoordinator = mockk<UserLifecycleCoordinator>(relaxed = true)
        val token = mockk<FirebaseToken>()

        coEvery { authService.verifyIdToken("token") } returns token
        every { token.uid } returns "uid-1"
        every { token.email } returns "blocked@test.com"
        every { token.name } returns "Blocked User"
        coEvery { authService.createSessionCookie("token") } returns "cookie-value"
        every { authService.sessionExpirySeconds() } returns 3600
        every { authService.sessionCookieName } returns "session"
        every { authService.cookieSecure } returns false
        every { authService.cookieSameSite } returns "Lax"

        val useCases = LoginServiceImpl(
            authService = authService,
            userLifecycleCoordinator = userLifecycleCoordinator,
        )
        val result = useCases.sessionLogin(SessionLoginRequest("token"))

        assertTrue(result is AuthResponse.Ok)
    }

    @Test
    fun `session login returns unauthorized when identity email cannot be resolved`() = kotlinx.coroutines.runBlocking {
        val authService = mockk<AuthService>()
        val userLifecycleCoordinator = mockk<UserLifecycleCoordinator>(relaxed = true)
        val token = mockk<FirebaseToken>()

        coEvery { authService.verifyIdToken("token") } returns token
        every { token.uid } returns "uid-no-email"
        every { token.email } returns null
        every { authService.getUserEmail("uid-no-email") } returns null

        val useCases = LoginServiceImpl(
            authService = authService,
            userLifecycleCoordinator = userLifecycleCoordinator,
        )
        val result = useCases.sessionLogin(SessionLoginRequest("token"))

        assertTrue(result is AuthResponse.Unauthorized)
        assertEquals("Authenticated identity is missing email.", result.message)
        coVerify(exactly = 0) { userLifecycleCoordinator.ensureUserCreated(any(), any(), any()) }
    }

    @Test
    fun `signup init blocks unapproved users when early access enabled`() = kotlinx.coroutines.runBlocking {
        val authService = mockk<AuthService>()
        val earlyAccessService = mockk<EarlyAccessService>()
        val signupEntitlementCoordinator = mockk<SignupEntitlementCoordinator>(relaxed = true)
        val userLifecycleCoordinator = mockk<UserLifecycleCoordinator>(relaxed = true)
        val token = mockk<FirebaseToken>()

        coEvery { authService.verifyIdToken("token") } returns token
        every { token.uid } returns "uid-1"
        every { token.email } returns "blocked@test.com"
        every { token.name } returns "Blocked User"
        coEvery { earlyAccessService.enforceLoginAccess("blocked@test.com") } returns EarlyAccessDecision.Blocked(
            "Early access required."
        )

        val useCases = RegistrationServiceImpl(
            authService = authService,
            earlyAccessService = earlyAccessService,
            signupEntitlementCoordinator = signupEntitlementCoordinator,
            userLifecycleCoordinator = userLifecycleCoordinator,
        )
        val result = useCases.signupInit(SignupInitRequest("token"))

        assertTrue(result is AuthResponse.Forbidden)
        coVerify(exactly = 1) { earlyAccessService.enforceLoginAccess("blocked@test.com") }
    }

    @Test
    fun `signup init returns unauthorized when identity email cannot be resolved`() = kotlinx.coroutines.runBlocking {
        val authService = mockk<AuthService>()
        val earlyAccessService = mockk<EarlyAccessService>(relaxed = true)
        val signupEntitlementCoordinator = mockk<SignupEntitlementCoordinator>(relaxed = true)
        val userLifecycleCoordinator = mockk<UserLifecycleCoordinator>(relaxed = true)
        val token = mockk<FirebaseToken>()

        coEvery { authService.verifyIdToken("token") } returns token
        every { token.uid } returns "uid-no-email"
        every { token.email } returns null
        every { authService.getUserEmail("uid-no-email") } returns null

        val useCases = RegistrationServiceImpl(
            authService = authService,
            earlyAccessService = earlyAccessService,
            signupEntitlementCoordinator = signupEntitlementCoordinator,
            userLifecycleCoordinator = userLifecycleCoordinator,
        )
        val result = useCases.signupInit(SignupInitRequest("token"))

        assertTrue(result is AuthResponse.Unauthorized)
        assertEquals("Authenticated identity is missing email.", result.message)
        coVerify(exactly = 0) { earlyAccessService.enforceLoginAccess(any()) }
        coVerify(exactly = 0) { signupEntitlementCoordinator.initializeDefaultEntitlement(any()) }
    }

    @Test
    fun `signup init provisions approved user`() = kotlinx.coroutines.runBlocking {
        val authService = mockk<AuthService>()
        val earlyAccessService = mockk<EarlyAccessService>(relaxed = true)
        val signupEntitlementCoordinator = mockk<SignupEntitlementCoordinator>(relaxed = true)
        val userLifecycleCoordinator = mockk<UserLifecycleCoordinator>(relaxed = true)
        val token = mockk<FirebaseToken>()

        coEvery { authService.verifyIdToken("token") } returns token
        every { token.uid } returns "uid-init"
        every { token.email } returns "init@test.com"
        every { token.name } returns "Init User"
        coEvery { earlyAccessService.enforceLoginAccess("init@test.com") } returns EarlyAccessDecision.Allowed
        coEvery { userLifecycleCoordinator.hasExistingAccount("uid-init") } returns false

        val useCases = RegistrationServiceImpl(
            authService = authService,
            earlyAccessService = earlyAccessService,
            signupEntitlementCoordinator = signupEntitlementCoordinator,
            userLifecycleCoordinator = userLifecycleCoordinator,
        )
        val result = useCases.signupInit(SignupInitRequest("token"))

        assertTrue(result is AuthResponse.Ok)
        coVerify(exactly = 1) { userLifecycleCoordinator.ensureUserCreated("uid-init", "init@test.com", "Init User") }
        coVerify(exactly = 1) { signupEntitlementCoordinator.initializeDefaultEntitlement("uid-init") }
    }

    @Test
    fun `signup init bypasses early access for existing account`() = kotlinx.coroutines.runBlocking {
        val authService = mockk<AuthService>()
        val earlyAccessService = mockk<EarlyAccessService>(relaxed = true)
        val signupEntitlementCoordinator = mockk<SignupEntitlementCoordinator>(relaxed = true)
        val userLifecycleCoordinator = mockk<UserLifecycleCoordinator>(relaxed = true)
        val token = mockk<FirebaseToken>()

        coEvery { authService.verifyIdToken("token") } returns token
        every { token.uid } returns "uid-existing"
        every { token.email } returns "existing@test.com"
        every { token.name } returns "Existing User"
        coEvery { userLifecycleCoordinator.hasExistingAccount("uid-existing") } returns true

        val service = RegistrationServiceImpl(
            authService = authService,
            earlyAccessService = earlyAccessService,
            signupEntitlementCoordinator = signupEntitlementCoordinator,
            userLifecycleCoordinator = userLifecycleCoordinator,
        )
        val result = service.signupInit(SignupInitRequest("token"))

        assertTrue(result is AuthResponse.Ok)
        coVerify(exactly = 0) { earlyAccessService.enforceLoginAccess(any()) }
    }

    @Test
    fun `submit onboarding persists answers and marks completion`() = kotlinx.coroutines.runBlocking {
        val userLifecycleCoordinator = mockk<UserLifecycleCoordinator>(relaxed = true)

        coEvery { userLifecycleCoordinator.questionExists("q_goal") } returns true
        coEvery { userLifecycleCoordinator.optionBelongsToQuestion("opt_goal", "q_goal") } returns true

        val useCases = OnboardingQaServiceImpl(
            userLifecycleCoordinator = userLifecycleCoordinator,
            timestampProvider = object : TimestampProvider {
                override fun now(): Instant = Instant.parse("2026-01-01T00:00:00Z")
            },
        )
        val result = useCases.submitOnboarding(
            userId = "uid-auth",
            request = OnboardingSubmitRequest(
                answers = listOf(OnboardingSubmitAnswerRequest(questionId = "q_goal", optionIds = listOf("opt_goal"))),
                completedAt = ProtoTimestamp(seconds = 1716120000L, nanos = 0),
            )
        )

        assertTrue(result is AuthResponse.Ok)
        coVerify(exactly = 1) { userLifecycleCoordinator.persistOnboardingAnswer("uid-auth", "q_goal", listOf("opt_goal"), null, any()) }
        coVerify(exactly = 1) { userLifecycleCoordinator.markOnboardingCompleted("uid-auth", any()) }
    }
}

package com.appforge.server.integration

import com.appforge.server.api.OnboardingSubmitAnswerRequest
import com.appforge.server.api.OnboardingSubmitRequest
import com.appforge.server.api.ProtoTimestamp
import com.appforge.server.api.SessionLoginRequest
import com.appforge.server.api.SignupInitRequest
import com.appforge.server.providers.time.TimestampProvider
import com.appforge.server.services.auth.AuthResponse
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.auth.UserLifecycleCoordinator
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

class AuthUseCasesUnitTest {
    @Test
    fun `session login provisions user and returns session cookie`() = kotlinx.coroutines.runBlocking {
        val authService = mockk<AuthService>()
        val userLifecycleCoordinator = mockk<UserLifecycleCoordinator>(relaxed = true)
        val token = mockk<FirebaseToken>()

        every { authService.verifyIdToken("token") } returns token
        every { token.uid } returns "uid-1"
        every { token.email } returns "user@test.com"
        every { token.name } returns "User"
        coEvery { authService.createSessionCookie("token") } returns "cookie-value"
        every { authService.sessionExpirySeconds() } returns 3600
        every { authService.sessionCookieName } returns "session"
        every { authService.cookieSecure } returns false
        every { authService.cookieSameSite } returns "Lax"

        val service = LoginServiceImpl(
            authService = authService,
            userLifecycleCoordinator = userLifecycleCoordinator,
        )

        val result = service.sessionLogin(SessionLoginRequest("token"))

        assertTrue(result is AuthResponse.Ok)
        assertEquals("uid-1", result.data.uid)
        assertEquals("session", result.cookie?.name)
        assertEquals("cookie-value", result.cookie?.value)
        coVerify(exactly = 1) { userLifecycleCoordinator.ensureUserCreated("uid-1", "user@test.com", "User") }
    }

    @Test
    fun `signup init blocks unapproved users when early access is enabled`() = kotlinx.coroutines.runBlocking {
        val authService = mockk<AuthService>()
        val earlyAccessService = mockk<EarlyAccessService>()
        val signupEntitlementCoordinator = mockk<SignupEntitlementCoordinator>(relaxed = true)
        val userLifecycleCoordinator = mockk<UserLifecycleCoordinator>(relaxed = true)
        val token = mockk<FirebaseToken>()

        every { authService.verifyIdToken("token") } returns token
        every { token.uid } returns "uid-1"
        every { token.email } returns "blocked@test.com"
        every { token.name } returns "Blocked User"
        coEvery { userLifecycleCoordinator.hasExistingAccount("uid-1") } returns false
        coEvery {
            earlyAccessService.enforceLoginAccess("blocked@test.com")
        } returns EarlyAccessDecision.Blocked("Early access required.")

        val service = RegistrationServiceImpl(
            authService = authService,
            earlyAccessService = earlyAccessService,
            signupEntitlementCoordinator = signupEntitlementCoordinator,
            userLifecycleCoordinator = userLifecycleCoordinator,
        )

        val result = service.signupInit(SignupInitRequest("token"))

        assertTrue(result is AuthResponse.Forbidden)
        coVerify(exactly = 1) { earlyAccessService.enforceLoginAccess("blocked@test.com") }
        coVerify(exactly = 0) { signupEntitlementCoordinator.initializeDefaultEntitlement(any()) }
    }

    @Test
    fun `signup init provisions approved user`() = kotlinx.coroutines.runBlocking {
        val authService = mockk<AuthService>()
        val earlyAccessService = mockk<EarlyAccessService>(relaxed = true)
        val signupEntitlementCoordinator = mockk<SignupEntitlementCoordinator>(relaxed = true)
        val userLifecycleCoordinator = mockk<UserLifecycleCoordinator>(relaxed = true)
        val token = mockk<FirebaseToken>()

        every { authService.verifyIdToken("token") } returns token
        every { token.uid } returns "uid-init"
        every { token.email } returns "init@test.com"
        every { token.name } returns "Init User"
        coEvery { userLifecycleCoordinator.hasExistingAccount("uid-init") } returns false
        coEvery { earlyAccessService.enforceLoginAccess("init@test.com") } returns EarlyAccessDecision.Allowed

        val service = RegistrationServiceImpl(
            authService = authService,
            earlyAccessService = earlyAccessService,
            signupEntitlementCoordinator = signupEntitlementCoordinator,
            userLifecycleCoordinator = userLifecycleCoordinator,
        )

        val result = service.signupInit(SignupInitRequest("token"))

        assertTrue(result is AuthResponse.Ok)
        assertEquals("uid-init", result.data.uid)
        coVerify(exactly = 1) { userLifecycleCoordinator.ensureUserCreated("uid-init", "init@test.com", "Init User") }
        coVerify(exactly = 1) { signupEntitlementCoordinator.initializeDefaultEntitlement("uid-init") }
        coVerify(exactly = 1) { earlyAccessService.autoApproveWhenOpen("init@test.com") }
    }

    @Test
    fun `submit onboarding persists normalized answers and completion timestamp`() = kotlinx.coroutines.runBlocking {
        val userLifecycleCoordinator = mockk<UserLifecycleCoordinator>(relaxed = true)
        coEvery { userLifecycleCoordinator.questionExists("q_goal") } returns true
        coEvery { userLifecycleCoordinator.optionBelongsToQuestion("opt_goal", "q_goal") } returns true

        val service = OnboardingQaServiceImpl(
            userLifecycleCoordinator = userLifecycleCoordinator,
            timestampProvider = object : TimestampProvider {
                override fun now(): Instant = Instant.parse("2026-01-01T00:00:00Z")
            },
        )

        val result = service.submitOnboarding(
            userId = "uid-auth",
            request = OnboardingSubmitRequest(
                answers = listOf(
                    OnboardingSubmitAnswerRequest(
                        questionId = "q_goal",
                        optionIds = listOf("opt_goal"),
                    )
                ),
                completedAt = ProtoTimestamp(seconds = 1716120000L, nanos = 0),
            ),
        )

        assertTrue(result is AuthResponse.Ok)
        coVerify(exactly = 1) {
            userLifecycleCoordinator.persistOnboardingAnswer(
                "uid-auth",
                "q_goal",
                listOf("opt_goal"),
                null,
                any(),
            )
        }
        coVerify(exactly = 1) { userLifecycleCoordinator.markOnboardingCompleted("uid-auth", any()) }
    }

    @Test
    fun `signup init propagates duplicate email collision as deterministic error`() = kotlinx.coroutines.runBlocking {
        val authService = mockk<AuthService>()
        val earlyAccessService = mockk<EarlyAccessService>(relaxed = true)
        val signupEntitlementCoordinator = mockk<SignupEntitlementCoordinator>(relaxed = true)
        val userLifecycleCoordinator = mockk<UserLifecycleCoordinator>()
        val token = mockk<FirebaseToken>()

        every { authService.verifyIdToken("token") } returns token
        every { token.uid } returns "uid-2"
        every { token.email } returns "existing@test.com"
        every { token.name } returns "Collision User"
        coEvery { userLifecycleCoordinator.hasExistingAccount("uid-2") } returns false
        coEvery { earlyAccessService.enforceLoginAccess("existing@test.com") } returns EarlyAccessDecision.Allowed
        coEvery {
            userLifecycleCoordinator.ensureUserCreated("uid-2", "existing@test.com", "Collision User")
        } throws IllegalArgumentException("An account with this email already exists. Please log in.")

        val service = RegistrationServiceImpl(
            authService = authService,
            earlyAccessService = earlyAccessService,
            signupEntitlementCoordinator = signupEntitlementCoordinator,
            userLifecycleCoordinator = userLifecycleCoordinator,
        )

        val thrown = kotlin.runCatching {
            service.signupInit(SignupInitRequest("token"))
        }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
        assertEquals("An account with this email already exists. Please log in.", thrown.message)
    }
}

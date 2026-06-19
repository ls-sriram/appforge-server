package com.appforge.server.middleware

import com.appforge.server.services.auth.AuthService
import com.google.firebase.auth.FirebaseToken
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.slf4j.MDC
import kotlin.test.*

class RequestContextTest {

    private val authService: AuthService = mockk()

    @BeforeTest
    fun setUp() { MDC.clear() }

    @AfterTest
    fun tearDown() { MDC.clear() }

    // ─── RequestContext data class ───────────────────────────────────────

    @Test
    fun `appId is required`() {
        val ctx = RequestContext(userId = "user-1", appId = "my-app")
        assertEquals("user-1", ctx.userId)
        assertEquals("my-app", ctx.appId)
        assertNull(ctx.teamId)
        assertEquals(setOf(PlatformRole.OWNER), ctx.roles)
        assertFalse(ctx.isAdmin)
    }

    @Test
    fun `admin role`() {
        val ctx = RequestContext(
            userId = "user-1",
            appId = "my-app",
            roles = setOf(PlatformRole.OWNER, PlatformRole.ADMIN),
        )
        assertTrue(ctx.isAdmin)
    }

    @Test
    fun `team context is optional`() {
        val ctx = RequestContext(userId = "user-1", appId = "my-app", teamId = "team-1")
        assertEquals("team-1", ctx.teamId)
    }

    // ─── resolveRequestContext ───────────────────────────────────────────

    @Test
    fun `requires X-App-Id header`() = runBlocking {
        coEvery { authService.verifyIdToken(any()) } returns mockToken()

        val call = TestCall(
            authHeader = "Bearer token1",
            requestCookies = emptyMap(),
            requestQueryParams = emptyMap(),
            requestHeaders = emptyMap(),
        )
        val ctx = call.resolveRequestContext(authService)
        assertNull(ctx)
    }

    @Test
    fun `resolves context with X-App-Id and bearer token`() = runBlocking {
        coEvery { authService.verifyIdToken(any()) } returns mockToken()
        coEvery { authService.internalSecret } returns "test-secret"

        val call = TestCall(
            authHeader = "Bearer token1",
            requestCookies = emptyMap(),
            requestQueryParams = emptyMap(),
            requestHeaders = mapOf(
                "X-App-Id" to "my-app",
                "X-Team-Id" to "team-1",
                "X-Internal-Secret" to "test-secret",
            ),
        )
        val ctx = call.resolveRequestContext(authService)
        assertNotNull(ctx)
        assertEquals("user-123", ctx.userId)
        assertEquals("my-app", ctx.appId)
        assertEquals("team-1", ctx.teamId)
        assertEquals(setOf(PlatformRole.OWNER, PlatformRole.ADMIN), ctx.roles)
        assertTrue(ctx.isAdmin)
    }

    @Test
    fun `resolves context with X-App-Id and session cookie`() = runBlocking {
        coEvery { authService.verifySessionCookie(any()) } returns mockToken()
        coEvery { authService.sessionCookieName } returns "session"

        val call = TestCall(
            authHeader = null,
            requestCookies = mapOf("session" to "cookie-value"),
            requestQueryParams = emptyMap(),
            requestHeaders = mapOf("X-App-Id" to "my-app"),
        )
        val ctx = call.resolveRequestContext(authService)
        assertNotNull(ctx)
        assertEquals("user-123", ctx.userId)
        assertEquals("my-app", ctx.appId)
    }

    @Test
    fun `populates MDC with userId and appId`() = runBlocking {
        coEvery { authService.verifyIdToken(any()) } returns mockToken()

        val call = TestCall(
            authHeader = "Bearer token1",
            requestCookies = emptyMap(),
            requestQueryParams = emptyMap(),
            requestHeaders = mapOf("X-App-Id" to "my-app"),
        )
        call.resolveRequestContext(authService)

        assertEquals("user-123", MDC.get("userId"))
        assertEquals("my-app", MDC.get("appId"))
    }

    // ─── resolveUserId (legacy helper) ──────────────────────────────────

    @Test
    fun `resolves uid from bearer token`() = runBlocking {
        coEvery { authService.verifyIdToken(any()) } returns mockToken()

        val call = TestCall(
            authHeader = "Bearer token1",
            requestCookies = emptyMap(),
            requestQueryParams = emptyMap(),
            requestHeaders = emptyMap(),
        )
        val uid = call.resolveUserId(authService)
        assertEquals("user-123", uid)
    }

    @Test
    fun `resolves uid from session cookie`() = runBlocking {
        coEvery { authService.verifySessionCookie(any()) } returns mockToken()
        coEvery { authService.sessionCookieName } returns "session"

        val call = TestCall(
            authHeader = null,
            requestCookies = mapOf("session" to "cookie-value"),
            requestQueryParams = emptyMap(),
            requestHeaders = emptyMap(),
        )
        val uid = call.resolveUserId(authService)
        assertEquals("user-123", uid)
    }

    @Test
    fun `returns null when no auth present`() = runBlocking {
        coEvery { authService.sessionCookieName } returns "session"

        val call = TestCall(
            authHeader = null,
            requestCookies = emptyMap(),
            requestQueryParams = emptyMap(),
            requestHeaders = emptyMap(),
        )
        val uid = call.resolveUserId(authService)
        assertNull(uid)
    }

    // ─── Helpers ─────────────────────────────────────────────────────────

    private fun mockToken(): FirebaseToken {
        val token = mockk<FirebaseToken>()
        coEvery { token.uid } returns "user-123"
        coEvery { token.email } returns "test@example.com"
        coEvery { token.name } returns "Test User"
        return token
    }
}

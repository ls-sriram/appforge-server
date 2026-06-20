package com.appforge.server.integration

import com.appforge.server.api.SignupInitRequest
import com.appforge.server.api.UpdateUserProfileRequest
import com.appforge.server.config.options.RuntimeOptions
import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.middleware.configureErrorHandling
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.auth.AuthServices
import com.appforge.server.services.auth.UserLifecycleCoordinator
import com.appforge.server.services.auth.repository.SqlUserRepository
import com.appforge.server.services.billing.SignupEntitlementCoordinator
import com.appforge.server.services.billing.catalog.BillingFeatureCatalog
import com.appforge.server.services.billing.models.BillingEntitlement
import com.appforge.server.services.billing.models.BillingSource
import com.appforge.server.services.billing.models.BillingStatus
import com.appforge.server.services.billing.models.Plan
import com.appforge.server.services.earlyaccess.EarlyAccessAppService
import com.appforge.server.services.earlyaccess.EarlyAccessDecision
import com.appforge.server.services.earlyaccess.EarlyAccessService
import com.appforge.server.services.login.LoginService
import com.appforge.server.services.onboarding.OnboardingFlowUseCases
import com.appforge.server.services.onboarding.OnboardingQaService
import com.appforge.server.services.onboarding.repository.SqlOnboardingRepository
import com.appforge.server.services.registration.RegistrationService
import com.appforge.server.services.registration.RegistrationServiceImpl
import com.appforge.server.services.usage.UsageMetricsService
import com.appforge.server.services.useraccount.AccountDeletionAuditRepository
import com.appforge.server.services.useraccount.AccountDeletionService
import com.appforge.server.services.useraccount.UserAccountService
import com.appforge.server.services.useraccount.UserAccountServiceImpl
import com.appforge.server.services.userprofile.UserProfileService
import com.appforge.server.routing.authRoutes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseToken
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.delete
import io.ktor.client.request.put
import io.ktor.client.request.setBody
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
import io.mockk.every
import io.mockk.mockk
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class UserAccountSqlTraceIntegrationTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `user create update delete emits sql trace report`() = testApplication {
        val db = IntegrationDbHarness.createDatabase()
        resetUserTables(db)

        val traceFile = resolveTraceFile()
        Files.createDirectories(traceFile.parent)
        traceFile.writeText("")

        val services = buildAuthServices(db)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing {
                authRoutes(services)
            }
        }

        val signupResponse = client.post("/api/v1/signup/init") {
            header("X-App-Id", "integration-app")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(SignupInitRequest(idToken = "good-token")))
        }
        assertEquals(HttpStatusCode.OK, signupResponse.status)
        appendTrace(traceFile, "create", db, "user-1")

        val updateResponse = client.put("/api/v1/users/me") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "integration-app")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(UpdateUserProfileRequest(name = "Updated User")))
        }
        assertEquals(HttpStatusCode.OK, updateResponse.status)
        appendTrace(traceFile, "update", db, "user-1")

        val accountDeleteResponse = client.delete("/api/v1/users/me") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "integration-app")
        }
        assertEquals(HttpStatusCode.OK, accountDeleteResponse.status)
        appendTrace(traceFile, "delete", db, "user-1")
    }

    private fun buildAuthServices(db: ExposedDatabase): AuthServices {
        val authService = mockk<AuthService>()
        val token = mockk<FirebaseToken>()
        every { token.uid } returns "user-1"
        every { token.email } returns "user-1@example.com"
        every { token.name } returns "User One"
        every { authService.verifyIdToken("good-token") } returns token
        coEvery { authService.createSessionCookie("good-token") } returns "session-cookie"
        every { authService.verifySessionCookie(any()) } returns token
        every { authService.sessionCookieName } returns "session"
        every { authService.sessionExpirySeconds() } returns 3600
        every { authService.cookieSecure } returns false
        every { authService.cookieSameSite } returns "Lax"
        every { authService.internalSecret } returns "integration-secret"

        val userRepository = SqlUserRepository(db)
        val onboardingRepository = SqlOnboardingRepository(db)
        val userLifecycleCoordinator = UserLifecycleCoordinator(userRepository, onboardingRepository)

        val earlyAccessService = mockk<EarlyAccessService>()
        coEvery { earlyAccessService.enforceLoginAccess(any()) } returns EarlyAccessDecision.Allowed
        coEvery { earlyAccessService.autoApproveWhenOpen(any()) } returns Unit
        every { earlyAccessService.isEnabled() } returns false
        coEvery { earlyAccessService.hasAccess(any()) } returns true
        coEvery { earlyAccessService.joinWaitlist(any()) } returns true

        val signupEntitlementCoordinator = mockk<SignupEntitlementCoordinator>()
        coEvery { signupEntitlementCoordinator.initializeDefaultEntitlement("user-1") } returns BillingEntitlement(
            customerId = "user-1",
            plan = Plan.FREE,
            status = BillingStatus.ACTIVE,
            expiresAt = null,
            startedAt = Instant.parse("2026-01-01T00:00:00Z"),
            source = BillingSource.MANUAL,
            features = BillingFeatureCatalog.defaultForPlan(Plan.FREE),
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
        )

        val registrationService: RegistrationService = RegistrationServiceImpl(
            authService = authService,
            earlyAccessService = earlyAccessService,
            signupEntitlementCoordinator = signupEntitlementCoordinator,
            userLifecycleCoordinator = userLifecycleCoordinator,
        )

        val firebaseAuth = mockk<FirebaseAuth>()
        every { firebaseAuth.revokeRefreshTokens("user-1") } returns Unit
        every { firebaseAuth.deleteUser("user-1") } returns Unit

        val userAccountService: UserAccountService = UserAccountServiceImpl(
            userRepository = userRepository,
            accountDeletionService = AccountDeletionService(
                userRepository = userRepository,
                firebaseAuth = firebaseAuth,
                auditRepository = AccountDeletionAuditRepository(db),
            ),
        )

        return object : AuthServices {
            override val authService: AuthService = authService
            override val requestIdentityProvider = ExternalIdentityProvider(authService)
            override val loginService: LoginService = mockk(relaxed = true)
            override val registrationService: RegistrationService = registrationService
            override val onboardingQaService: OnboardingQaService = mockk(relaxed = true)
            override val userProfileService: UserProfileService = mockk(relaxed = true)
            override val userAccountService: UserAccountService = userAccountService
            override val usageMetricsService: UsageMetricsService = mockk(relaxed = true)
            override val earlyAccessAppService: EarlyAccessAppService = mockk(relaxed = true)
            override val onboardingFlowUseCases: OnboardingFlowUseCases = mockk(relaxed = true)
            override val runtimeOptions: RuntimeOptions = RuntimeOptions(
                appId = "integration-app",
                port = 0,
                host = "127.0.0.1",
                corsAllowedOrigins = emptyList(),
                nodeEnv = "test",
                publicBaseUrl = "http://localhost",
                internalSecret = "integration-secret",
                earlyAccessEnabled = false,
                documentMaxContentChars = 20_000,
            )
        }
    }

    private fun resetUserTables(db: ExposedDatabase) = runBlocking {
        db.withConnection { conn ->
            conn.prepareStatement(
                """
                TRUNCATE TABLE account_deletion_audit_records, onboarding_responses, onboarding_state, profiles, app_users
                RESTART IDENTITY CASCADE
                """.trimIndent()
            ).use { it.executeUpdate() }
        }
    }

    private fun appendTrace(traceFile: Path, phase: String, db: ExposedDatabase, userId: String) {
        val content = buildString {
            appendLine("== $phase ==")
            append(renderQueryBlock(db, "SELECT uid, email, email_normalized, display_name FROM app_users WHERE uid = ? ORDER BY uid", userId))
            append(renderQueryBlock(db, "SELECT user_id, display_name, email, email_normalized FROM profiles WHERE user_id = ? ORDER BY user_id", userId))
            append(renderQueryBlock(db, "SELECT user_id, version, current_step, completed FROM onboarding_state WHERE user_id = ? ORDER BY user_id", userId))
            append(renderQueryBlock(db, "SELECT user_id, event, status, COALESCE(detail, '') AS detail FROM account_deletion_audit_records WHERE user_id = ? ORDER BY created_at, id", userId))
            appendLine()
        }
        Files.writeString(traceFile, content, java.nio.file.StandardOpenOption.APPEND)
    }

    private fun renderQueryBlock(db: ExposedDatabase, sql: String, userId: String): String = runBlocking {
        db.withConnection { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, userId)
                stmt.executeQuery().use { rs ->
                    val meta = rs.metaData
                    val headers = (1..meta.columnCount).joinToString("\t") { meta.getColumnLabel(it) }
                    val rows = buildList {
                        while (rs.next()) {
                            add((1..meta.columnCount).joinToString("\t") { idx -> rs.getString(idx) ?: "NULL" })
                        }
                    }
                    buildString {
                        appendLine(sql)
                        appendLine(headers)
                        if (rows.isEmpty()) {
                            appendLine("<no rows>")
                        } else {
                            rows.forEach { appendLine(it) }
                        }
                    }
                }
            }
        }
    }

    private fun resolveTraceFile(): Path =
        Paths.get(
            System.getenv("USER_ACCOUNT_SQL_TRACE_FILE")
                ?.takeIf { it.isNotBlank() }
                ?: "build/reports/integration-sql/user-account-crud-trace.txt"
        )
}

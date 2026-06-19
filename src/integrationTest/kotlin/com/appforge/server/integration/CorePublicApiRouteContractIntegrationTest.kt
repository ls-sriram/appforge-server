package com.appforge.server.integration

import com.appforge.server.api.BillingSourceDto
import com.appforge.server.api.BillingStatusDto
import com.appforge.server.api.CheckoutResponse
import com.appforge.server.api.DeleteUserAccountResponse
import com.appforge.server.api.EarlyAccessCheckResponse
import com.appforge.server.api.EarlyAccessJoinResponse
import com.appforge.server.api.EntitlementSnapshotResponse
import com.appforge.server.api.OnboardingFlowResponse
import com.appforge.server.api.OnboardingSubmitResponse
import com.appforge.server.api.PasswordResetLinkResponse
import com.appforge.server.api.PlanDto
import com.appforge.server.api.PricingCardsResponse
import com.appforge.server.api.ProtoTimestamp
import com.appforge.server.api.SessionLoginResponse
import com.appforge.server.api.SessionLogoutResponse
import com.appforge.server.api.SessionMeResponse
import com.appforge.server.api.SignupInitResponse
import com.appforge.server.api.UpdateUserProfileResponse
import com.appforge.server.api.UploadCompleteResponse
import com.appforge.server.api.UploadInitResponse
import com.appforge.server.api.UploadTypeDto
import com.appforge.server.api.UserProfileResponse
import com.appforge.server.api.WebhookResponse
import com.appforge.server.api.reviews.ReviewResponse
import com.appforge.server.api.reviews.ReviewTemplate
import com.appforge.server.api.reviews.ReviewTemplateField
import com.appforge.server.api.reviews.ReviewTemplateResponse
import com.appforge.server.api.sharing.PublicEntity
import com.appforge.server.api.sharing.PublicEntityResponse
import com.appforge.server.api.sharing.PublicShareMetadata
import com.appforge.server.api.sharing.ShareResponse
import com.appforge.server.api.sharing.ShareSummaryResponse
import com.appforge.server.config.options.RuntimeOptions
import com.appforge.server.middleware.configureErrorHandling
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.routing.authRoutes
import com.appforge.server.routing.billingRoutes
import com.appforge.server.routing.entityReviewRoutes
import com.appforge.server.routing.entityShareRoutes
import com.appforge.server.routing.healthRoutes
import com.appforge.server.routing.publicShareRoutes
import com.appforge.server.routing.reviewRoutes
import com.appforge.server.routing.uploadEventRoutes
import com.appforge.server.routing.uploadRoutes
import com.appforge.server.services.auth.AuthResponse
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.auth.AuthServices
import com.appforge.server.services.auth.SessionCookieSpec
import com.appforge.server.services.billing.BillingServices
import com.appforge.server.services.billing.BillingUseCases
import com.appforge.server.services.earlyaccess.EarlyAccessAppService
import com.appforge.server.services.login.LoginService
import com.appforge.server.services.onboarding.OnboardingFlowUseCases
import com.appforge.server.services.onboarding.OnboardingQaService
import com.appforge.server.services.recordings.RecordingContent
import com.appforge.server.services.recordings.RecordingMetadata
import com.appforge.server.services.registration.RegistrationService
import com.appforge.server.services.reviews.ReviewServices
import com.appforge.server.services.reviews.ReviewUseCases
import com.appforge.server.services.sharing.PublicShareServices
import com.appforge.server.services.sharing.PublicShareUseCases
import com.appforge.server.services.sharing.ShareServices
import com.appforge.server.services.sharing.ShareUseCases
import com.appforge.server.services.system.HealthUseCases
import com.appforge.server.services.system.SystemServices
import com.appforge.server.services.system.SystemUseCases
import com.appforge.server.services.uploads.UploadCompletionRequest
import com.appforge.server.services.uploads.UploadCompletionResult
import com.appforge.server.services.uploads.UploadServices
import com.appforge.server.services.uploads.UploadUseCases
import com.appforge.server.services.usage.UsageGranularity
import com.appforge.server.services.usage.UsageMetricsService
import com.appforge.server.services.usage.UsageSummary
import com.appforge.server.services.useraccount.UserAccountService
import com.appforge.server.services.userprofile.UserProfileService
import com.google.firebase.auth.FirebaseToken
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

class CorePublicApiRouteContractIntegrationTest {
    @Test
    fun `all core public endpoints are mounted and callable`() = testApplication {
        val authService = mockk<AuthService>(relaxed = true)
        val token = mockk<FirebaseToken>()
        every { token.uid } returns "user-1"
        every { token.email } returns "user@test.com"
        every { token.name } returns "User"
        every { authService.verifyIdToken("good-token") } returns token
        every { authService.verifySessionCookie("cookie") } returns token
        every { authService.sessionCookieName } returns "session"

        val requestIdentityProvider = ExternalIdentityProvider(authService)

        val loginService = mockk<LoginService>()
        every { loginService.sessionCookieName } returns "session"
        coEvery { loginService.sessionMe("cookie") } returns AuthResponse.Ok(
            SessionMeResponse("user-1", "user@test.com", "User", true)
        )
        coEvery { loginService.sessionLogin(any()) } returns AuthResponse.Ok(
            SessionLoginResponse(success = true, uid = "user-1"),
            SessionCookieSpec("session", "cookie", 3600, false, "Lax"),
        )
        coEvery { loginService.sessionLogout("cookie") } returns AuthResponse.Ok(
            SessionLogoutResponse(success = true, uid = "user-1"),
            SessionCookieSpec("session", "", 0, false, "Lax"),
        )
        coEvery { loginService.sendPasswordResetLink(any()) } returns AuthResponse.Ok(
            PasswordResetLinkResponse(success = true)
        )

        val registrationService = mockk<RegistrationService>()
        coEvery { registrationService.signupInit(any()) } returns AuthResponse.Ok(
            SignupInitResponse(success = true, uid = "user-1")
        )

        val earlyAccessAppService = mockk<EarlyAccessAppService>()
        coEvery { earlyAccessAppService.check(any()) } returns AuthResponse.Ok(EarlyAccessCheckResponse(hasAccess = true))
        coEvery { earlyAccessAppService.join(any()) } returns AuthResponse.Ok(EarlyAccessJoinResponse(success = true))

        val onboardingFlowUseCases = mockk<OnboardingFlowUseCases>()
        coEvery { onboardingFlowUseCases.getActiveFlow() } returns OnboardingFlowResponse(
            id = "flow-1",
            version = 1,
            name = "Flow",
            steps = emptyList(),
        )

        val onboardingQaService = mockk<OnboardingQaService>()
        coEvery { onboardingQaService.submitOnboarding(any(), any()) } returns AuthResponse.Ok(
            OnboardingSubmitResponse(success = true, uid = "user-1")
        )

        val userProfileService = mockk<UserProfileService>()
        coEvery { userProfileService.userProfile("user-1") } returns UserProfileResponse(
            uid = "user-1",
            email = "user@test.com",
            name = "User",
        )

        val userAccountService = mockk<UserAccountService>()
        coEvery { userAccountService.updateUserProfile(any(), any()) } returns AuthResponse.Ok(
            UpdateUserProfileResponse(success = true)
        )
        coEvery { userAccountService.deleteUserAccount(any()) } returns AuthResponse.Ok(
            DeleteUserAccountResponse(success = true)
        )

        val usageMetricsService = mockk<UsageMetricsService>()
        coEvery { usageMetricsService.usageSummary(any(), any(), any(), any()) } returns UsageSummary(
            granularity = UsageGranularity.DAY,
            from = null,
            to = null,
            series = emptyList(),
        )

        val billingUseCases = mockk<BillingUseCases>()
        every { billingUseCases.listPricingCards() } returns PricingCardsResponse(cards = emptyList())
        every { billingUseCases.entitlement("user-1") } returns EntitlementSnapshotResponse(
            userId = "user-1",
            plan = PlanDto.FREE,
            status = BillingStatusDto.ACTIVE,
            source = BillingSourceDto.MANUAL,
            startedAt = ProtoTimestamp(1, 0),
            expiresAt = null,
            updatedAt = ProtoTimestamp(1, 0),
            features = emptyList(),
        )
        every { billingUseCases.checkout(any(), any(), any()) } returns CheckoutResponse("session-1", "https://checkout.local")
        every { billingUseCases.cancelSubscription(any()) } returns Unit
        every { billingUseCases.handleWebhook(any(), any(), any(), any()) } returns WebhookResponse(received = true)

        val uploadUseCases = mockk<UploadUseCases>()
        coEvery { uploadUseCases.initUpload(any(), any()) } returns UploadInitResponse(
            uploadId = "upload-1",
            assetId = "asset-1",
            uploadUrl = "https://upload.local",
            expiresAtTimestamp = 123L,
            accessUrl = "/api/v1/uploads/access/asset-1",
        )
        coEvery { uploadUseCases.getAccessUrl(any(), any()) } returns "https://download.local"
        coEvery { uploadUseCases.completeUpload(any<UploadCompletionRequest>()) } returns UploadCompletionResult(processed = true)

        val reviewUseCases = mockk<ReviewUseCases>()
        coEvery { reviewUseCases.listAllReviews(any()) } returns emptyList()
        coEvery { reviewUseCases.getReviews(any(), any(), any()) } returns emptyList()
        coEvery { reviewUseCases.requestAiReview(any(), any(), any(), any()) } returns Unit

        val shareUseCases = mockk<ShareUseCases>()
        coEvery { shareUseCases.createShare(any(), any(), any(), any()) } returns ShareResponse(
            id = "share-1",
            entityType = "statement",
            entityId = "entity-1",
            shareUrl = "https://host/shares/share-1",
            expiresAt = ProtoTimestamp(123, 0),
        )
        coEvery { shareUseCases.listShares(any(), any(), any()) } returns listOf(
            ShareSummaryResponse(
                id = "share-1",
                entityType = "statement",
                entityId = "entity-1",
                shareUrl = "https://host/shares/share-1",
                expiresAt = ProtoTimestamp(123, 0),
            )
        )
        coEvery { shareUseCases.revokeShare(any(), any()) } returns Unit
        coEvery { shareUseCases.sendShareEmail(any(), any(), any()) } returns Unit

        val publicShareUseCases = mockk<PublicShareUseCases>()
        coEvery { publicShareUseCases.getPublicShare(any()) } returns PublicEntityResponse(
            share = PublicShareMetadata(
                token = "share-1",
                entityType = "statement",
                entityId = "entity-1",
                accessMode = "public",
                expiresAt = ProtoTimestamp(123, 0),
                revokedAt = null,
            ),
            entity = PublicEntity(
                id = "entity-1",
                category = "statement",
                title = "Title",
                content = "content",
            ),
        )
        coEvery { publicShareUseCases.getReviewTemplate(any()) } returns ReviewTemplateResponse(
            reviewForm = ReviewTemplate(
                id = "review-form-1",
                version = 1,
                entityType = "statement",
                name = "Statement Review",
                fields = listOf(
                    ReviewTemplateField(
                        id = "score",
                        label = "Score",
                        type = "text",
                        required = false,
                    )
                ),
            )
        )
        coEvery { publicShareUseCases.submitReview(any(), any()) } returns ReviewResponse(
            id = "review-1",
            authorRole = "external",
            authorId = null,
            authorName = "Reviewer",
            authorEmail = null,
            content = mapOf("score" to "5"),
            createdAtTimestamp = 123L,
        )
        coEvery { publicShareUseCases.getSharedRecordingContent(any()) } returns RecordingContent(
            metadata = RecordingMetadata(
                id = "recording-1",
                uid = "user-1",
                contentType = "audio/mpeg",
                sizeBytes = 10,
                durationSeconds = 1,
                createdAt = Instant.parse("2026-01-01T00:00:00Z"),
            ),
            audioBytes = byteArrayOf(1, 2, 3),
        )

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing {
                healthRoutes(object : SystemServices {
                    override val runtimeOptions = runtimeOptions()
                    override val healthUseCases = object : HealthUseCases {
                        override fun status(): String = "ok"
                    }
                    override val systemUseCases: SystemUseCases = mockk(relaxed = true)
                })
                authRoutes(object : AuthServices {
                    override val authService = authService
                    override val requestIdentityProvider = requestIdentityProvider
                    override val loginService = loginService
                    override val registrationService = registrationService
                    override val onboardingQaService = onboardingQaService
                    override val userProfileService = userProfileService
                    override val userAccountService = userAccountService
                    override val usageMetricsService = usageMetricsService
                    override val earlyAccessAppService = earlyAccessAppService
                    override val onboardingFlowUseCases = onboardingFlowUseCases
                    override val runtimeOptions = runtimeOptions()
                })
                billingRoutes(object : BillingServices {
                    override val authService = authService
                    override val requestIdentityProvider = requestIdentityProvider
                    override val billingUseCases = billingUseCases
                })
                uploadEventRoutes(object : UploadServices {
                    override val authService = authService
                    override val requestIdentityProvider = requestIdentityProvider
                    override val uploadEventSharedSecret = "upload-secret"
                    override val uploadUseCases = uploadUseCases
                })
                uploadRoutes(object : UploadServices {
                    override val authService = authService
                    override val requestIdentityProvider = requestIdentityProvider
                    override val uploadEventSharedSecret = "upload-secret"
                    override val uploadUseCases = uploadUseCases
                })
                reviewRoutes(object : ReviewServices {
                    override val authService = authService
                    override val requestIdentityProvider = requestIdentityProvider
                    override val reviewUseCases = reviewUseCases
                })
                publicShareRoutes(object : PublicShareServices {
                    override val publicShareUseCases = publicShareUseCases
                })
                route("/api/v1/entities/{type}/{id}") {
                    install(com.appforge.server.middleware.UserAuthPlugin) {
                        this.authService = authService
                        this.requestIdentityProvider = requestIdentityProvider
                    }
                    entityReviewRoutes(object : ReviewServices {
                        override val authService = authService
                        override val requestIdentityProvider = requestIdentityProvider
                        override val reviewUseCases = reviewUseCases
                    })
                    entityShareRoutes(object : ShareServices {
                        override val authService = authService
                        override val requestIdentityProvider = requestIdentityProvider
                        override val shareUseCases = shareUseCases
                    })
                }
            }
        }

        val calls = listOf(
            client.get("/health"),
            client.post("/api/v1/session/early-access/check") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"email":"u@test.com"}""")
            },
            client.post("/api/v1/session/early-access/join") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"email":"u@test.com"}""")
            },
            client.get("/api/v1/session/early-access/status"),
            client.get("/api/v1/session/me") {
                header("X-App-Id", "test-app")
                header(HttpHeaders.Cookie, "session=cookie")
            },
            client.post("/api/v1/session/login") {
                header("X-App-Id", "test-app")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"idToken":"good-token"}""")
            },
            client.post("/api/v1/session/logout") {
                header("X-App-Id", "test-app")
                header(HttpHeaders.Cookie, "session=cookie")
            },
            client.post("/api/v1/session/password/reset-link") {
                header("X-App-Id", "test-app")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"email":"u@test.com"}""")
            },
            client.post("/api/v1/signup/init") {
                header("X-App-Id", "test-app")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"idToken":"good-token"}""")
            },
            client.get("/api/v1/onboarding/flow") {
                header("X-App-Id", "test-app")
            },
            client.post("/api/v1/onboarding/submit") {
                header(HttpHeaders.Authorization, "Bearer good-token")
                header("X-App-Id", "test-app")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"answers":[],"completedAt":{"seconds":0,"nanos":0}}""")
            },
            client.get("/api/v1/users/me") {
                header(HttpHeaders.Authorization, "Bearer good-token")
                header("X-App-Id", "test-app")
            },
            client.put("/api/v1/users/me") {
                header(HttpHeaders.Authorization, "Bearer good-token")
                header("X-App-Id", "test-app")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"name":"User"}""")
            },
            client.delete("/api/v1/users/me") {
                header(HttpHeaders.Authorization, "Bearer good-token")
                header("X-App-Id", "test-app")
            },
            client.get("/api/v1/users/me/usage") {
                header(HttpHeaders.Authorization, "Bearer good-token")
                header("X-App-Id", "test-app")
            },
            client.get("/api/v1/billing/pricing-cards"),
            client.get("/api/v1/billing/entitlement") {
                header(HttpHeaders.Authorization, "Bearer good-token")
                header("X-App-Id", "test-app")
            },
            client.post("/api/v1/billing/checkout") {
                header(HttpHeaders.Authorization, "Bearer good-token")
                header("X-App-Id", "test-app")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    """{"customerEmail":"u@test.com","priceId":"price_1","paymentType":"subscription"}"""
                )
            },
            client.post("/api/v1/billing/subscription/cancel") {
                header(HttpHeaders.Authorization, "Bearer good-token")
                header("X-App-Id", "test-app")
            },
            client.post("/api/v1/billing/webhook/dodo") {
                header("webhook-signature", "sig")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("{}")
            },
            client.post("/api/v1/uploads/init") {
                header(HttpHeaders.Authorization, "Bearer good-token")
                header("X-App-Id", "test-app")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"type":"image","entityId":"e1","contentType":"image/png","sizeBytes":10,"assetId":"asset-1"}""")
            },
            client.get("/api/v1/uploads/access/asset-1?redirect=false") {
                header(HttpHeaders.Authorization, "Bearer good-token")
                header("X-App-Id", "test-app")
            },
            client.post("/api/v1/upload-events/complete") {
                header("X-Upload-Event-Secret", "upload-secret")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"bucket":"b","objectName":"o","generation":1,"sizeBytes":10,"contentType":"image/png"}""")
            },
            client.get("/api/v1/reviews") {
                header(HttpHeaders.Authorization, "Bearer good-token")
                header("X-App-Id", "test-app")
            },
            client.get("/api/v1/entities/statement/entity-1/reviews") {
                header(HttpHeaders.Authorization, "Bearer good-token")
                header("X-App-Id", "test-app")
            },
            client.post("/api/v1/entities/statement/entity-1/ai-review") {
                header(HttpHeaders.Authorization, "Bearer good-token")
                header("X-App-Id", "test-app")
            },
            client.post("/api/v1/entities/statement/entity-1/shares") {
                header(HttpHeaders.Authorization, "Bearer good-token")
                header("X-App-Id", "test-app")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"entityType":"statement"}""")
            },
            client.get("/api/v1/entities/statement/entity-1/shares") {
                header(HttpHeaders.Authorization, "Bearer good-token")
                header("X-App-Id", "test-app")
            },
            client.post("/api/v1/entities/statement/entity-1/shares/share-1/revoke") {
                header(HttpHeaders.Authorization, "Bearer good-token")
                header("X-App-Id", "test-app")
            },
            client.post("/api/v1/entities/statement/entity-1/shares/share-1/email") {
                header(HttpHeaders.Authorization, "Bearer good-token")
                header("X-App-Id", "test-app")
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"toEmail":"u@test.com"}""")
            },
            client.get("/shares/share-1"),
            client.get("/reviews/share-1"),
            client.post("/shares/share-1/reviews") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"displayName":"Reviewer","reviewFormId":"review-form-1","reviewFormVersion":1,"answers":[]}""")
            },
            client.post("/reviews/share-1") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody("""{"displayName":"Reviewer","reviewFormId":"review-form-1","reviewFormVersion":1,"answers":[]}""")
            },
            client.get("/shares/share-1/content"),
        )

        calls.forEach { response ->
            assertTrue(response.status != HttpStatusCode.NotFound, "route should not be 404")
            assertTrue(response.status != HttpStatusCode.MethodNotAllowed, "route should not be 405")
        }
    }

    private fun runtimeOptions() = RuntimeOptions(
        appId = "test-app",
        port = 8080,
        host = "localhost",
        corsAllowedOrigins = emptyList(),
        nodeEnv = "test",
        publicBaseUrl = "http://localhost:8080",
        internalSecret = "secret",
        earlyAccessEnabled = false,
        documentMaxContentChars = 20000,
    )
}

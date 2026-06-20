package com.appforge.server.routing

import com.appforge.server.api.ProtoTimestamp
import com.appforge.server.api.reviews.ReviewAnswerRequest
import com.appforge.server.api.reviews.ReviewResponse
import com.appforge.server.api.reviews.ReviewTemplate
import com.appforge.server.api.reviews.ReviewTemplateField
import com.appforge.server.api.reviews.ReviewTemplateResponse
import com.appforge.server.api.sharing.CreateReviewerShareRequest
import com.appforge.server.api.sharing.PublicEntity
import com.appforge.server.api.sharing.ReviewerShareEntityResponse
import com.appforge.server.api.sharing.ReviewerShareResponse
import com.appforge.server.api.sharing.SubmitReviewerShareReviewRequest
import com.appforge.server.middleware.UserAuthPlugin
import com.appforge.server.middleware.configureErrorHandling
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.sharing.ReviewerShareUseCases
import com.appforge.server.services.sharing.ShareServices
import com.appforge.server.services.sharing.ShareUseCases
import com.google.firebase.auth.FirebaseToken
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class ReviewerShareRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `reviewer share routes enforce auth and delegate to use cases`() = testApplication {
        val authService = mockk<AuthService>()
        val ownerToken = mockk<FirebaseToken>()
        val reviewerToken = mockk<FirebaseToken>()
        val reviewerShareUseCases = mockk<ReviewerShareUseCases>()

        every { ownerToken.uid } returns "user-1"
        every { reviewerToken.uid } returns "reviewer-1"
        every { authService.verifyIdToken("owner-token") } returns ownerToken
        every { authService.verifyIdToken("reviewer-token") } returns reviewerToken
        every { authService.verifySessionCookie(any()) } returns null
        every { authService.sessionCookieName } returns "session"

        val shareResponse = ReviewerShareResponse(
            id = "share-1",
            entityType = "document",
            entityId = "doc-1",
            reviewerEmail = "mentor@example.com",
            status = "active",
            createdAt = ProtoTimestamp(seconds = 1704067200, nanos = 0),
            expiresAt = ProtoTimestamp(seconds = 1705881600, nanos = 0),
            ownerUid = "user-1",
            ownerName = "Student",
            ownerEmail = "student@example.com",
        )
        val reviewResponse = ReviewResponse(
            id = "review-1",
            authorRole = "external",
            authorId = null,
            authorName = "Mentor",
            authorEmail = "mentor@example.com",
            content = mapOf("summary" to JsonPrimitive("Looks good")),
            createdAtTimestamp = 1704067200000,
        )
        val shareEntityResponse = ReviewerShareEntityResponse(
            share = shareResponse,
            entity = PublicEntity(
                id = "doc-1",
                category = "document",
                title = "Statement",
                subtitle = null,
                content = "Essay content",
                question = null,
                assetUrl = null,
            ),
        )
        val templateResponse = ReviewTemplateResponse(
            reviewForm = ReviewTemplate(
                id = "document_review_form_v1",
                version = 1,
                entityType = "document",
                name = "Document Review",
                fields = listOf(
                    ReviewTemplateField(
                        id = "summary",
                        label = "Summary",
                        type = "text",
                        required = true,
                    )
                ),
            )
        )

        coEvery {
            reviewerShareUseCases.createReviewerShare("user-1", "document", "doc-1", any())
        } returns shareResponse
        coEvery {
            reviewerShareUseCases.listReviewerSharesForEntity("user-1", "document", "doc-1")
        } returns listOf(shareResponse)
        coEvery { reviewerShareUseCases.revokeReviewerShare("user-1", "share-1") } returns Unit
        coEvery { reviewerShareUseCases.listReviewerInbox("reviewer-1") } returns listOf(shareResponse)
        coEvery { reviewerShareUseCases.getReviewerShare("reviewer-1", "share-1") } returns shareEntityResponse
        coEvery { reviewerShareUseCases.getReviewerShareReviewTemplate("reviewer-1", "share-1") } returns templateResponse
        coEvery {
            reviewerShareUseCases.submitReviewerShareReview("reviewer-1", "share-1", any())
        } returns reviewResponse

        val services = object : ShareServices {
            override val authService = authService
            override val requestIdentityProvider = ExternalIdentityProvider(authService)
            override val shareUseCases: ShareUseCases = mockk(relaxed = true)
            override val reviewerShareUseCases = reviewerShareUseCases
        }

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing {
                route("/api/v1/entities/{type}/{id}") {
                    install(UserAuthPlugin) {
                        this.authService = services.authService
                        this.requestIdentityProvider = services.requestIdentityProvider
                    }
                    entityReviewerShareRoutes(services)
                }
                route("/api/v1/reviewer-shares") {
                    install(UserAuthPlugin) {
                        this.authService = services.authService
                        this.requestIdentityProvider = services.requestIdentityProvider
                    }
                    reviewerShareManagementRoutes(services)
                }
                route("/api/v1/reviewer") {
                    install(UserAuthPlugin) {
                        this.authService = services.authService
                        this.requestIdentityProvider = services.requestIdentityProvider
                    }
                    reviewerInboxRoutes(services)
                }
            }
        }

        val createResponse = client.post("/api/v1/entities/document/doc-1/reviewer-shares") {
            header(HttpHeaders.Authorization, "Bearer owner-token")
            header("X-App-Id", "test-app")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(CreateReviewerShareRequest(reviewerEmail = "mentor@example.com")))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)

        val listResponse = client.get("/api/v1/entities/document/doc-1/reviewer-shares") {
            header(HttpHeaders.Authorization, "Bearer owner-token")
            header("X-App-Id", "test-app")
        }
        assertEquals(HttpStatusCode.OK, listResponse.status)

        val revokeResponse = client.post("/api/v1/reviewer-shares/share-1/revoke") {
            header(HttpHeaders.Authorization, "Bearer owner-token")
            header("X-App-Id", "test-app")
        }
        assertEquals(HttpStatusCode.NoContent, revokeResponse.status)

        val inboxResponse = client.get("/api/v1/reviewer/shares") {
            header(HttpHeaders.Authorization, "Bearer reviewer-token")
            header("X-App-Id", "test-app")
        }
        assertEquals(HttpStatusCode.OK, inboxResponse.status)

        val detailResponse = client.get("/api/v1/reviewer/shares/share-1") {
            header(HttpHeaders.Authorization, "Bearer reviewer-token")
            header("X-App-Id", "test-app")
        }
        assertEquals(HttpStatusCode.OK, detailResponse.status)

        val templateResult = client.get("/api/v1/reviewer/shares/share-1/review-template") {
            header(HttpHeaders.Authorization, "Bearer reviewer-token")
            header("X-App-Id", "test-app")
        }
        assertEquals(HttpStatusCode.OK, templateResult.status)

        val reviewResult = client.post("/api/v1/reviewer/shares/share-1/reviews") {
            header(HttpHeaders.Authorization, "Bearer reviewer-token")
            header("X-App-Id", "test-app")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                json.encodeToString(
                    SubmitReviewerShareReviewRequest(
                        reviewFormId = "document_review_form_v1",
                        reviewFormVersion = 1,
                        answers = listOf(
                            ReviewAnswerRequest(fieldId = "summary", textValue = "Looks good")
                        ),
                    )
                )
            )
        }
        assertEquals(HttpStatusCode.Created, reviewResult.status)

        coVerify(exactly = 1) {
            reviewerShareUseCases.createReviewerShare(
                "user-1",
                "document",
                "doc-1",
                CreateReviewerShareRequest(reviewerEmail = "mentor@example.com"),
            )
        }
        coVerify(exactly = 1) { reviewerShareUseCases.revokeReviewerShare("user-1", "share-1") }
        coVerify(exactly = 1) { reviewerShareUseCases.listReviewerInbox("reviewer-1") }
        coVerify(exactly = 1) { reviewerShareUseCases.getReviewerShare("reviewer-1", "share-1") }
        coVerify(exactly = 1) { reviewerShareUseCases.getReviewerShareReviewTemplate("reviewer-1", "share-1") }
        coVerify(exactly = 1) {
            reviewerShareUseCases.submitReviewerShareReview(
                "reviewer-1",
                "share-1",
                SubmitReviewerShareReviewRequest(
                    reviewFormId = "document_review_form_v1",
                    reviewFormVersion = 1,
                    answers = listOf(ReviewAnswerRequest(fieldId = "summary", textValue = "Looks good")),
                ),
            )
        }
    }
}

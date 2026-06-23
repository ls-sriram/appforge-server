package com.appforge.server.routing

import com.appforge.server.api.ProtoTimestamp
import com.appforge.server.api.reviews.ReviewAnswerRequest
import com.appforge.server.api.reviews.ReviewResponse
import com.appforge.server.api.reviews.ReviewTemplate
import com.appforge.server.api.reviews.ReviewTemplateField
import com.appforge.server.api.reviews.ReviewTemplateResponse
import com.appforge.server.api.sharing.CollaboratorShareEntityResponse
import com.appforge.server.api.sharing.CollaboratorShareResponse
import com.appforge.server.api.sharing.CreateCollaboratorShareRequest
import com.appforge.server.api.sharing.PublicEntity
import com.appforge.server.api.sharing.SubmitCollaboratorReviewRequest
import com.appforge.server.middleware.UserAuthPlugin
import com.appforge.server.middleware.configureErrorHandling
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.sharing.CollaboratorShareUseCases
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

class CollaboratorShareRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `collaborator share routes enforce auth and delegate to use cases`() = testApplication {
        val authService = mockk<AuthService>()
        val ownerToken = mockk<FirebaseToken>()
        val collaboratorToken = mockk<FirebaseToken>()
        val collaboratorShareUseCases = mockk<CollaboratorShareUseCases>()

        every { ownerToken.uid } returns "user-1"
        every { collaboratorToken.uid } returns "collaborator-1"
        every { authService.verifyIdToken("owner-token") } returns ownerToken
        every { authService.verifyIdToken("collaborator-token") } returns collaboratorToken
        every { authService.verifySessionCookie(any()) } returns null
        every { authService.sessionCookieName } returns "session"

        val shareResponse = CollaboratorShareResponse(
            id = "share-1",
            entityType = "document",
            entityId = "doc-1",
            collaboratorEmail = "mentor@example.com",
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
        val shareEntityResponse = CollaboratorShareEntityResponse(
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
            collaboratorShareUseCases.createCollaboratorShare("user-1", "document", "doc-1", any())
        } returns shareResponse
        coEvery {
            collaboratorShareUseCases.listCollaboratorSharesForEntity("user-1", "document", "doc-1")
        } returns listOf(shareResponse)
        coEvery { collaboratorShareUseCases.revokeCollaboratorShare("user-1", "share-1") } returns Unit
        coEvery { collaboratorShareUseCases.listCollaboratorInbox("collaborator-1") } returns listOf(shareResponse)
        coEvery { collaboratorShareUseCases.getCollaboratorShare("collaborator-1", "share-1") } returns shareEntityResponse
        coEvery { collaboratorShareUseCases.getCollaboratorReviewTemplate("collaborator-1", "share-1") } returns templateResponse
        coEvery {
            collaboratorShareUseCases.submitCollaboratorReview("collaborator-1", "share-1", any())
        } returns reviewResponse

        val services = object : ShareServices {
            override val authService = authService
            override val requestIdentityProvider = ExternalIdentityProvider(authService)
            override val shareUseCases: ShareUseCases = mockk(relaxed = true)
            override val collaboratorShareUseCases = collaboratorShareUseCases
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
                    entityCollaboratorShareRoutes(services)
                }
                route("/api/v1/collaborator-shares") {
                    install(UserAuthPlugin) {
                        this.authService = services.authService
                        this.requestIdentityProvider = services.requestIdentityProvider
                    }
                    collaboratorShareManagementRoutes(services)
                }
                route("/api/v1/collaborator") {
                    install(UserAuthPlugin) {
                        this.authService = services.authService
                        this.requestIdentityProvider = services.requestIdentityProvider
                    }
                    collaboratorInboxRoutes(services)
                }
            }
        }

        val createResponse = client.post("/api/v1/entities/document/doc-1/collaborator-shares") {
            header(HttpHeaders.Authorization, "Bearer owner-token")
            header("X-App-Id", "test-app")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(CreateCollaboratorShareRequest(collaboratorEmail = "mentor@example.com")))
        }
        assertEquals(HttpStatusCode.Created, createResponse.status)

        val listResponse = client.get("/api/v1/entities/document/doc-1/collaborator-shares") {
            header(HttpHeaders.Authorization, "Bearer owner-token")
            header("X-App-Id", "test-app")
        }
        assertEquals(HttpStatusCode.OK, listResponse.status)

        val revokeResponse = client.post("/api/v1/collaborator-shares/share-1/revoke") {
            header(HttpHeaders.Authorization, "Bearer owner-token")
            header("X-App-Id", "test-app")
        }
        assertEquals(HttpStatusCode.NoContent, revokeResponse.status)

        val inboxResponse = client.get("/api/v1/collaborator/shares") {
            header(HttpHeaders.Authorization, "Bearer collaborator-token")
            header("X-App-Id", "test-app")
        }
        assertEquals(HttpStatusCode.OK, inboxResponse.status)

        val detailResponse = client.get("/api/v1/collaborator/shares/share-1") {
            header(HttpHeaders.Authorization, "Bearer collaborator-token")
            header("X-App-Id", "test-app")
        }
        assertEquals(HttpStatusCode.OK, detailResponse.status)

        val templateResult = client.get("/api/v1/collaborator/shares/share-1/review-template") {
            header(HttpHeaders.Authorization, "Bearer collaborator-token")
            header("X-App-Id", "test-app")
        }
        assertEquals(HttpStatusCode.OK, templateResult.status)

        val reviewResult = client.post("/api/v1/collaborator/shares/share-1/reviews") {
            header(HttpHeaders.Authorization, "Bearer collaborator-token")
            header("X-App-Id", "test-app")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                json.encodeToString(
                    SubmitCollaboratorReviewRequest(
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
            collaboratorShareUseCases.createCollaboratorShare(
                "user-1",
                "document",
                "doc-1",
                CreateCollaboratorShareRequest(collaboratorEmail = "mentor@example.com"),
            )
        }
        coVerify(exactly = 1) { collaboratorShareUseCases.revokeCollaboratorShare("user-1", "share-1") }
        coVerify(exactly = 1) { collaboratorShareUseCases.listCollaboratorInbox("collaborator-1") }
        coVerify(exactly = 1) { collaboratorShareUseCases.getCollaboratorShare("collaborator-1", "share-1") }
        coVerify(exactly = 1) { collaboratorShareUseCases.getCollaboratorReviewTemplate("collaborator-1", "share-1") }
        coVerify(exactly = 1) {
            collaboratorShareUseCases.submitCollaboratorReview(
                "collaborator-1",
                "share-1",
                SubmitCollaboratorReviewRequest(
                    reviewFormId = "document_review_form_v1",
                    reviewFormVersion = 1,
                    answers = listOf(ReviewAnswerRequest(fieldId = "summary", textValue = "Looks good")),
                ),
            )
        }
    }
}

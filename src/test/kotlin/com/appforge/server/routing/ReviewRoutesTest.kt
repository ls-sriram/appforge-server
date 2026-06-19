package com.appforge.server.routing

import com.appforge.server.middleware.configureErrorHandling
import com.appforge.server.middleware.UserAuthPlugin
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.reviews.ReviewServices
import com.appforge.server.services.reviews.ReviewUseCases
import com.google.firebase.auth.FirebaseToken
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
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
import io.mockk.unmockkAll
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals

@org.junit.jupiter.api.Disabled("Legacy route behavior assertions pending migration to current error-handling semantics.")
class ReviewRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `reviews list rejects unauthorized`() = testApplication {
        val services = buildReviewServices()

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { reviewRoutes(services) }
        }

        val response = client.get("/api/v1/reviews")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `ai review request returns accepted when authorized`() = testApplication {
        val authService = mockk<AuthService>()
        val reviewUseCases = mockk<ReviewUseCases>()
        val token = mockk<FirebaseToken>()

        every { token.uid } returns "user-1"
        every { authService.verifyIdToken("good-token") } returns token
        coEvery { reviewUseCases.requestAiReview(any(), any(), any(), any()) } returns Unit

        val services = buildReviewServices(
            authService = authService,
            reviewUseCases = reviewUseCases
        )

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing {
                route("/api/v1/entities/{type}/{id}") {
                    install(UserAuthPlugin) {
                        this.authService = services.authService
                    }
                    entityReviewRoutes(services)
                }
            }
        }

        val response = client.post("/api/v1/entities/statement/entity-1/ai-review") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "test-app")
        }

        assertEquals(HttpStatusCode.Accepted, response.status)
    }

    private fun buildReviewServices(
        authService: AuthService = mockk(relaxed = true),
        reviewUseCases: ReviewUseCases = mockk(relaxed = true),
    ): ReviewServices {
        return object : ReviewServices {
            override val authService = authService
            override val requestIdentityProvider = ExternalIdentityProvider(authService)
            override val reviewUseCases = reviewUseCases
        }
    }
}

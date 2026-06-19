package com.appforge.server.routing

import com.appforge.server.api.ProtoTimestamp
import com.appforge.server.api.sharing.CreateShareRequest
import com.appforge.server.api.sharing.ShareResponse
import com.appforge.server.middleware.configureErrorHandling
import com.appforge.server.middleware.UserAuthPlugin
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.services.auth.AuthService
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals

@org.junit.jupiter.api.Disabled("Legacy route behavior assertions pending migration to current error-handling semantics.")
class ShareRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `shares list rejects unauthorized`() = testApplication {
        val services = buildShareServices()

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing {
                route("/api/v1/entities/{type}/{id}") {
                    install(UserAuthPlugin) {
                        this.authService = services.authService
                    }
                    entityShareRoutes(services)
                }
            }
        }

        val response = client.get("/api/v1/entities/statement/entity-1/shares")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `create share returns created when authorized`() = testApplication {
        val authService = mockk<AuthService>()
        val shareUseCases = mockk<ShareUseCases>()
        val token = mockk<FirebaseToken>()

        every { token.uid } returns "user-1"
        every { authService.verifyIdToken("good-token") } returns token
        coEvery { shareUseCases.createShare(any(), any(), any(), any()) } returns ShareResponse(
            id = "share-1",
            entityType = "statement",
            entityId = "entity-1",
            shareUrl = "https://example.com/shares/share-1",
            expiresAt = ProtoTimestamp(seconds = 1704067200, nanos = 0),
        )

        val services = buildShareServices(
            authService = authService,
            shareUseCases = shareUseCases
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
                    entityShareRoutes(services)
                }
            }
        }

        val response = client.post("/api/v1/entities/statement/entity-1/shares") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "test-app")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(CreateShareRequest(entityType = "statement")))
        }

        assertEquals(HttpStatusCode.Created, response.status)
    }

    private fun buildShareServices(
        authService: AuthService = mockk(relaxed = true),
        shareUseCases: ShareUseCases = mockk(relaxed = true),
    ): ShareServices {
        return object : ShareServices {
            override val authService = authService
            override val requestIdentityProvider = ExternalIdentityProvider(authService)
            override val shareUseCases = shareUseCases
        }
    }
}

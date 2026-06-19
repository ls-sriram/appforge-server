package com.appforge.server.routing

import com.appforge.server.api.ProtoTimestamp
import com.appforge.server.api.sharing.ShareSummaryResponse
import com.appforge.server.middleware.UserAuthPlugin
import com.appforge.server.middleware.configureErrorHandling
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.sharing.ShareServices
import com.appforge.server.services.sharing.ShareUseCases
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
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals

class EntityShareCollectionRoutesTest {
    @Test
    fun `owner shares list and revoke work with auth`() = testApplication {
        val authService = mockk<AuthService>()
        val token = mockk<FirebaseToken>()
        val useCases = mockk<ShareUseCases>(relaxed = true)
        every { token.uid } returns "user-1"
        every { authService.verifyIdToken("good-token") } returns token
        coEvery { useCases.listOwnerShares("user-1") } returns listOf(
            ShareSummaryResponse(
                id = "share-1",
                entityType = "recording",
                entityId = "rec-1",
                shareUrl = "https://example.com/web/shares/t1",
                expiresAt = ProtoTimestamp(seconds = 1790000000, nanos = 0),
                revokedAt = null,
            )
        )
        coEvery { useCases.revokeShare("user-1", "t1") } returns Unit

        val services = buildShareServices(authService, useCases)
        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing {
                route("/api/v1/entities") {
                    install(UserAuthPlugin) {
                        this.authService = services.authService
                        this.requestIdentityProvider = services.requestIdentityProvider
                    }
                    entityShareCollectionRoutes(services)
                }
            }
        }

        val listResponse = client.get("/api/v1/entities/shares") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "frontend-client")
        }
        assertEquals(HttpStatusCode.OK, listResponse.status)

        val revokeResponse = client.post("/api/v1/entities/shares/t1/revoke") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "frontend-client")
        }
        assertEquals(HttpStatusCode.NoContent, revokeResponse.status)
        coVerify(exactly = 1) { useCases.revokeShare("user-1", "t1") }
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

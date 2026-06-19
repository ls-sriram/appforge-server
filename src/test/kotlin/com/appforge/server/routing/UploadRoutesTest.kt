package com.appforge.server.routing

import com.appforge.server.api.UploadInitRequest
import com.appforge.server.api.UploadTypeDto
import com.appforge.server.services.uploads.UploadCompletionResult

import com.appforge.server.middleware.configureErrorHandling
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.uploads.UploadUseCases
import com.appforge.server.services.uploads.UploadServices
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
import kotlin.test.assertFailsWith

class UploadRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `upload init rejects unauthorized`() = testApplication {
        val services = buildUploadServices()

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing {
                uploadEventRoutes(services)
                uploadRoutes(services)
            }
        }

        assertFailsWith<com.appforge.server.middleware.UnauthorizedException> {
            client.post("/api/v1/uploads/init") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(json.encodeToString(sampleUploadRequest()))
            }
        }
    }

    @Test
    fun `upload init returns ok when authorized`() = testApplication {
        val authService = mockk<AuthService>()
        val uploadUseCases = mockk<UploadUseCases>()
        val token = mockk<FirebaseToken>()

        every { token.uid } returns "user-1"
        every { authService.verifyIdToken("good-token") } returns token
        coEvery {
            uploadUseCases.initUpload(any(), any())
        } returns com.appforge.server.api.UploadInitResponse(
            uploadId = "upload-1",
            assetId = "asset-1",
            uploadUrl = "http://upload",
            expiresAtTimestamp = 123,
            accessUrl = "http://access"
        )

        val services = buildUploadServices(
            authService = authService,
            uploadUseCases = uploadUseCases
        )

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing {
                uploadEventRoutes(services)
                uploadRoutes(services)
            }
        }

        val response = client.post("/api/v1/uploads/init") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "test-app")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(sampleUploadRequest()))
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `upload access returns not found when missing`() = testApplication {
        val authService = mockk<AuthService>()
        val uploadUseCases = mockk<UploadUseCases>()
        val token = mockk<FirebaseToken>()

        every { token.uid } returns "user-1"
        every { authService.verifyIdToken("good-token") } returns token
        coEvery { uploadUseCases.getAccessUrl("user-1", "asset-1") } returns null

        val services = buildUploadServices(
            authService = authService,
            uploadUseCases = uploadUseCases
        )

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing {
                uploadEventRoutes(services)
                uploadRoutes(services)
            }
        }

        val response = client.get("/api/v1/uploads/access/asset-1?redirect=false") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "test-app")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `upload access rejects invalid redirect query value`() = testApplication {
        val authService = mockk<AuthService>()
        val uploadUseCases = mockk<UploadUseCases>()
        val token = mockk<FirebaseToken>()

        every { token.uid } returns "user-1"
        every { authService.verifyIdToken("good-token") } returns token

        val services = buildUploadServices(
            authService = authService,
            uploadUseCases = uploadUseCases
        )

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing {
                uploadEventRoutes(services)
                uploadRoutes(services)
            }
        }

        val response = client.get("/api/v1/uploads/access/asset-1?redirect=maybe") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "test-app")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `upload complete rejects missing secret`() = testApplication {
        val services = buildUploadServices()

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing {
                uploadEventRoutes(services)
                uploadRoutes(services)
            }
        }

        val response = client.post("/api/v1/upload-events/complete") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """
                {
                  "bucket":"b",
                  "objectName":"o",
                  "generation":1,
                  "sizeBytes":10,
                  "contentType":"image/png"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `upload complete returns ok with secret`() = testApplication {
        val uploadUseCases = mockk<UploadUseCases>()
        coEvery {
            uploadUseCases.completeUpload(any())
        } returns UploadCompletionResult(processed = true)
        val services = buildUploadServices(uploadUseCases = uploadUseCases)

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing {
                uploadEventRoutes(services)
                uploadRoutes(services)
            }
        }

        val response = client.post("/api/v1/upload-events/complete") {
            header("X-Upload-Event-Secret", "test-upload-secret")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(
                """
                {
                  "bucket":"b",
                  "objectName":"o",
                  "generation":1,
                  "sizeBytes":10,
                  "contentType":"image/png"
                }
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    private fun sampleUploadRequest(): UploadInitRequest {
        return UploadInitRequest(
            type = UploadTypeDto.IMAGE,
            entityId = "entity-1",
            contentType = "image/png",
            sizeBytes = 10,
            assetId = "asset-1"
        )
    }

    private fun buildUploadServices(
        authService: AuthService = mockk(relaxed = true),
        uploadUseCases: UploadUseCases = mockk(relaxed = true),
    ): UploadServices {
        return object : UploadServices {
            override val authService = authService
            override val requestIdentityProvider = ExternalIdentityProvider(authService)
            override val uploadEventSharedSecret = "test-upload-secret"
            override val uploadUseCases = uploadUseCases
        }
    }
}

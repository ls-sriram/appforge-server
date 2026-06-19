package com.appforge.server.routing

import com.appforge.server.api.RecordingCreateRequest
import com.appforge.server.api.RecordingListResponse
import com.appforge.server.api.RecordingResponse
import com.appforge.server.api.ProtoTimestamp
import com.appforge.server.middleware.configureErrorHandling
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.auth.AuthResponse
import com.appforge.server.services.recordings.RecordingContent
import com.appforge.server.services.recordings.RecordingMetadata
import com.appforge.server.services.recordings.RecordingServices
import com.appforge.server.services.recordings.RecordingService
import com.google.firebase.auth.FirebaseToken
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
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
import java.time.Instant
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RecordingRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `recording create rejects unauthorized`() = testApplication {
        val services = buildRecordingServices()
        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { recordingRoutes(services) }
        }
        assertFailsWith<com.appforge.server.middleware.UnauthorizedException> {
            client.post("/api/v1/recordings") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(
                    json.encodeToString(
                        RecordingCreateRequest(
                            audioBase64 = "YQ==",
                            contentType = "audio/webm",
                            durationSeconds = 10
                        )
                    )
                )
            }
        }
    }

    @Test
    fun `recording list returns ok when authorized`() = testApplication {
        val authService = mockk<AuthService>()
        val useCases = mockk<RecordingService>()
        val token = mockk<FirebaseToken>()
        every { token.uid } returns "user-1"
        every { authService.verifyIdToken("good-token") } returns token
        coEvery { useCases.list("user-1", any()) } returns AuthResponse.Ok(
            RecordingListResponse(
                recordings = listOf(
                    RecordingResponse(
                        id = "r1",
                        createdAt = ProtoTimestamp(seconds = 1, nanos = 0),
                        durationSeconds = 12,
                        contentType = "audio/webm",
                        sizeBytes = 100
                    )
                )
            )
        )
        val services = buildRecordingServices(authService = authService, useCases = useCases)
        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { recordingRoutes(services) }
        }
        val response = client.get("/api/v1/recordings") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "test-app")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.decodeFromString<RecordingListResponse>(response.bodyAsText())
        assertEquals(1, payload.recordings.size)
        assertEquals("r1", payload.recordings.first().id)
    }

    @Test
    fun `recording content returns not found when denied`() = testApplication {
        val authService = mockk<AuthService>()
        val useCases = mockk<RecordingService>()
        val token = mockk<FirebaseToken>()
        every { token.uid } returns "user-a"
        every { authService.verifyIdToken("good-token") } returns token
        coEvery { useCases.content("user-a", "r-b") } returns AuthResponse.Forbidden("Recording not found.")
        val services = buildRecordingServices(authService = authService, useCases = useCases)
        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { recordingRoutes(services) }
        }
        val response = client.get("/api/v1/recordings/r-b/content") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "test-app")
        }
        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    @Test
    fun `recording content returns bytes when authorized`() = testApplication {
        val authService = mockk<AuthService>()
        val useCases = mockk<RecordingService>()
        val token = mockk<FirebaseToken>()
        every { token.uid } returns "user-1"
        every { authService.verifyIdToken("good-token") } returns token
        val bytes = "audio".toByteArray()
        coEvery { useCases.content("user-1", "r1") } returns AuthResponse.Ok(
            RecordingContent(
                metadata = RecordingMetadata(
                    id = "r1",
                    uid = "user-1",
                    contentType = "audio/webm",
                    sizeBytes = bytes.size.toLong(),
                    durationSeconds = 5,
                    createdAt = Instant.ofEpochSecond(1),
                ),
                audioBytes = bytes,
            )
        )
        val services = buildRecordingServices(authService = authService, useCases = useCases)
        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { recordingRoutes(services) }
        }
        val response = client.get("/api/v1/recordings/r1/content") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "test-app")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("audio", response.bodyAsText())
    }

    private fun buildRecordingServices(
        authService: AuthService = mockk(relaxed = true),
        useCases: RecordingService = mockk(relaxed = true),
    ): RecordingServices {
        return object : RecordingServices {
            override val authService = authService
            override val requestIdentityProvider = ExternalIdentityProvider(authService)
            override val recordingService = useCases
        }
    }
}

package com.appforge.server.routing

import com.appforge.server.api.CollectionCreateRequest
import com.appforge.server.api.CollectionDeleteResponse
import com.appforge.server.api.CollectionListResponse
import com.appforge.server.api.CollectionRecord
import com.appforge.server.api.CollectionUpdateRequest
import com.appforge.server.api.ProtoTimestamp
import com.appforge.server.middleware.configureErrorHandling
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.services.auth.AuthResponse
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.collections.CollectionService
import com.appforge.server.services.collections.CollectionServices
import com.google.firebase.auth.FirebaseToken
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CollectionRoutesTest {

    private val json = Json { ignoreUnknownKeys = true }

    @AfterEach
    fun teardown() { unmockkAll() }

    // ─── Auth ─────────────────────────────────────────────────────────────

    @Test
    fun `create rejects unauthenticated request`() = testApplication {
        val services = buildServices()
        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { collectionRoutes(services) }
        }
        assertFailsWith<com.appforge.server.middleware.UnauthorizedException> {
            client.post("/api/v1/collections/expenses") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(json.encodeToString(CollectionCreateRequest(data = buildJsonObject { put("amount", 10) })))
            }
        }
    }

    @Test
    fun `list rejects unauthenticated request`() = testApplication {
        val services = buildServices()
        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { collectionRoutes(services) }
        }
        assertFailsWith<com.appforge.server.middleware.UnauthorizedException> {
            client.get("/api/v1/collections/expenses")
        }
    }

    // ─── Create ───────────────────────────────────────────────────────────

    @Test
    fun `create returns 201 with the new record`() = testApplication {
        val authService = mockk<AuthService>()
        val service = mockk<CollectionService>()
        val token = mockk<FirebaseToken>()
        every { token.uid } returns "u1"
        every { authService.verifyIdToken("good-token") } returns token
        coEvery { service.create("u1", "test-app", "expenses", any()) } returns AuthResponse.Ok(
            stubRecord(id = "r1", collection = "expenses")
        )

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { collectionRoutes(buildServices(authService, service)) }
        }
        val response = client.post("/api/v1/collections/expenses") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "test-app")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(CollectionCreateRequest(data = buildJsonObject { put("amount", 10) })))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val body = json.decodeFromString<CollectionRecord>(response.bodyAsText())
        assertEquals("r1", body.id)
        assertEquals("expenses", body.collection)
    }

    @Test
    fun `create returns 400 on invalid collection name`() = testApplication {
        val authService = mockk<AuthService>()
        val service = mockk<CollectionService>()
        val token = mockk<FirebaseToken>()
        every { token.uid } returns "u1"
        every { authService.verifyIdToken("good-token") } returns token
        coEvery { service.create(any(), any(), any(), any()) } returns
            AuthResponse.BadRequest("collection name must be 1–64 characters: letters, digits, hyphens, underscores only.")

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { collectionRoutes(buildServices(authService, service)) }
        }
        val response = client.post("/api/v1/collections/bad%20name") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "test-app")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(CollectionCreateRequest(data = buildJsonObject { })))
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // ─── List ─────────────────────────────────────────────────────────────

    @Test
    fun `list returns 200 with records for the authenticated user`() = testApplication {
        val authService = mockk<AuthService>()
        val service = mockk<CollectionService>()
        val token = mockk<FirebaseToken>()
        every { token.uid } returns "u1"
        every { authService.verifyIdToken("good-token") } returns token
        coEvery { service.list("u1", "test-app", "expenses", any()) } returns AuthResponse.Ok(
            CollectionListResponse(
                records = listOf(stubRecord("r1", "expenses"), stubRecord("r2", "expenses")),
                total = 2,
            )
        )

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { collectionRoutes(buildServices(authService, service)) }
        }
        val response = client.get("/api/v1/collections/expenses") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "test-app")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<CollectionListResponse>(response.bodyAsText())
        assertEquals(2, body.total)
        assertEquals(2, body.records.size)
    }

    // ─── Get one ──────────────────────────────────────────────────────────

    @Test
    fun `get returns 200 when record exists`() = testApplication {
        val authService = mockk<AuthService>()
        val service = mockk<CollectionService>()
        val token = mockk<FirebaseToken>()
        every { token.uid } returns "u1"
        every { authService.verifyIdToken("good-token") } returns token
        coEvery { service.get("u1", "test-app", "expenses", "r1") } returns AuthResponse.Ok(
            stubRecord("r1", "expenses")
        )

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { collectionRoutes(buildServices(authService, service)) }
        }
        val response = client.get("/api/v1/collections/expenses/r1") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "test-app")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<CollectionRecord>(response.bodyAsText())
        assertEquals("r1", body.id)
    }

    @Test
    fun `get returns 404 when record does not exist`() = testApplication {
        val authService = mockk<AuthService>()
        val service = mockk<CollectionService>()
        val token = mockk<FirebaseToken>()
        every { token.uid } returns "u1"
        every { authService.verifyIdToken("good-token") } returns token
        coEvery { service.get("u1", "test-app", "expenses", "missing") } returns
            AuthResponse.Forbidden("Record not found.")

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { collectionRoutes(buildServices(authService, service)) }
        }
        val response = client.get("/api/v1/collections/expenses/missing") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "test-app")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // ─── Update ───────────────────────────────────────────────────────────

    @Test
    fun `patch returns 200 with updated record`() = testApplication {
        val authService = mockk<AuthService>()
        val service = mockk<CollectionService>()
        val token = mockk<FirebaseToken>()
        every { token.uid } returns "u1"
        every { authService.verifyIdToken("good-token") } returns token
        coEvery { service.update("u1", "test-app", "expenses", "r1", any()) } returns AuthResponse.Ok(
            stubRecord("r1", "expenses")
        )

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { collectionRoutes(buildServices(authService, service)) }
        }
        val response = client.patch("/api/v1/collections/expenses/r1") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "test-app")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(CollectionUpdateRequest(data = buildJsonObject { put("amount", 99) })))
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    // ─── Delete ───────────────────────────────────────────────────────────

    @Test
    fun `delete returns 200 on success`() = testApplication {
        val authService = mockk<AuthService>()
        val service = mockk<CollectionService>()
        val token = mockk<FirebaseToken>()
        every { token.uid } returns "u1"
        every { authService.verifyIdToken("good-token") } returns token
        coEvery { service.delete("u1", "test-app", "expenses", "r1") } returns
            AuthResponse.Ok(CollectionDeleteResponse(success = true))

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { collectionRoutes(buildServices(authService, service)) }
        }
        val response = client.delete("/api/v1/collections/expenses/r1") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "test-app")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = json.decodeFromString<CollectionDeleteResponse>(response.bodyAsText())
        assertEquals(true, body.success)
    }

    @Test
    fun `delete returns 404 when record does not exist`() = testApplication {
        val authService = mockk<AuthService>()
        val service = mockk<CollectionService>()
        val token = mockk<FirebaseToken>()
        every { token.uid } returns "u1"
        every { authService.verifyIdToken("good-token") } returns token
        coEvery { service.delete("u1", "test-app", "expenses", "ghost") } returns
            AuthResponse.Forbidden("Record not found.")

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { collectionRoutes(buildServices(authService, service)) }
        }
        val response = client.delete("/api/v1/collections/expenses/ghost") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "test-app")
        }

        assertEquals(HttpStatusCode.NotFound, response.status)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private fun buildServices(
        authService: AuthService = mockk(relaxed = true),
        service: CollectionService = mockk(relaxed = true),
    ): CollectionServices = object : CollectionServices {
        override val authService = authService
        override val requestIdentityProvider = ExternalIdentityProvider(authService)
        override val collectionService = service
    }

    private fun stubRecord(id: String, collection: String) = CollectionRecord(
        id = id,
        collection = collection,
        data = buildJsonObject { put("stub", true) },
        createdAt = ProtoTimestamp(1_000_000L, 0),
        updatedAt = ProtoTimestamp(1_000_000L, 0),
    )
}

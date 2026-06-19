package com.appforge.server.routing

import com.appforge.server.api.TaskCreateRequest
import com.appforge.server.api.TaskListResponse
import com.appforge.server.api.TaskResponse
import com.appforge.server.middleware.configureErrorHandling
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.auth.AuthResponse
import com.appforge.server.services.tasks.TaskService
import com.appforge.server.services.tasks.TaskServices
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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TaskRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `task create rejects unauthorized`() = testApplication {
        val services = buildTaskServices()
        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { taskRoutes(services) }
        }
        assertFailsWith<com.appforge.server.middleware.UnauthorizedException> {
            client.post("/api/v1/tasks") {
                header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
                setBody(json.encodeToString(TaskCreateRequest(type = "general", title = "Task")))
            }
        }
    }

    @Test
    fun `task list returns ok when authorized`() = testApplication {
        val authService = mockk<AuthService>()
        val service = mockk<TaskService>()
        val token = mockk<FirebaseToken>()
        every { token.uid } returns "user-1"
        every { authService.verifyIdToken("good-token") } returns token
        coEvery { service.list("user-1", any(), any(), any(), any()) } returns AuthResponse.Ok(
            TaskListResponse(
                tasks = listOf(
                    TaskResponse(
                        id = "t1",
                        type = "general",
                        title = "Do thing",
                        status = "open",
                        createdAt = com.appforge.server.api.ProtoTimestamp(1, 0),
                        updatedAt = com.appforge.server.api.ProtoTimestamp(1, 0),
                    )
                )
            )
        )

        val services = buildTaskServices(authService = authService, service = service)
        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { taskRoutes(services) }
        }
        val response = client.get("/api/v1/tasks") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "test-app")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        val payload = json.decodeFromString<TaskListResponse>(response.bodyAsText())
        assertEquals(1, payload.tasks.size)
        assertEquals("t1", payload.tasks.first().id)
    }

    private fun buildTaskServices(
        authService: AuthService = mockk(relaxed = true),
        service: TaskService = mockk(relaxed = true),
    ): TaskServices {
        return object : TaskServices {
            override val authService = authService
            override val requestIdentityProvider = ExternalIdentityProvider(authService)
            override val taskService = service
        }
    }
}

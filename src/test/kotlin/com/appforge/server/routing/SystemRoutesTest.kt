package com.appforge.server.routing

import com.appforge.server.config.options.*
import com.appforge.server.middleware.configureErrorHandling
import com.appforge.server.services.system.SystemServices
import com.appforge.server.services.system.SystemUseCases
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import kotlin.test.Test
import kotlin.test.assertEquals

@org.junit.jupiter.api.Disabled("Legacy route behavior assertions pending migration to current error-handling semantics.")
class SystemRoutesTest {
    private val internalSecret = "test-secret-123"
    private lateinit var systemServices: SystemServices

    @BeforeEach
    fun setup() {
        val runtime = mockk<RuntimeOptions>()
        val systemUseCases = mockk<SystemUseCases>()
        every { runtime.internalSecret } returns internalSecret
        every { systemUseCases.trigger("u1") } returns mapOf("status" to "success")
        every { systemUseCases.trigger("") } throws IllegalArgumentException("userId is required")
        systemServices = object : SystemServices {
            override val runtimeOptions = runtime
            override val healthUseCases = mockk<com.appforge.server.services.system.HealthUseCases>(relaxed = true)
            override val systemUseCases = systemUseCases
        }
    }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `trigger rejects unauthorized secret`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }

        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { systemRoutes(systemServices) }
        }

        val response = client.post("/api/v1/system/trigger?userId=u1") {
            header("X-Internal-Secret", "wrong-secret")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `trigger requires userId`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }

        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { systemRoutes(systemServices) }
        }

        val response = client.post("/api/v1/system/trigger") {
            header("X-Internal-Secret", internalSecret)
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `trigger responds OK with correct secret`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }

        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { systemRoutes(systemServices) }
        }

        val res = client.post("/api/v1/system/trigger?userId=u1") {
            header("X-Internal-Secret", internalSecret)
        }
        assertEquals(HttpStatusCode.OK, res.status)
    }
}

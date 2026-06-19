package com.appforge.server.routing

import com.appforge.server.config.options.RuntimeOptions
import com.appforge.server.services.system.HealthUseCases
import com.appforge.server.services.system.SystemServices
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.routing.routing
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.testing.testApplication
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.application.install
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HealthRoutesTest {
    @Test
    fun `health route returns ok`() = testApplication {
        environment {
            config = MapApplicationConfig()
        }

        application {
            install(ContentNegotiation) { json() }
            val services = object : SystemServices {
                override val runtimeOptions = RuntimeOptions(
                    appId = "test-app",
                    port = 8080,
                    host = "localhost",
                    corsAllowedOrigins = emptyList(),
                    nodeEnv = "test",
                    publicBaseUrl = "http://localhost:8080",
                    internalSecret = "secret",
                    earlyAccessEnabled = false,
                    documentMaxContentChars = 20_000,
                )
                override val healthUseCases = mockk<HealthUseCases>().also {
                    every { it.status() } returns "ok"
                }
                override val systemUseCases = mockk<com.appforge.server.services.system.SystemUseCases>(relaxed = true)
            }
            routing { healthRoutes(services) }
        }

        val response = client.get("/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("ok"))
    }
}

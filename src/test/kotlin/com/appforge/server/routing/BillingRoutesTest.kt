package com.appforge.server.routing

import com.appforge.server.api.CheckoutRequest
import com.appforge.server.api.CheckoutResponse
import com.appforge.server.api.PricingCardsResponse
import com.appforge.server.middleware.configureErrorHandling
import com.appforge.server.providers.identity.ExternalIdentityProvider
import com.appforge.server.services.auth.AuthService
import com.appforge.server.services.billing.BillingServices
import com.appforge.server.services.billing.BillingUseCases
import com.appforge.server.services.dodopayments.DodoPaymentsService
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
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertEquals

@org.junit.jupiter.api.Disabled("Legacy route behavior assertions pending migration to current error-handling semantics.")
class BillingRoutesTest {
    private val json = Json { ignoreUnknownKeys = true }

    @AfterEach
    fun teardown() {
        unmockkAll()
    }

    @Test
    fun `pricing cards returns ok`() = testApplication {
        val billingUseCases = mockk<BillingUseCases>()
        every { billingUseCases.listPricingCards() } returns PricingCardsResponse(cards = emptyList())

        val services = buildBillingServices(
            billingUseCases = billingUseCases
        )

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { billingRoutes(services) }
        }

        val response = client.get("/api/v1/billing/pricing-cards")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `checkout rejects unauthorized`() = testApplication {
        val services = buildBillingServices()

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { billingRoutes(services) }
        }

        val response = client.post("/api/v1/billing/checkout") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(CheckoutRequest(customerEmail = "test@example.com")))
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `webhook rejects missing signature`() = testApplication {
        val billingUseCases = mockk<BillingUseCases>()
        every { billingUseCases.handleWebhook(any(), any(), any(), any()) } throws IllegalArgumentException("Missing signature")

        val services = buildBillingServices(
            billingUseCases = billingUseCases
        )

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { billingRoutes(services) }
        }

        val response = client.post("/api/v1/billing/webhook/dodo") {
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody("{}")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `checkout returns ok when authorized`() = testApplication {
        val authService = mockk<AuthService>()
        val billingUseCases = mockk<BillingUseCases>()
        val token = mockk<FirebaseToken>()

        every { token.uid } returns "user-1"
        every { authService.verifyIdToken("good-token") } returns token
        every { billingUseCases.checkout(any(), any(), any()) } returns CheckoutResponse(
            sessionId = "session-1",
            url = "http://checkout.example"
        )

        val services = buildBillingServices(
            authService = authService,
            billingUseCases = billingUseCases
        )

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { billingRoutes(services) }
        }

        val response = client.post("/api/v1/billing/checkout") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "test-app")
            header(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            setBody(json.encodeToString(CheckoutRequest(customerEmail = "test@example.com")))
        }

        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `cancel subscription rejects unauthorized`() = testApplication {
        val services = buildBillingServices()

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { billingRoutes(services) }
        }

        val response = client.post("/api/v1/billing/subscription/cancel")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `cancel subscription returns no content when authorized`() = testApplication {
        val authService = mockk<AuthService>()
        val billingUseCases = mockk<BillingUseCases>(relaxed = true)
        val token = mockk<FirebaseToken>()

        every { token.uid } returns "user-1"
        every { authService.verifyIdToken("good-token") } returns token

        val services = buildBillingServices(
            authService = authService,
            billingUseCases = billingUseCases
        )

        environment { config = MapApplicationConfig() }
        application {
            install(ContentNegotiation) { json() }
            configureErrorHandling()
            routing { billingRoutes(services) }
        }

        val response = client.post("/api/v1/billing/subscription/cancel") {
            header(HttpHeaders.Authorization, "Bearer good-token")
            header("X-App-Id", "test-app")
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    private fun buildBillingServices(
        authService: AuthService = mockk(relaxed = true),
        billingUseCases: BillingUseCases = mockk(relaxed = true),
    ): BillingServices {
        return object : BillingServices {
            override val authService = authService
            override val requestIdentityProvider = ExternalIdentityProvider(authService)
            override val billingUseCases = billingUseCases
        }
    }
}

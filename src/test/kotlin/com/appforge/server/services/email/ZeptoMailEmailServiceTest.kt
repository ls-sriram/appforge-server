package com.appforge.server.services.email

import com.appforge.server.config.options.EmailOptions
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZeptoMailEmailServiceTest {

    @Test
    fun `sendEmail sends correct JSON payload to Zoho`() = runBlocking {
        var capturedBody: String? = null
        
        val mockEngine = MockEngine { request ->
            capturedBody = request.body.toByteArray().decodeToString()
            respond(
                content = """{"data": [{"code": "SUCCESS", "message": "Email sent"}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val options = EmailOptions(
            enabled = true,
            zeptoMailSendToken = "test-token",
            zeptoMailApiUrl = "https://test.com",
            fromEmail = "noreply@test.com",
            fromName = "Test App"
        )
        
        val service = ZeptoMailEmailService(options)
        
        // Use reflection to swap the client for testing since it's private
        val clientField = service.javaClass.getDeclaredField("client")
        clientField.isAccessible = true
        clientField.set(service, HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        })

        service.sendPaymentConfirmation(
            to = "user@test.com",
            amount = "100.00",
            currency = "USD",
            planName = "Premium",
            transactionId = "tx_123"
        )

        assertNotNull(capturedBody)
        val json = Json.parseToJsonElement(capturedBody!!).jsonObject
        
        assertEquals("noreply@test.com", json["from"]?.jsonObject?.get("address")?.jsonPrimitive?.content)
        assertEquals("Test App", json["from"]?.jsonObject?.get("name")?.jsonPrimitive?.content)
        assertEquals("Order Confirmed - Premium", json["subject"]?.jsonPrimitive?.content)
        
        val toArray = json["to"]?.jsonArray
        assertEquals(1, toArray?.size)
        assertEquals("user@test.com", toArray?.get(0)?.jsonObject?.get("email_address")?.jsonObject?.get("address")?.jsonPrimitive?.content)
        
        assertTrue(json["htmlbody"]?.jsonPrimitive?.content?.contains("Premium") == true)
        assertTrue(json["htmlbody"]?.jsonPrimitive?.content?.contains("100.00 USD") == true)
    }

    private fun assertNotNull(value: Any?) {
        if (value == null) throw AssertionError("Value should not be null")
    }
}

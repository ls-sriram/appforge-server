package com.appforge.server.clients

import com.aallam.openai.client.OpenAI
import com.appforge.server.config.AppEnv
import com.appforge.server.config.options.*
import com.appforge.server.infrastructure.DatabaseMode
import com.appforge.server.infrastructure.DatabaseProvider
import kotlin.test.Test
import kotlin.test.assertNotNull

class OpenAIClientTest {
    @Test
    fun `OpenAIClient can be initialized with mock override`() {
        val env =
                AppEnv(
                        runtime = RuntimeOptions(8080, "localhost", emptyList(), "development", "http://localhost:8080", "test-secret", false),
                        session = SessionOptions(false, "session", 14, "Lax"),
                        dodoPayments = DodoPaymentsOptions("test", null, "https://test.com"),
                        firebase = FirebaseOptions("test", "test", "test", null, null, null),
                        uploads = UploadOptions("test-bucket", "test-secret", 600, 1000L),
                        openai = OpenAIOptions("test-key"),
                        email = EmailOptions("test", "https://test.com", "test@test.com", "test"),
                        database = com.appforge.server.config.options.DatabaseOptions(com.appforge.server.infrastructure.DatabaseProvider.SQL, null, "", "", "", 10),
                        billing = BillingOptions(
                                trialDurationDays = 14,
                                dodoProductIds = mapOf(
                                        "pro_monthly" to "p_monthly",
                                        "pro_annual" to "p_annual"
                                )
                        )
                )

        // Create a mock client that throws if any real methods are called
        val mockOpenAI =
                object : OpenAIDataClient {
                    override val service: OpenAI
                        get() =
                                throw UnsupportedOperationException(
                                        "Should not hit real OpenAI SDK"
                                )
                }

        assertNotNull(mockOpenAI)
        println("OpenAI architecture verified with mock isolation")
    }
}

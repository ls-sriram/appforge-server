package com.appforge.server.services.uploads

import com.appforge.server.config.AppEnv
import com.appforge.server.config.options.*
import com.appforge.server.infrastructure.DatabaseMode
import com.appforge.server.infrastructure.DatabaseProvider
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private inline fun require(value: Boolean) { if (!value) throw IllegalArgumentException("require failed") }

class UploadInitServiceTest {
    @Test
    fun `rejects when not authorized`() {
        val service =
                UploadInitService(
                        env = testEnv(),
                        authorizer = UploadOwnershipAuthorizer { _, _, _ -> false },
                        metadataRepository = InMemoryUploadMetadataRepository(),
                        signedPutUrlIssuer = FakeSignedUploadIssuer(),
                        signedGetUrlIssuer = FakeSignedGetIssuer(),
                        clock = fixedClock(),
                )

        assertFailsWith<ForbiddenUploadException> {
            runSuspend {
                service.init(
                        uid = "u1",
                        type = UploadType("audio"),
                        entityId = "a1",
                        contentType = "audio/webm",
                        sizeBytes = 123,
                        assetId = "r1"
                )
            }
        }
    }

    @Test
    fun `creates pending upload and returns signed url`() {
        val repo = InMemoryUploadMetadataRepository()
        val service =
                UploadInitService(
                        env = testEnv(),
                        authorizer = UploadOwnershipAuthorizer { _, _, _ -> true },
                        metadataRepository = repo,
                        signedPutUrlIssuer = FakeSignedUploadIssuer(),
                        signedGetUrlIssuer = FakeSignedGetIssuer(),
                        clock = fixedClock(),
                )

        val result = runSuspend {
            service.init(
                    uid = "u1",
                    type = UploadType("image"),
                    entityId = "e1",
                    contentType = "image/png",
                    sizeBytes = 100,
                    assetId = "i1"
            )
        }

        assertEquals("https://signed.example/upload", result.uploadUrl)
        assertEquals(
                Instant.parse("2025-01-01T00:00:00Z").plusSeconds(600).toEpochMilli(),
                result.expiresAtTimestamp
        )
        assertEquals(1, repo.records.size)

        val stored = repo.records.single()
        assertEquals(
                "/uploads/access/i1",
                result.accessUrl
        )
        assertEquals("u1", stored.uid)
        assertEquals(UploadType("image"), stored.type)
        assertEquals("e1", stored.entityId)
        assertEquals("test-bucket", stored.bucket)
        assertEquals("pending", stored.status)
        assertEquals("image/png", stored.contentType)
        assertEquals(100, stored.sizeBytes)
        require(stored.objectName.contains("entities/e1/uploads/"))
        require(stored.objectName.endsWith(".png"))
    }

    @Test
    fun `enforces max size`() {
        val service =
                UploadInitService(
                        env = testEnv(uploadMaxBytes = 99),
                        authorizer = UploadOwnershipAuthorizer { _, _, _ -> true },
                        metadataRepository = InMemoryUploadMetadataRepository(),
                        signedPutUrlIssuer = FakeSignedUploadIssuer(),
                        signedGetUrlIssuer = FakeSignedGetIssuer(),
                        clock = fixedClock(),
                )

        assertFailsWith<IllegalArgumentException> {
            runSuspend {
                service.init(
                        uid = "u1",
                        type = UploadType("image"),
                        entityId = "e1",
                        contentType = "image/jpeg",
                        sizeBytes = 100,
                        assetId = "i1"
                )
            }
        }
    }

    private class InMemoryUploadMetadataRepository : UploadMetadataRepository {
        val records = mutableListOf<UploadRecord>()
        override suspend fun createPending(record: UploadRecord) {
            records.add(record)
        }

        override suspend fun getByAssetId(assetId: String): UploadRecord? {
            return records.find { it.assetId == assetId }
        }
    }

    private class FakeSignedGetIssuer : SignedGetUrlIssuer {
        override suspend fun issue(bucket: String, objectPath: String, expiresInSeconds: Long): String {
            return "https://signed.example/get"
        }
    }

    private class FakeSignedUploadIssuer : SignedUploadUrlIssuer {
        override suspend fun issue(request: SignedUploadRequest): SignedUploadResponse {
            return SignedUploadResponse(
                    uploadId = request.uploadId,
                    objectPath = request.objectPath,
                    uploadUrl = "https://signed.example/upload",
                    expiresAtTimestamp = request.expiresAtTimestamp,
            )
        }
    }

    private fun fixedClock(): Clock =
            Clock.fixed(Instant.parse("2025-01-01T00:00:00Z"), ZoneOffset.UTC)

    private fun testEnv(uploadMaxBytes: Long = 10_000): AppEnv {
        return AppEnv(
                runtime =
                        RuntimeOptions(
                                port = 8080,
                                host = "localhost",
                                corsAllowedOrigins = emptyList(),
                                nodeEnv = "development",
                                publicBaseUrl = "http://localhost:8080",
                                internalSecret = "test-secret",
                                earlyAccessEnabled = false
                        ),
                session =
                        SessionOptions(
                                cookieSecure = false,
                                sessionCookieName = "session",
                                sessionExpiryDays = 14,
                                cookieSameSite = "Lax",
                        ),
                firebase =
                        FirebaseOptions(
                                firebaseProjectId = "p",
                                firebaseClientEmail = "e",
                                firebasePrivateKey = "k",
                                firebaseServiceAccountJson = null,
                                firebasePrivateKeyId = null,
                                firebaseClientId = null,
                        ),
                uploads =
                        UploadOptions(
                                uploadsBucket = "test-bucket",
                                uploadEventSharedSecret = "test-secret",
                                uploadUrlExpirySeconds = 600,
                                uploadMaxBytes = uploadMaxBytes,
                        ),
                dodoPayments =
                        DodoPaymentsOptions(
                                dodoPaymentsApiKey = "test",
                                dodoPaymentsWebhookKey = null,
                                dodoPaymentsBaseUrl = "https://test.com",
                        ),
                openai =
                        OpenAIOptions(
                                apiKey = "test-key",
                        ),
                email =
                        EmailOptions(
                                zeptoMailSendToken = "test",
                                zeptoMailApiUrl = "https://test.com",
                                fromEmail = "test@test.com",
                                fromName = "test"
                        ),
                database = com.appforge.server.config.options.DatabaseOptions(com.appforge.server.infrastructure.DatabaseProvider.SQL, null, "", "", "", 10),
                billing = BillingOptions(
                        trialDurationDays = 14,
                        dodoProductIds = mapOf(
                                "pro_monthly" to "p_monthly",
                                "pro_annual" to "p_annual"
                        )
                )
        )
    }

    private fun <T> runSuspend(block: suspend () -> T): T =
            kotlinx.coroutines.runBlocking { block() }
}

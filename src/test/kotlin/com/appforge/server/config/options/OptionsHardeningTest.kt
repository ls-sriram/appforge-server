package com.appforge.server.config.options

import com.appforge.server.config.ConfigReader
import com.typesafe.config.ConfigFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OptionsHardeningTest {
    @Test
    fun `runtime options require internal secret`() {
        val reader = ConfigReader(ConfigFactory.empty(), mapOf("NODE_ENV" to "development"))

        assertFailsWith<IllegalStateException> {
            RuntimeOptions.load(reader)
        }
    }

    @Test
    fun `upload options require upload event shared secret`() {
        val reader = ConfigReader(
            ConfigFactory.empty(),
            mapOf(
                "UPLOADS_BUCKET" to "bucket",
                "UPLOAD_MAX_BYTES" to "1024",
            ),
        )

        assertFailsWith<IllegalStateException> {
            UploadOptions.load(reader)
        }
    }

    @Test
    fun `runtime and upload options load with required secrets present`() {
        val reader = ConfigReader(
            ConfigFactory.empty(),
            mapOf(
                "NODE_ENV" to "development",
                "APP_ID" to "example-app",
                "INTERNAL_SECRET" to "test-secret",
                "UPLOADS_BUCKET" to "bucket",
                "UPLOAD_EVENT_SHARED_SECRET" to "upload-secret",
                "UPLOAD_MAX_BYTES" to "1024",
            ),
        )

        val runtime = RuntimeOptions.load(reader)
        val upload = UploadOptions.load(reader)

        assertEquals("test-secret", runtime.internalSecret)
        assertEquals("example-app", runtime.appId)
        assertEquals("upload-secret", upload.uploadEventSharedSecret)
    }

    @Test
    fun `email options require token when enabled`() {
        val reader = ConfigReader(
            ConfigFactory.empty(),
            mapOf("EMAIL_ENABLED" to "true"),
        )

        assertFailsWith<IllegalStateException> {
            EmailOptions.load(reader)
        }
    }

    @Test
    fun `email options allow missing token when disabled`() {
        val reader = ConfigReader(
            ConfigFactory.empty(),
            mapOf("EMAIL_ENABLED" to "false"),
        )

        val email = EmailOptions.load(reader)
        assertEquals(false, email.enabled)
        assertEquals("", email.zeptoMailSendToken)
    }

    @Test
    fun `firebase options require key fields when enabled`() {
        val reader = ConfigReader(
            ConfigFactory.empty(),
            mapOf(
                "FIREBASE_ENABLED" to "true",
                "FIREBASE_PROJECT_ID" to "test-project",
            ),
        )

        assertFailsWith<IllegalStateException> {
            FirebaseOptions.load(reader)
        }
    }

    @Test
    fun `firebase options allow missing key fields when disabled`() {
        val reader = ConfigReader(
            ConfigFactory.empty(),
            mapOf(
                "FIREBASE_ENABLED" to "false",
                "FIREBASE_PROJECT_ID" to "test-project",
            ),
        )

        val firebase = FirebaseOptions.load(reader)
        assertEquals(false, firebase.enabled)
        assertEquals("", firebase.firebasePrivateKey)
    }
}

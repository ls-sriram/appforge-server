package com.appforge.server.services.dodopayments

import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DodoWebhookVerifierTest {

    @Test
    fun `verify should return true for valid Base64 secret`() {
        val rawSecret = "test_raw_key_12345"
        val base64Secret = java.util.Base64.getEncoder().encodeToString(rawSecret.toByteArray())
        val secret = "whsec_$base64Secret"

        val verifier = DodoWebhookVerifier(secret)

        val payload = "{\"test\": \"data\"}"
        val id = "msg_123"
        val timestamp = "1700000000"

        // Calculate correct signature using raw bytes of the secret
        val signedMessage = "$id.$timestamp.$payload"
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(rawSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hash = hmac.doFinal(signedMessage.toByteArray(Charsets.UTF_8))
        val encodedHash = java.util.Base64.getEncoder().encodeToString(hash)
        val signature = "v1,$encodedHash"

        assertTrue(verifier.verify(payload, signature, id, timestamp))
    }

    @Test
    fun `verify should return true for valid raw string secret`() {
        val secret = "whsec_test_secret_key"
        val cleanSecret = "test_secret_key" // Stripped by verifier
        val verifier = DodoWebhookVerifier(secret)

        val payload = "{\"test\": \"data\"}"
        val id = "msg_123"
        val timestamp = "1700000000"

        // Calculate correct signature
        val signedMessage = "$id.$timestamp.$payload"
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(cleanSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hash = hmac.doFinal(signedMessage.toByteArray(Charsets.UTF_8))
        val encodedHash = Base64.getEncoder().encodeToString(hash)
        val signature = "v1,$encodedHash"

        assertTrue(verifier.verify(payload, signature, id, timestamp))
    }

    @Test
    fun `verify should return false for invalid signature`() {
        val secret = "whsec_test_secret_key"
        val verifier = DodoWebhookVerifier(secret)

        val payload = "{\"test\": \"data\"}"
        val id = "msg_123"
        val timestamp = "1700000000"
        val signature = "v1,invalid_signature"

        assertFalse(verifier.verify(payload, signature, id, timestamp))
    }

    @Test
    fun `verify should return false for missing headers`() {
        val secret = "whsec_test_secret_key"
        val verifier = DodoWebhookVerifier(secret)

        val payload = "{\"test\": \"data\"}"
        val signature = "v1,signature"

        assertFalse(verifier.verify(payload, signature, null, "123"))
        assertFalse(verifier.verify(payload, signature, "msg_123", null))
    }

    @Test
    fun `verify should return true if no secret configured`() {
        val verifier = DodoWebhookVerifier(null)
        assertTrue(verifier.verify("payload", "signature", "id", "timestamp"))
    }

    @Test
    fun `verify should handle multiple signatures in header`() {
        val secret = "whsec_test_secret_key"
        val cleanSecret = "test_secret_key"
        val verifier = DodoWebhookVerifier(secret)

        val payload = "{\"test\": \"data\"}"
        val id = "msg_123"
        val timestamp = "1700000000"

        val signedMessage = "$id.$timestamp.$payload"
        val hmac = Mac.getInstance("HmacSHA256")
        hmac.init(SecretKeySpec(cleanSecret.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        val hash = hmac.doFinal(signedMessage.toByteArray(Charsets.UTF_8))
        val encodedHash = Base64.getEncoder().encodeToString(hash)

        val signature = "v1,other_sig v1,$encodedHash v2,something_else"

        assertTrue(verifier.verify(payload, signature, id, timestamp))
    }
}

package com.appforge.server.services.dodopayments

import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.slf4j.LoggerFactory

class DodoWebhookVerifier(private val secret: String?) {
    private val logger = LoggerFactory.getLogger(DodoWebhookVerifier::class.java)

    fun verify(payload: String, signature: String, id: String?, timestamp: String?): Boolean {
        if (secret == null) {
            logger.warn("No DodoPayments webhook secret configured. Skipping verification.")
            return true
        }

        if (id == null || timestamp == null) {
            logger.warn("Missing DodoPayments webhook headers (id/timestamp) for verification.")
            return false
        }

        return try {
            val signedMessage = "$id.$timestamp.$payload"
            val hmac = Mac.getInstance("HmacSHA256")

            // Dodo/Svix documentation specifies the secret is provided as a Base64 string (often
            // prefixed with whsec_).
            // We strip the 'whsec_' prefix if present and decode the remaining Base64 string.
            val cleanSecret = if (secret.startsWith("whsec_")) secret.substring(6) else secret
            val secretBytes =
                    try {
                        java.util.Base64.getDecoder().decode(cleanSecret)
                    } catch (e: Exception) {
                        // Fallback for non-base64 secrets (legacy or custom)
                        cleanSecret.toByteArray(Charsets.UTF_8)
                    }

            val secretKey = SecretKeySpec(secretBytes, "HmacSHA256")
            hmac.init(secretKey)

            val computedHash = hmac.doFinal(signedMessage.toByteArray(Charsets.UTF_8))
            val computedSignature = java.util.Base64.getEncoder().encodeToString(computedHash)

            // The signature header contains space-separated signatures in the format v1,BASE64
            val passedSignatures = signature.split(" ")
            for (passedSig in passedSignatures) {
                if (!passedSig.startsWith("v1,")) continue
                val sigOnly = passedSig.substring(3)

                // Constant-time comparison to prevent timing attacks
                if (MessageDigest.isEqual(sigOnly.toByteArray(), computedSignature.toByteArray())) {
                    return true
                }
            }

            logger.error(
                    "DodoPayments signature mismatch. Computed: $computedSignature, Received: $signature"
            )
            false
        } catch (e: Exception) {
            logger.error("Error verifying DodoPayments webhook signature", e)
            false
        }
    }
}

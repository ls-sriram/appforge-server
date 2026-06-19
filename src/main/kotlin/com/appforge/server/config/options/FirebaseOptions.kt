package com.appforge.server.config.options

import com.appforge.server.config.ConfigReader
import com.google.auth.oauth2.GoogleCredentials
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

data class FirebaseOptions(
    val enabled: Boolean,
    val firebaseProjectId: String,
    val firebasePrivateKey: String,
    val firebaseServiceAccountJson: String?,
    val firebasePrivateKeyId: String?,
    val firebaseClientId: String?,
) {
    /**
     * Returns GoogleCredentials derived from the configuration.
     * Prefers the full service account JSON if provided, otherwise reconstructs it from individual fields.
     * If no credentials are found in the environment, falls back to Application Default Credentials
     * (ideal for Cloud Run).
     */
    fun getCredentials(): GoogleCredentials {
        if (!enabled) {
            error("Firebase is disabled")
        }
        return try {
            when {
                !firebaseServiceAccountJson.isNullOrBlank() ->
                    GoogleCredentials.fromStream(
                        ByteArrayInputStream(firebaseServiceAccountJson.toByteArray(StandardCharsets.UTF_8))
                    )
                firebasePrivateKey.isNotBlank() -> {
                    val json = buildServiceAccountJson()
                    GoogleCredentials.fromStream(ByteArrayInputStream(json.toByteArray(StandardCharsets.UTF_8)))
                }
                else -> GoogleCredentials.getApplicationDefault()
            }
        } catch (error: Exception) {
            System.err.println("Warning: failed to load Firebase credentials, falling back to application default. ${error.message}")
            GoogleCredentials.getApplicationDefault()
        }
    }

    private fun buildServiceAccountJson(): String {
        val privateKeyId = firebasePrivateKeyId ?: ""
        val clientId = firebaseClientId ?: ""
        return """
            {
              "type": "service_account",
              "project_id": "${firebaseProjectId}",
              "private_key_id": "${privateKeyId}",
              "private_key": "${firebasePrivateKey}",
              "client_id": "${clientId}",
              "auth_uri": "https://accounts.google.com/o/oauth2/auth",
              "token_uri": "https://oauth2.googleapis.com/token",
              "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs"
            }
        """.trimIndent()
    }

    companion object {
        fun load(reader: ConfigReader): FirebaseOptions {
            val enabled = reader.bool("FIREBASE_ENABLED") ?: true
            val serviceAccountJson = reader.string("FIREBASE_SERVICE_ACCOUNT_JSON")
            val privateKeyRaw = reader.string("FIREBASE_PRIVATE_KEY")
            if (enabled && serviceAccountJson.isNullOrBlank() && privateKeyRaw.isNullOrBlank()) {
                error("Missing required config value: FIREBASE_PRIVATE_KEY or FIREBASE_SERVICE_ACCOUNT_JSON")
            }
            return FirebaseOptions(
                enabled = enabled,
                firebaseProjectId = reader.requiredString("FIREBASE_PROJECT_ID"),
                firebasePrivateKey = normalizePrivateKey(privateKeyRaw ?: ""),
                firebaseServiceAccountJson = serviceAccountJson,
                firebasePrivateKeyId = reader.string("FIREBASE_PRIVATE_KEY_ID"),
                firebaseClientId = reader.string("FIREBASE_CLIENT_ID"),
            )
        }

        private fun normalizePrivateKey(raw: String): String {
            return raw.replace("\\n", "\n").trim()
        }
    }
}

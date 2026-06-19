package com.appforge.server.config.options

import com.appforge.server.config.ConfigDefaults.DEFAULT_HOST
import com.appforge.server.config.ConfigDefaults.DEFAULT_PORT
import com.appforge.server.config.ConfigReader

const val DOCUMENT_CONTENT_MAX_HARD_LIMIT = 20_000

data class RuntimeOptions(
    val appId: String,
    val port: Int,
    val host: String,
    val corsAllowedOrigins: List<String>,
    val nodeEnv: String,
    val publicBaseUrl: String,
    val internalSecret: String,
    val earlyAccessEnabled: Boolean,
    val documentMaxContentChars: Int,
) {
    companion object {
        fun load(reader: ConfigReader): RuntimeOptions {
            val nodeEnv = reader.string("NODE_ENV")?.ifBlank { "development" } ?: "development"
            val internalSecret = reader.requiredString("INTERNAL_SECRET")

            return RuntimeOptions(
                appId = reader.string("APP_ID")?.ifBlank { "example-app" } ?: "example-app",
                port = reader.int("PORT") ?: DEFAULT_PORT,
                host = reader.string("HOST")?.ifBlank { DEFAULT_HOST } ?: DEFAULT_HOST,
                corsAllowedOrigins = reader.stringList("CORS_ALLOWED_ORIGINS"),
                nodeEnv = nodeEnv,
                publicBaseUrl = reader.string("APP_PUBLIC_URL")?.ifBlank { "http://localhost:8080" } ?: "http://localhost:8080",
                internalSecret = internalSecret,
                earlyAccessEnabled = reader.bool("EARLY_ACCESS_ENABLED") ?: true,
                documentMaxContentChars = (reader.int("DOCUMENT_MAX_CONTENT_CHARS")
                    ?: DOCUMENT_CONTENT_MAX_HARD_LIMIT)
                    .coerceIn(1, DOCUMENT_CONTENT_MAX_HARD_LIMIT),
            )
        }
    }
}

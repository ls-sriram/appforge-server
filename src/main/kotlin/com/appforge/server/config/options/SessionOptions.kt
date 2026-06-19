package com.appforge.server.config.options

import com.appforge.server.config.ConfigReader
import com.appforge.server.config.ConfigDefaults.DEFAULT_COOKIE_NAME
import com.appforge.server.config.ConfigDefaults.DEFAULT_SESSION_DAYS
import com.appforge.server.config.ConfigDefaults.DEFAULT_COOKIE_SAMESITE
import java.util.Locale

data class SessionOptions(
    val cookieSecure: Boolean,
    val sessionCookieName: String,
    val sessionExpiryDays: Int,
    val cookieSameSite: String,
) {
    companion object {
        fun load(reader: ConfigReader, nodeEnv: String): SessionOptions {
            val cookieSecureDefault = nodeEnv.lowercase(Locale.getDefault()) == "production"
            val cookieSameSite = reader.string("COOKIE_SAMESITE")
                ?.trim()
                ?.lowercase(Locale.getDefault())
                ?.let { normalizeSameSite(it) }
                ?: DEFAULT_COOKIE_SAMESITE
            return SessionOptions(
                cookieSecure = reader.bool("COOKIE_SECURE") ?: cookieSecureDefault,
                sessionCookieName = reader.string("SESSION_COOKIE_NAME")?.ifBlank { DEFAULT_COOKIE_NAME }
                    ?: DEFAULT_COOKIE_NAME,
                sessionExpiryDays = reader.int("SESSION_EXPIRY_DAYS") ?: DEFAULT_SESSION_DAYS,
                cookieSameSite = cookieSameSite,
            )
        }

        private fun normalizeSameSite(value: String): String {
            return when (value) {
                "none" -> "None"
                "lax" -> "Lax"
                "strict" -> "Strict"
                else -> DEFAULT_COOKIE_SAMESITE
            }
        }
    }
}

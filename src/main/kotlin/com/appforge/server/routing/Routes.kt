package com.appforge.server.routing

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import com.appforge.server.config.AppEnv
import java.util.Locale
import java.net.URI
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.appforge.server.routing.Routes")
// RFC1918 private 172.16.0.0/12 range
private val privateLanRegex = Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")

private data class CorsEnvironment(
    val isDevelopment: Boolean,
    val allowedOrigins: List<String>,
)

private data class NormalizedOrigin(
    val scheme: String,
    val host: String,
    val port: Int,
)

private sealed interface AllowedOriginMatcher {
    fun matches(origin: NormalizedOrigin): Boolean
}

private data class ExactOriginMatcher(
    val origin: NormalizedOrigin,
) : AllowedOriginMatcher {
    override fun matches(origin: NormalizedOrigin): Boolean = this.origin == origin
}

private data class HostMatcher(
    val host: String,
) : AllowedOriginMatcher {
    override fun matches(origin: NormalizedOrigin): Boolean = origin.host == host
}

private data class HostPortMatcher(
    val host: String,
    val port: Int,
) : AllowedOriginMatcher {
    override fun matches(origin: NormalizedOrigin): Boolean {
        return origin.host == host && origin.port == port
    }
}

fun Application.configureCors(env: AppEnv) {
    val configuredOrigins = env.runtime.corsAllowedOrigins

    val corsEnv = CorsEnvironment(
        isDevelopment = env.runtime.nodeEnv.lowercase(Locale.getDefault()) == "development",
        allowedOrigins = configuredOrigins,
    )

    val allowedOriginMatchers = configuredOrigins.mapNotNull(::buildAllowedOriginMatcher)

    install(CORS) {
        // Removed redundant localhost allowHost calls

        // 2. Allow all required methods
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)

        // 3. Allow standard headers
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("X-App-Id")
        allowHeader("ngrok-skip-browser-warning")
        allowHeader("X-Requested-With")
        allowHeader("X-Firebase-AppCheck")

        // 4. Required for session cookies
        allowCredentials = true
        allowNonSimpleContentTypes = true

        allowOrigins { origin ->
            if (isAllowedDevelopmentOrigin(origin, corsEnv.isDevelopment)) {
                return@allowOrigins true
            }
            val result = isAllowedConfiguredOrigin(origin, allowedOriginMatchers)

            if (!result) {
                logger.debug("CORS rejected origin: $origin")
            } else {
                logger.debug("CORS accepted origin: $origin")
            }
            result
        }
    }
}

private fun isAllowedDevelopmentOrigin(origin: String, isDevelopment: Boolean): Boolean {
    if (!isDevelopment) return false

    val uri = parseOriginUri(origin) ?: return false
    val host = uri.host ?: return false
    if (host == "localhost" || host == "127.0.0.1") {
        logger.debug("CORS accepted localhost dev origin: $origin")
        return true
    }

    val isPrivateLanOrigin = host.startsWith("10.") ||
        host.startsWith("192.168.") ||
        host.matches(privateLanRegex)
    if (isPrivateLanOrigin) {
        logger.debug("CORS accepted private LAN dev origin: $origin")
        return true
    }

    return false
}

private fun isAllowedConfiguredOrigin(
    origin: String,
    allowedOrigins: List<AllowedOriginMatcher>,
): Boolean {
    val normalizedOrigin = normalizeOrigin(origin) ?: return false
    return allowedOrigins.any { matcher -> matcher.matches(normalizedOrigin) }
}

private fun buildAllowedOriginMatcher(entry: String): AllowedOriginMatcher? {
    val normalizedOrigin = normalizeOrigin(entry)

    if (normalizedOrigin != null) {
        return ExactOriginMatcher(normalizedOrigin)
    }

    val hostPortParts = entry.split(":")
    if (hostPortParts.size == 2) {
        val port = hostPortParts[1].toIntOrNull() ?: return null
        return HostPortMatcher(
            host = hostPortParts[0],
            port = port,
        )
    }

    return HostMatcher(entry)
}

private fun normalizeOrigin(origin: String): NormalizedOrigin? {
    val uri = parseOriginUri(origin) ?: return null
    val scheme = uri.scheme ?: return null
    val host = uri.host ?: return null

    val port = when {
        uri.port != -1 -> uri.port
        scheme.equals("https", ignoreCase = true) -> 443
        scheme.equals("http", ignoreCase = true) -> 80
        else -> return null
    }

    return NormalizedOrigin(
        scheme = scheme.lowercase(Locale.getDefault()),
        host = host.lowercase(Locale.getDefault()),
        port = port,
    )
}

private fun parseOriginUri(origin: String): URI? =
    try {
        URI(origin)
    } catch (_: Exception) {
        null
    }

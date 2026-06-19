package com.appforge.server.extensions

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Hook event types that the platform fires.
 * Extensions subscribe to these events to react to platform actions.
 */
object HookEvents {
    /** Before an entity is created. Return `{ allow: false }` to block. */
    const val BEFORE_ENTITY_CREATED = "before-entity-created"

    /** After an entity is created. Fire-and-forget. */
    const val AFTER_ENTITY_CREATED = "after-entity-created"

    /** Before an entity is updated. Return `{ allow: false }` to block. */
    const val BEFORE_ENTITY_UPDATED = "before-entity-updated"

    /** After an entity is updated. Fire-and-forget. */
    const val AFTER_ENTITY_UPDATED = "after-entity-updated"

    /** Before a review is submitted. Return `{ allow: false }` to block. */
    const val BEFORE_REVIEW_SUBMITTED = "before-review-submitted"

    /** After a review is submitted. Fire-and-forget. */
    const val AFTER_REVIEW_SUBMITTED = "after-review-submitted"

    /** Before a share is created. Return `{ allow: false }` to block. */
    const val BEFORE_SHARE_CREATED = "before-share-created"

    /** After a share is created. Fire-and-forget. */
    const val AFTER_SHARE_CREATED = "after-share-created"

    /** When an entitlement changes. Fire-and-forget. */
    const val ON_ENTITLEMENT_CHANGED = "on-entitlement-changed"

    /** When a user first logs in. Fire-and-forget. */
    const val ON_USER_LOGIN = "on-user-login"
}

/**
 * Registration of a hook handler.
 * Extensions create these to subscribe to platform events.
 */
data class HookRegistration(
    /** The event type to subscribe to (e.g., `HookEvents.AFTER_ENTITY_CREATED`). */
    val eventType: String,

    /**
     * Hook handler function.
     * For "before" hooks: return `{ "allow": false, "reason": "..." }` to block.
     * For "after" hooks: return value is ignored.
     *
     * @param payload Event-specific data (varies by event type)
     * @return Hook response — `{ "allow": true }` by default
     */
    val handler: suspend (Map<String, Any?>) -> HookResponse,
)

/**
 * Response from a hook handler.
 */
@Serializable
data class HookResponse(
    val allow: Boolean = true,
    val reason: String? = null,
)

/**
 * Hook execution result.
 */
data class HookResult(
    val allowed: Boolean,
    val deniedBy: String? = null,
    val reason: String? = null,
)

/**
 * Hook engine — manages hook subscriptions and fires events.
 *
 * Supports two types of hooks:
 * 1. **In-process hooks** — Kotlin handlers registered by extensions
 * 2. **Webhook hooks** — HTTP POST to external URLs (registered via platform API)
 *
 * ## Before Hooks (blocking)
 * Fire sequentially. First denial blocks the operation.
 *
 * ## After Hooks (fire-and-forget)
 * Fire concurrently. Failures are logged but don't affect the operation.
 */
class HookEngine {
    private val logger = LoggerFactory.getLogger(HookEngine::class.java)

    /** In-process hook handlers, keyed by event type. */
    private val handlers = ConcurrentHashMap<String, MutableList<Pair<String, suspend (Map<String, Any?>) -> HookResponse>>>()

    /** HTTP client for webhook calls. */
    private val httpClient = HttpClient(CIO) {
        engine {
            requestTimeout = 10_000
        }
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Register an in-process hook handler.
     *
     * @param extensionId The extension registering this hook
     * @param registration The hook registration (event type + handler)
     */
    fun register(extensionId: String, registration: HookRegistration) {
        handlers.getOrPut(registration.eventType) { mutableListOf() }
            .add(extensionId to registration.handler)
        logger.info("Hook registered: ${registration.eventType} by $extensionId")
    }

    /**
     * Unregister all hooks for an extension.
     */
    fun unregisterExtension(extensionId: String) {
        handlers.forEach { (_, list) ->
            list.removeAll { it.first == extensionId }
        }
        logger.info("All hooks unregistered for extension: $extensionId")
    }

    /**
     * Fire a "before" hook — blocking, sequential, can deny.
     *
     * Returns `HookResult(allowed = true)` if all handlers allow,
     * or `HookResult(allowed = false, deniedBy = "...", reason = "...")` if any denies.
     */
    suspend fun fireBefore(eventType: String, payload: Map<String, Any?>): HookResult {
        val eventHandlers = handlers[eventType] ?: return HookResult(allowed = true)

        for ((extensionId, handler) in eventHandlers) {
            try {
                val response = handler(payload)
                if (!response.allow) {
                    logger.warn("Hook denied: $eventType by $extensionId — ${response.reason}")
                    return HookResult(allowed = false, deniedBy = extensionId, reason = response.reason)
                }
            } catch (e: Exception) {
                logger.error("Hook error: $eventType by $extensionId — ${e.message}", e)
                // On error, fail open (allow) — the platform shouldn't break due to a hook crash
            }
        }
        return HookResult(allowed = true)
    }

    /**
     * Fire an "after" hook — non-blocking, concurrent, fire-and-forget.
     */
    fun fireAfter(eventType: String, payload: Map<String, Any?>) {
        val eventHandlers = handlers[eventType] ?: return

        CoroutineScope(Dispatchers.IO).launch {
            eventHandlers.map { (extensionId, handler) ->
                async {
                    try {
                        handler(payload)
                    } catch (e: Exception) {
                        logger.error("Async hook error: $eventType by $extensionId — ${e.message}", e)
                    }
                }
            }.awaitAll()
        }
    }

    /**
     * Send a webhook to an external URL.
     * Signs the payload with HMAC-SHA256 using the provided secret.
     */
    suspend fun sendWebhook(
        url: String,
        eventType: String,
        payload: Map<String, Any?>,
        secret: String?,
    ): Boolean {
        return try {
            val body = kotlinx.serialization.json.Json.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                kotlinx.serialization.json.JsonObject(payload.mapValues { (_, v) ->
                    when (v) {
                        is String -> kotlinx.serialization.json.JsonPrimitive(v)
                        is Number -> kotlinx.serialization.json.JsonPrimitive(v)
                        is Boolean -> kotlinx.serialization.json.JsonPrimitive(v)
                        null -> kotlinx.serialization.json.JsonNull
                        else -> kotlinx.serialization.json.JsonPrimitive(v.toString())
                    }
                })
            )
            val timestamp = System.currentTimeMillis() / 1000
            val signature = signPayload(body, timestamp, secret)

            val response = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                header("X-Hook-Event", eventType)
                header("X-Hook-Timestamp", timestamp.toString())
                if (signature != null) {
                    header("X-Hook-Signature", signature)
                }
                setBody(body)
            }

            val success = response.status.isSuccess()
            if (!success) {
                logger.warn("Webhook failed: POST $url — HTTP ${response.status}")
            }
            success
        } catch (e: Exception) {
            logger.error("Webhook error: POST $url — ${e.message}", e)
            false
        }
    }

    private fun signPayload(body: String, timestamp: Long, secret: String?): String? {
        if (secret.isNullOrBlank()) return null
        val payload = "$timestamp.$body"
        val mac = javax.crypto.Mac.getInstance("HmacSHA256").apply {
            init(javax.crypto.spec.SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        }
        val hmac = mac.doFinal(payload.toByteArray())
        return Base64.getEncoder().encodeToString(hmac)
    }

    /**
     * Clean up resources.
     */
    fun close() {
        httpClient.close()
    }
}

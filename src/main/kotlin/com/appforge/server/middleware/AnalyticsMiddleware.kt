package com.appforge.server.middleware

import com.appforge.server.services.analytics.AnalyticsRepository
import com.appforge.server.services.analytics.models.ApiCallRecord
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.slf4j.LoggerFactory
import java.util.UUID

object AnalyticsMiddleware {
    private val logger = LoggerFactory.getLogger("AnalyticsMiddleware")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val normalizedCache = mutableMapOf<String, String>()

    private val AnalyticsStartTimeKey = AttributeKey<Long>("analyticsStartTime")
    private val AnalyticsUserIdKey = AttributeKey<String>("analyticsUserId")

    fun normalizePath(path: String): String {
        return normalizedCache.getOrPut(path) {
            path.split("/")
                .joinToString("/") { segment ->
                    if (segment.isBlank()) segment
                    else if (segment.matches(Regex("^[a-f0-9]{8,}$"))) ":id"
                    else if (segment.matches(Regex("^[a-f0-9-]{20,}$"))) ":id"
                    else segment
                }
        }
    }

    /**
     * Call this from auth routes after login/signup succeeds to attach
     * the resolved userId to the analytics record.
     */
    fun ApplicationCall.setAnalyticsUserId(uid: String) {
        attributes.put(AnalyticsUserIdKey, uid)
    }

    /**
     * Read the userId that was set during auth processing.
     */
    fun ApplicationCall.getAnalyticsUserId(): String? {
        return attributes.getOrNull(AnalyticsUserIdKey)
    }

    fun Application.installAnalyticsCapture(
        repository: AnalyticsRepository,
        getUserId: (ApplicationCall) -> String?,
        getAppId: (ApplicationCall) -> String?,
        getTeamId: (ApplicationCall) -> String?,
        includePaths: (String) -> Boolean,
    ) {
        val self = this
        self.intercept(ApplicationCallPipeline.Call) {
            val call = this.context
            if (!includePaths(call.request.path())) {
                return@intercept
            }

            call.attributes.put(AnalyticsStartTimeKey, System.currentTimeMillis())

            try {
                proceed()
            } finally {
                val startTime = call.attributes.getOrNull(AnalyticsStartTimeKey)
                    ?: return@intercept
                val durationMs = System.currentTimeMillis() - startTime
                val status = call.response.status()?.value ?: 0

                // Priority: explicit auth userId > RequestContext userId
                val userId = call.getAnalyticsUserId() ?: getUserId(call)

                scope.launch {
                    try {
                        repository.insert(ApiCallRecord(
                            requestId = call.request.header("X-Request-Id") ?: UUID.randomUUID().toString(),
                            timestamp = System.currentTimeMillis(),
                            method = call.request.httpMethod.value,
                            path = normalizePath(call.request.path()),
                            rawPath = call.request.uri,
                            status = status,
                            durationMs = durationMs,
                            userId = userId,
                            appId = getAppId(call),
                            teamId = getTeamId(call),
                            userAgent = call.request.header("User-Agent"),
                            error = if (status >= 400) call.response.status()?.description else null,
                        ))
                    } catch (e: Exception) {
                        logger.debug("Analytics capture failed: ${e.message}")
                    }
                }
            }
        }
    }
}

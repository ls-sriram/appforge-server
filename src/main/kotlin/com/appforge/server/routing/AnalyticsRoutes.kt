package com.appforge.server.routing

import com.appforge.server.middleware.SecretAuthPlugin
import com.appforge.server.services.analytics.AnalyticsUseCases
import com.appforge.server.services.analytics.UserAnalyticsUseCases
import com.appforge.server.services.analytics.models.AnalyticsQuery
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

@Serializable
data class AnalyticsErrorResponse(
    val success: Boolean = false,
    val error: String,
)

fun Route.analyticsRoutes(
    useCases: AnalyticsUseCases,
    userUseCases: UserAnalyticsUseCases,
    internalSecret: String,
) {
    route("/internal/analytics") {
        install(SecretAuthPlugin) {
            this.internalSecret = internalSecret
        }

        get {
            val windowMinutes = call.request.queryParameters["window"]?.toLongOrNull() ?: 60L
            val limit = call.request.queryParameters["limit"]?.toLongOrNull() ?: 10L

            val query = AnalyticsQuery(
                windowMinutes = windowMinutes,
                limit = limit,
            )

            try {
                val a = useCases.getAnalytics(query)
                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to true,
                    "summary" to mapOf(
                        "totalCalls" to a.summary.totalCalls,
                        "successRate" to a.summary.successRate,
                        "avgLatencyMs" to a.summary.avgLatencyMs,
                        "p95LatencyMs" to a.summary.p95LatencyMs,
                        "p99LatencyMs" to a.summary.p99LatencyMs,
                    ),
                    "topRoutes" to a.topRoutes.map { r ->
                        mapOf(
                            "method" to r.method,
                            "route" to r.path,
                            "calls" to r.calls,
                            "avgLatencyMs" to r.avgLatencyMs,
                            "errorRate" to r.errorRate,
                        )
                    },
                    "errors" to a.errors.map { e ->
                        mapOf(
                            "route" to e.path,
                            "status" to e.status,
                            "count" to e.count,
                            "message" to (e.message ?: ""),
                        )
                    },
                    "latency" to mapOf(
                        "p50" to a.latency.p50,
                        "p90" to a.latency.p90,
                        "p95" to a.latency.p95,
                        "p99" to a.latency.p99,
                        "max" to a.latency.max,
                    ),
                ))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AnalyticsErrorResponse(error = e.message ?: "Unknown error")
                )
            }
        }

        get("/users/{userId}") {
            val userId = call.parameters["userId"] ?: run {
                call.respond(HttpStatusCode.BadRequest, AnalyticsErrorResponse(error = "userId required"))
                return@get
            }
            val windowMinutes = call.request.queryParameters["window"]?.toLongOrNull() ?: 60L
            val limit = call.request.queryParameters["limit"]?.toLongOrNull() ?: 50L

            try {
                val ua = userUseCases.getUserAnalytics(userId, windowMinutes, limit)
                call.respond(HttpStatusCode.OK, mapOf(
                    "success" to true,
                    "userId" to ua.userId,
                    "summary" to mapOf(
                        "totalCalls" to ua.summary.totalCalls,
                        "firstSeen" to ua.summary.firstSeen,
                        "lastSeen" to ua.summary.lastSeen,
                        "loginCount" to ua.summary.loginCount,
                        "errorCount" to ua.summary.errorCount,
                        "avgLatencyMs" to ua.summary.avgLatencyMs,
                    ),
                    "activity" to ua.activity.map { act ->
                        mapOf(
                            "timestamp" to act.timestamp,
                            "event" to act.event,
                            "route" to act.route,
                            "method" to act.httpMethod,
                            "status" to act.httpStatus,
                            "durationMs" to act.durationMs,
                            "error" to (act.err ?: ""),
                        )
                    },
                    "latency" to mapOf(
                        "p50" to ua.latency.p50,
                        "p90" to ua.latency.p90,
                        "p95" to ua.latency.p95,
                        "p99" to ua.latency.p99,
                        "max" to ua.latency.max,
                    ),
                ))
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    AnalyticsErrorResponse(error = e.message ?: "Unknown error")
                )
            }
        }

        post("/init") {
            call.respond(HttpStatusCode.OK, mapOf("success" to true, "message" to "analytics initialized"))
        }
    }
}

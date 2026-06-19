package com.appforge.server.services.analytics.models

import kotlinx.serialization.Serializable

/**
 * Captured API call record for analytics.
 */
data class ApiCallRecord(
    val requestId: String,
    val timestamp: Long,          // epoch millis
    val method: String,           // GET, POST, etc.
    val path: String,             // normalized route (e.g. /api/v1/entities/:type/:id)
    val rawPath: String,          // full path (e.g. /api/v1/entities/reviews/abc123)
    val status: Int,              // HTTP status code
    val durationMs: Long,         // request duration in milliseconds
    val userId: String?,          // authenticated user or null
    val appId: String?,           // extension app id or null
    val teamId: String?,          // team context or null
    val userAgent: String?,       // client user agent
    val error: String?,           // error message if status >= 400
)

/**
 * Aggregated analytics response.
 */
@Serializable
data class AnalyticsResponse(
    val summary: AnalyticsSummary,
    val topRoutes: List<RouteStats>,
    val errors: List<ErrorRecord>,
    val latency: LatencyStats,
)

@Serializable
data class AnalyticsSummary(
    val totalCalls: Long,
    val successRate: Double,      // percentage of 2xx responses
    val avgLatencyMs: Double,
    val p95LatencyMs: Long,
    val p99LatencyMs: Long,
)

@Serializable
data class RouteStats(
    val method: String,
    val path: String,
    val calls: Long,
    val avgLatencyMs: Double,
    val errorRate: Double,
)

@Serializable
data class ErrorRecord(
    val path: String,
    val method: String,
    val status: Int,
    val message: String?,
    val count: Long,
    val lastSeen: Long,
)

@Serializable
data class LatencyStats(
    val p50: Long,
    val p90: Long,
    val p95: Long,
    val p99: Long,
    val max: Long,
)

/**
 * Query parameters for analytics endpoint.
 */
data class AnalyticsQuery(
    val windowMinutes: Long = 60,
    val limit: Long = 10,
)

/**
 * Per-user analytics response.
 */
@Serializable
data class UserAnalyticsResponse(
    val userId: String,
    val summary: UserSummary,
    val activity: List<UserActivityRecord>,
    val latency: LatencyStats,
)

@Serializable
data class UserSummary(
    val totalCalls: Long,
    val firstSeen: Long,
    val lastSeen: Long,
    val loginCount: Long,
    val errorCount: Long,
    val avgLatencyMs: Double,
)

@Serializable
data class UserActivityRecord(
    val timestamp: Long,
    val event: String,
    val route: String,
    val httpMethod: String,
    val httpStatus: Int,
    val durationMs: Long,
    val err: String?,
)

/**
 * User event types for explicit tracking.
 */
enum class UserEventType {
    LOGIN, LOGOUT, SIGNUP, API_CALL
}

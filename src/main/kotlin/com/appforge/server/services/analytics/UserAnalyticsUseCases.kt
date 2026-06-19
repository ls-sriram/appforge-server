package com.appforge.server.services.analytics

import com.appforge.server.services.analytics.models.*

class UserAnalyticsUseCases(
    private val repository: AnalyticsRepository,
) {
    private data class ParsedUserRecord(
        val userId: String,
        val timestamp: Long,
        val durationMs: Long,
        val status: Int,
        val method: String,
        val path: String,
        val rawPath: String?,
        val error: String?,
    )

    suspend fun getUserAnalytics(userId: String, windowMinutes: Long, limit: Long): UserAnalyticsResponse {
        val sinceTimestamp = (System.currentTimeMillis() - windowMinutes * 60 * 1000).toDouble()
        val userRecords = repository.getAllSince(sinceTimestamp)
            .mapNotNull(::parseRecord)
            .filter { it.userId == userId }

        if (userRecords.isEmpty()) {
            return UserAnalyticsResponse(
                userId = userId,
                summary = UserSummary(0, 0, 0, 0, 0, 0.0),
                activity = emptyList(),
                latency = LatencyStats(0, 0, 0, 0, 0),
            )
        }

        val timestamps = userRecords.map { it.timestamp }.sorted()
        val latencies = userRecords.map { it.durationMs }.sorted()
        val statuses = userRecords.map { it.status }

        val eventTypes = userRecords.map { doc ->
            val path = doc.path
            when {
                path.contains("/login") -> "login"
                path.contains("/logout") -> "logout"
                path.contains("/signup") -> "signup"
                else -> "api_call"
            }
        }

        val loginCount = eventTypes.count { it == "login" }
        val errorCount = statuses.count { it >= 400 }

        val activity = userRecords
            .mapIndexed { idx, doc ->
                val evtType = eventTypes[idx]
                Triple(doc.timestamp, evtType, doc)
            }
            .sortedByDescending { it.first }
            .take(limit.toInt())
            .map { (ts, evt, doc) ->
                UserActivityRecord(
                    timestamp = ts,
                    event = evt,
                    route = doc.rawPath ?: doc.path,
                    httpMethod = doc.method,
                    httpStatus = doc.status,
                    durationMs = doc.durationMs,
                    err = doc.error,
                )
            }

        return UserAnalyticsResponse(
            userId = userId,
            summary = UserSummary(
                totalCalls = userRecords.size.toLong(),
                firstSeen = timestamps.firstOrNull() ?: 0L,
                lastSeen = timestamps.lastOrNull() ?: 0L,
                loginCount = loginCount.toLong(),
                errorCount = errorCount.toLong(),
                avgLatencyMs = if (latencies.isNotEmpty()) latencies.average() else 0.0,
            ),
            activity = activity,
            latency = LatencyStats(
                p50 = latencies.percentile(0.50),
                p90 = latencies.percentile(0.90),
                p95 = latencies.percentile(0.95),
                p99 = latencies.percentile(0.99),
                max = latencies.maxOrNull() ?: 0,
            ),
        )
    }

    private fun List<Long>.percentile(p: Double): Long {
        if (isEmpty()) return 0L
        val idx = (p * (size - 1)).toInt().coerceIn(0, size - 1)
        return this[idx]
    }

    private fun parseRecord(record: Map<String, Any?>): ParsedUserRecord? {
        val parsedUserId = (record["userId"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val timestamp = (record["timestamp"] as? Number)?.toLong() ?: return null
        val durationMs = (record["durationMs"] as? Number)?.toLong() ?: return null
        val status = (record["status"] as? Number)?.toInt() ?: return null
        val method = (record["method"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val path = (record["path"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val rawPath = (record["rawPath"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val error = (record["error"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        return ParsedUserRecord(
            userId = parsedUserId,
            timestamp = timestamp,
            durationMs = durationMs,
            status = status,
            method = method,
            path = path,
            rawPath = rawPath,
            error = error,
        )
    }
}

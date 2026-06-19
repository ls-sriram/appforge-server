package com.appforge.server.services.analytics

import com.appforge.server.services.analytics.models.*

/**
 * Aggregates raw API call records into analytics summaries.
 */
class AnalyticsUseCases(
    private val repository: AnalyticsRepository,
) {
    private data class ParsedRecord(
        val method: String,
        val path: String,
        val rawPath: String?,
        val status: Int,
        val durationMs: Long,
        val timestamp: Long,
        val error: String?,
    )

    suspend fun getAnalytics(query: AnalyticsQuery): AnalyticsResponse {
        val sinceTimestamp = (System.currentTimeMillis() - query.windowMinutes * 60 * 1000).toDouble()
        val records = repository.getAllSince(sinceTimestamp)
            .mapNotNull(::parseRecord)

        if (records.isEmpty()) {
            return AnalyticsResponse(
                summary = AnalyticsSummary(0, 0.0, 0.0, 0, 0),
                topRoutes = emptyList(),
                errors = emptyList(),
                latency = LatencyStats(0, 0, 0, 0, 0),
            )
        }

        val latencies = records.map { it.durationMs }.sorted()
        val statuses = records.map { it.status }
        val successCount = statuses.count { it in 200..299 }
        val totalCount = records.size.toDouble()

        return AnalyticsResponse(
            summary = AnalyticsSummary(
                totalCalls = records.size.toLong(),
                successRate = if (totalCount > 0) (successCount / totalCount * 100).round(1) else 0.0,
                avgLatencyMs = if (latencies.isNotEmpty()) latencies.average().round(1) else 0.0,
                p95LatencyMs = latencies.percentile(0.95),
                p99LatencyMs = latencies.percentile(0.99),
            ),
            topRoutes = computeTopRoutes(records, query.limit),
            errors = computeErrors(records, query.limit),
            latency = LatencyStats(
                p50 = latencies.percentile(0.50),
                p90 = latencies.percentile(0.90),
                p95 = latencies.percentile(0.95),
                p99 = latencies.percentile(0.99),
                max = latencies.maxOrNull() ?: 0,
            ),
        )
    }

    private fun computeTopRoutes(records: List<ParsedRecord>, limit: Long): List<RouteStats> {
        val groups = mutableMapOf<String, MutableList<Long>>() // "METHOD path" -> [durations]
        val errorCounts = mutableMapOf<String, Int>() // "METHOD path" -> errors

        for (record in records) {
            val key = "${record.method} ${record.path}"

            groups.getOrPut(key) { mutableListOf() }.add(record.durationMs)
            if (record.status >= 400) {
                errorCounts[key] = errorCounts.getOrDefault(key, 0) + 1
            }
        }

        return groups.entries
            .sortedByDescending { it.value.sum() }
            .take(limit.toInt())
            .map { (key, durations) ->
                val (method, path) = key.split(" ", limit = 2)
                val errors = errorCounts.getOrDefault(key, 0)
                RouteStats(
                    method = method,
                    path = path,
                    calls = durations.size.toLong(),
                    avgLatencyMs = durations.average().round(1),
                    errorRate = if (durations.isNotEmpty()) (errors.toDouble() / durations.size * 100).round(1) else 0.0,
                )
            }
    }

    private fun computeErrors(records: List<ParsedRecord>, limit: Long): List<ErrorRecord> {
        val groups = mutableMapOf<String, Pair<Int, List<Pair<Long, String>>>>()

        for (record in records) {
            if (record.status < 400) continue
            val path = record.rawPath ?: record.path

            val key = "${record.method} $path ${record.status}"
            val existing = groups[key]
            val errorList = if (existing != null) {
                existing.second + (record.timestamp to (record.error ?: ""))
            } else {
                listOf(record.timestamp to (record.error ?: ""))
            }
            groups[key] = record.status to errorList
        }

        return groups.entries
            .sortedByDescending { it.value.second.size }
            .take(limit.toInt())
            .map { (key, pair) ->
                val (method, path, _) = key.split(" ", limit = 3)
                val (status, entries) = pair
                val (lastSeen, lastError) = entries.maxBy { it.first }
                ErrorRecord(
                    path = "$method $path",
                    method = method,
                    status = status,
                    message = if (lastError.isNotEmpty()) lastError else null,
                    count = entries.size.toLong(),
                    lastSeen = lastSeen,
                )
            }
    }

    private fun Double.round(decimals: Int): Double {
        val factor = Math.pow(10.0, decimals.toDouble())
        return kotlin.math.round(this * factor) / factor
    }

    private fun List<Long>.percentile(p: Double): Long {
        if (isEmpty()) return 0L
        val idx = (p * (size - 1)).toInt().coerceIn(0, size - 1)
        return this[idx]
    }

    private fun parseRecord(record: Map<String, Any?>): ParsedRecord? {
        val method = (record["method"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val path = (record["path"] as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val status = (record["status"] as? Number)?.toInt() ?: return null
        val durationMs = (record["durationMs"] as? Number)?.toLong() ?: return null
        val timestamp = (record["timestamp"] as? Number)?.toLong() ?: return null
        val rawPath = (record["rawPath"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        val error = (record["error"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
        return ParsedRecord(
            method = method,
            path = path,
            rawPath = rawPath,
            status = status,
            durationMs = durationMs,
            timestamp = timestamp,
            error = error,
        )
    }
}

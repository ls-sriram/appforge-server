package com.appforge.server.services.usage

import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.infrastructure.sql.NamedSql
import com.appforge.server.providers.transaction.SqlTransactionProvider
import com.appforge.server.providers.transaction.TransactionProvider
import java.sql.Types
import java.sql.Timestamp
import java.time.Instant

enum class UsageMetricKey(val wire: String) {
    ENTITIES("entities"),
    AUDIOS("audios"),
    REVIEWS("reviews"),
    SHARES("shares"),
}

enum class UsageGranularity(val wire: String, val dateTrunc: String) {
    DAY("day", "day"),
    WEEK("week", "week"),
    MONTH("month", "month");

    companion object {
        fun fromWire(value: String?): UsageGranularity? =
            entries.firstOrNull { it.wire.equals(value?.trim(), ignoreCase = true) }
    }
}

data class UsageBucketCount(
    val windowStart: Instant,
    val count: Long,
)

data class UsageMetricSeries(
    val metric: UsageMetricKey,
    val total: Long,
    val buckets: List<UsageBucketCount>,
)

data class UsageSummary(
    val granularity: UsageGranularity,
    val from: Instant?,
    val to: Instant?,
    val series: List<UsageMetricSeries>,
)

interface UsageMetricsRepository {
    suspend fun buckets(
        userId: String,
        metric: UsageMetricKey,
        granularity: UsageGranularity,
        from: Instant?,
        to: Instant?,
    ): List<UsageBucketCount>

    suspend fun total(
        userId: String,
        metric: UsageMetricKey,
        from: Instant?,
        to: Instant?,
    ): Long
}

class SqlUsageMetricsRepository(
    sqlDatabase: ExposedDatabase,
) : UsageMetricsRepository {
    private val sql = NamedSql.fromResource(
        resourcePath = "com/appforge/server/services/usage/usage.sql",
        classLoader = SqlUsageMetricsRepository::class.java.classLoader,
    )
    private val tx: TransactionProvider = SqlTransactionProvider(sqlDatabase)

    override suspend fun buckets(
        userId: String,
        metric: UsageMetricKey,
        granularity: UsageGranularity,
        from: Instant?,
        to: Instant?,
    ): List<UsageBucketCount> {
        val queryName = when (metric) {
            UsageMetricKey.ENTITIES -> "usage.count_entities_buckets"
            UsageMetricKey.AUDIOS -> "usage.count_audios_buckets"
            UsageMetricKey.REVIEWS -> "usage.count_reviews_buckets"
            UsageMetricKey.SHARES -> "usage.count_shares_buckets"
        }
        return tx.read { conn ->
            conn.prepareStatement(sql.query(queryName)).use { stmt ->
                if (metric == UsageMetricKey.ENTITIES) {
                    stmt.setString(1, granularity.dateTrunc)
                    stmt.setString(2, userId)
                    setNullableInstant(stmt, 3, from)
                    setNullableInstant(stmt, 4, from)
                    setNullableInstant(stmt, 5, to)
                    setNullableInstant(stmt, 6, to)
                    stmt.setString(7, granularity.dateTrunc)
                    stmt.setString(8, userId)
                    setNullableInstant(stmt, 9, from)
                    setNullableInstant(stmt, 10, from)
                    setNullableInstant(stmt, 11, to)
                    setNullableInstant(stmt, 12, to)
                } else {
                    stmt.setString(1, granularity.dateTrunc)
                    stmt.setString(2, userId)
                    setNullableInstant(stmt, 3, from)
                    setNullableInstant(stmt, 4, from)
                    setNullableInstant(stmt, 5, to)
                    setNullableInstant(stmt, 6, to)
                }
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(
                                UsageBucketCount(
                                    windowStart = rs.getTimestamp("bucket_start").toInstant(),
                                    count = rs.getLong("count_value"),
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    override suspend fun total(
        userId: String,
        metric: UsageMetricKey,
        from: Instant?,
        to: Instant?,
    ): Long {
        val queryName = when (metric) {
            UsageMetricKey.ENTITIES -> "usage.count_entities_total"
            UsageMetricKey.AUDIOS -> "usage.count_audios_total"
            UsageMetricKey.REVIEWS -> "usage.count_reviews_total"
            UsageMetricKey.SHARES -> "usage.count_shares_total"
        }
        return tx.read { conn ->
            conn.prepareStatement(sql.query(queryName)).use { stmt ->
                if (metric == UsageMetricKey.ENTITIES) {
                    stmt.setString(1, userId)
                    setNullableInstant(stmt, 2, from)
                    setNullableInstant(stmt, 3, from)
                    setNullableInstant(stmt, 4, to)
                    setNullableInstant(stmt, 5, to)
                    stmt.setString(6, userId)
                    setNullableInstant(stmt, 7, from)
                    setNullableInstant(stmt, 8, from)
                    setNullableInstant(stmt, 9, to)
                    setNullableInstant(stmt, 10, to)
                } else {
                    stmt.setString(1, userId)
                    setNullableInstant(stmt, 2, from)
                    setNullableInstant(stmt, 3, from)
                    setNullableInstant(stmt, 4, to)
                    setNullableInstant(stmt, 5, to)
                }
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) 0L else rs.getLong("count_value")
                }
            }
        }
    }

    private fun setNullableInstant(stmt: java.sql.PreparedStatement, index: Int, value: Instant?) {
        if (value == null) {
            stmt.setNull(index, Types.TIMESTAMP_WITH_TIMEZONE)
        } else {
            stmt.setTimestamp(index, Timestamp.from(value))
        }
    }
}

interface UsageMetricsService {
    suspend fun usageSummary(
        userId: String,
        granularity: UsageGranularity,
        from: Instant?,
        to: Instant?,
    ): UsageSummary
}

class UsageMetricsServiceImpl(
    private val repository: UsageMetricsRepository,
) : UsageMetricsService {
    override suspend fun usageSummary(
        userId: String,
        granularity: UsageGranularity,
        from: Instant?,
        to: Instant?,
    ): UsageSummary {
        val series = UsageMetricKey.entries.map { metric ->
            UsageMetricSeries(
                metric = metric,
                total = repository.total(userId, metric, from, to),
                buckets = repository.buckets(userId, metric, granularity, from, to),
            )
        }
        return UsageSummary(
            granularity = granularity,
            from = from,
            to = to,
            series = series,
        )
    }
}

package com.appforge.server.services.usage

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class UsageMetricsServiceTest {
    @Test
    fun `usage summary returns series for all metrics`() = kotlinx.coroutines.runBlocking {
        val repository = object : UsageMetricsRepository {
            override suspend fun buckets(
                userId: String,
                metric: UsageMetricKey,
                granularity: UsageGranularity,
                from: Instant?,
                to: Instant?,
            ): List<UsageBucketCount> {
                return listOf(
                    UsageBucketCount(
                        windowStart = Instant.parse("2026-05-01T00:00:00Z"),
                        count = 3L
                    )
                )
            }

            override suspend fun total(
                userId: String,
                metric: UsageMetricKey,
                from: Instant?,
                to: Instant?,
            ): Long = 7L
        }

        val service = UsageMetricsServiceImpl(repository)
        val summary = service.usageSummary(
            userId = "user-1",
            granularity = UsageGranularity.DAY,
            from = Instant.parse("2026-05-01T00:00:00Z"),
            to = Instant.parse("2026-05-31T00:00:00Z"),
        )

        assertEquals(UsageGranularity.DAY, summary.granularity)
        assertEquals(4, summary.series.size)
        summary.series.forEach { series ->
            assertEquals(7L, series.total)
            assertEquals(1, series.buckets.size)
            assertEquals(3L, series.buckets.first().count)
        }
    }
}


package com.appforge.server.services.analytics

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class AnalyticsUseCasesTest {
    @Test
    fun `getAnalytics ignores malformed records`() = runBlocking {
        val repository = mockRepository(
            listOf(
                mapOf(
                    "timestamp" to 1_700_000_000_000.0,
                    "method" to "GET",
                    "path" to "/health",
                    "rawPath" to "/health",
                    "status" to 200.0,
                    "durationMs" to 12.0,
                ),
                mapOf( // malformed: missing method
                    "timestamp" to 1_700_000_000_100.0,
                    "path" to "/broken",
                    "status" to 500.0,
                    "durationMs" to 50.0,
                ),
            )
        )

        val useCases = AnalyticsUseCases(repository)
        val result = useCases.getAnalytics(com.appforge.server.services.analytics.models.AnalyticsQuery(windowMinutes = 60, limit = 10))

        assertEquals(1, result.summary.totalCalls)
        assertEquals(100.0, result.summary.successRate)
        assertEquals(1, result.topRoutes.size)
    }

    private fun mockRepository(records: List<Map<String, Any?>>): AnalyticsRepository {
        val repository = mockk<AnalyticsRepository>()
        coEvery { repository.getAllSince(any()) } returns records
        return repository
    }
}

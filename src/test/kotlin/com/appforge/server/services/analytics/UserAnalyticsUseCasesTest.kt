package com.appforge.server.services.analytics

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class UserAnalyticsUseCasesTest {
    @Test
    fun `getUserAnalytics ignores malformed records and filters by user`() = runBlocking {
        val repository = mockk<AnalyticsRepository>()
        coEvery { repository.getAllSince(any()) } returns listOf(
            mapOf(
                "userId" to "user-1",
                "timestamp" to 1_700_000_000_000.0,
                "method" to "POST",
                "path" to "/api/v1/login",
                "rawPath" to "/api/v1/login",
                "status" to 200.0,
                "durationMs" to 20.0,
            ),
            mapOf( // malformed: missing path
                "userId" to "user-1",
                "timestamp" to 1_700_000_000_200.0,
                "method" to "GET",
                "status" to 500.0,
                "durationMs" to 90.0,
            ),
            mapOf( // different user
                "userId" to "user-2",
                "timestamp" to 1_700_000_000_300.0,
                "method" to "GET",
                "path" to "/api/v1/data",
                "status" to 200.0,
                "durationMs" to 10.0,
            ),
        )

        val useCases = UserAnalyticsUseCases(repository)
        val result = useCases.getUserAnalytics("user-1", 60, 50)

        assertEquals(1, result.summary.totalCalls)
        assertEquals(1, result.summary.loginCount)
        assertEquals(0, result.summary.errorCount)
        assertEquals(1, result.activity.size)
    }
}

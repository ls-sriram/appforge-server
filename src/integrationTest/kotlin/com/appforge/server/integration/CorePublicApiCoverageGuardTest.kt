package com.appforge.server.integration

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class CorePublicApiCoverageGuardTest {

    private val expected = setOf(
        "GET /health",
        "POST /api/v1/session/early-access/check",
        "POST /api/v1/session/early-access/join",
        "GET /api/v1/session/early-access/status",
        "GET /api/v1/session/me",
        "POST /api/v1/session/login",
        "POST /api/v1/session/logout",
        "POST /api/v1/session/password/reset-link",
        "POST /api/v1/signup/init",
        "POST /api/v1/signup/finalize",
        "GET /api/v1/onboarding/flow",
        "GET /api/v1/users/me",
        "PUT /api/v1/users/me",
        "DELETE /api/v1/users/me",
        "GET /api/v1/billing/pricing-cards",
        "GET /api/v1/billing/entitlement",
        "POST /api/v1/billing/checkout",
        "POST /api/v1/billing/subscription/cancel",
        "POST /api/v1/billing/webhook/dodo",
        "POST /api/v1/uploads/init",
        "GET /api/v1/uploads/access/{assetId}",
        "POST /api/v1/upload-events/complete",
        "GET /api/v1/reviews",
        "GET /api/v1/entities/{type}/{id}/reviews",
        "POST /api/v1/entities/{type}/{id}/ai-review",
        "POST /api/v1/entities/{type}/{id}/shares",
        "GET /api/v1/entities/{type}/{id}/shares",
        "POST /api/v1/entities/{type}/{id}/shares/{token}/revoke",
        "POST /api/v1/entities/{type}/{id}/shares/{token}/email",
        "GET /shares/{token}",
        "POST /shares/{token}/reviews",
    )

    @Test
    fun `coverage matrix is complete for core public routes`() {
        val root = File(System.getProperty("user.dir"))
        val matrix = root.resolve("docs/testing/api-integration-coverage.md")
        val lines = matrix.readLines()
        val actual = lines
            .asSequence()
            .filter { it.startsWith("|") }
            .map { it.trim() }
            .filter { !it.startsWith("|---") }
            .filter { !it.startsWith("| Method") }
            .map { row -> row.split("|").map { it.trim() }.filter { it.isNotEmpty() } }
            .map { cols -> "${cols[0]} ${cols[1]}" }
            .toSet()

        assertEquals(expected, actual, "Core/public API coverage matrix is out of sync with route inventory")
    }
}

package com.appforge.server.services.billing.repository

import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.services.billing.models.BillingAuditRecord
import java.time.Instant
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.Test
import kotlin.test.assertTrue

class BillingAuditRepositoryTest {
    companion object {
        private var postgres: PostgreSQLContainer<Nothing>? = null
    }

    private fun createRepository(): Pair<BillingAuditRepository, ExposedDatabase> {
        if (postgres == null) {
            postgres = runCatching {
                PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:16-alpine")).apply {
                    withDatabaseName("testdb_billing_audit_repo")
                    withUsername("test")
                    withPassword("test")
                    start()
                }
            }.getOrNull()
        }
        assumeTrue(postgres != null, "Docker/Testcontainers unavailable, skipping SQL repository test")
        val db = ExposedDatabase(
            connectionUrl = postgres!!.jdbcUrl,
            username = postgres!!.username,
            password = postgres!!.password,
            poolSize = 2,
        )
        db.runMigrations()
        return BillingAuditRepository(db) to db
    }

    @Test
    fun `record creates audit entry`() = kotlinx.coroutines.runBlocking {
        val (repo, db) = createRepository()
        val id = "wh-${System.nanoTime()}"
        val record = BillingAuditRecord(
            payload = "{\"k\":\"v\"}",
            timestamp = Instant.parse("2024-01-01T00:00:00Z"),
            webhookId = id,
            source = "dodo_payments",
        )

        repo.record(record)

        val exists = db.withConnection { conn ->
            conn.prepareStatement(
                "SELECT 1 FROM billing_audit_records WHERE id = ?"
            ).use { stmt ->
                stmt.setString(1, id)
                stmt.executeQuery().use { rs -> rs.next() }
            }
        }
        assertTrue(exists)
    }
}

package com.appforge.server.services.earlyaccess.repository

import com.appforge.server.infrastructure.ExposedDatabase
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EarlyAccessRepositoryTest {
    companion object {
        private var postgres: PostgreSQLContainer<Nothing>? = null
    }

    private fun createRepository(): EarlyAccessRepository {
        if (postgres == null) {
            postgres = runCatching {
                PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:16-alpine")).apply {
                    withDatabaseName("testdb_auth_early_access")
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
        return EarlyAccessRepository(db)
    }

    @Test
    fun `isEmailApproved checks status`() = kotlinx.coroutines.runBlocking {
        val repo = createRepository()
        val email = "ea_${System.nanoTime()}@example.com"

        assertFalse(repo.isEmailApproved(email))
        assertTrue(repo.joinWaitlist(email))
        assertFalse(repo.isEmailApproved(email))
        val approval = repo.approveAccess(email)
        assertNotNull(approval)
        assertTrue(repo.isEmailApproved(email))
    }

    @Test
    fun `joinWaitlist is idempotent`() = kotlinx.coroutines.runBlocking {
        val repo = createRepository()
        val email = "ea_${System.nanoTime()}@example.com"
        assertTrue(repo.joinWaitlist(email))
        assertTrue(repo.joinWaitlist(email))
    }
}

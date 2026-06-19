package com.appforge.server.services.reviews.repository

import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.infrastructure.Resource
import com.appforge.server.services.reviews.models.Profile
import java.time.Instant
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProfileRepositoryTest {
    companion object {
        private var postgres: PostgreSQLContainer<Nothing>? = null
    }

    private lateinit var db: ExposedDatabase

    private fun createRepository(): ProfileRepository {
        if (postgres == null) {
            postgres = runCatching {
                PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:16-alpine")).apply {
                    withDatabaseName("testdb_profile_repo")
                    withUsername("test")
                    withPassword("test")
                    start()
                }
            }.getOrNull()
        }
        assumeTrue(postgres != null, "Docker/Testcontainers unavailable, skipping SQL repository test")
        db = ExposedDatabase(
            connectionUrl = postgres!!.jdbcUrl,
            username = postgres!!.username,
            password = postgres!!.password,
            poolSize = 2,
        )
        db.runMigrations()
        return ProfileRepository(db)
    }

    @Test
    fun `upsert and getByEmail work`() = kotlinx.coroutines.runBlocking {
        val repo = createRepository()
        val userId = "u_${System.nanoTime()}"
        val email = "$userId@example.com"
        dbSeedUser(userId, email)
        val profile = Profile(
            userId = userId,
            displayName = "Alice",
            email = email,
            emailNormalized = email.lowercase(),
            lastSeenAt = Instant.parse("2024-01-01T00:00:00Z"),
        )

        val upsert = repo.upsert(profile)
        assertTrue(upsert is Resource.Success)

        val byEmail = repo.getByEmail(email.uppercase())
        assertTrue(byEmail is Resource.Success)
        val loaded = byEmail.data
        assertNotNull(loaded)
        assertEquals(profile.userId, loaded.userId)
        assertEquals(profile.emailNormalized, loaded.emailNormalized)
    }

    private suspend fun dbSeedUser(uid: String, email: String) {
        db.withConnection { conn ->
            conn.prepareStatement(
                """
                INSERT INTO app_users (uid, email, email_normalized, display_name, created_at, last_login_at)
                VALUES (?, ?, ?, ?, NOW(), NOW())
                ON CONFLICT (uid) DO NOTHING
                """.trimIndent()
            ).use { stmt ->
                stmt.setString(1, uid)
                stmt.setString(2, email)
                stmt.setString(3, email.lowercase())
                stmt.setString(4, uid)
                stmt.executeUpdate()
            }
        }
    }
}

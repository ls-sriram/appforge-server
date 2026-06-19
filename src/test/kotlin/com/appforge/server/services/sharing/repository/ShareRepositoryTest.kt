package com.appforge.server.services.sharing.repository

import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.infrastructure.Resource
import com.appforge.server.services.reviews.models.EntityCategory
import com.appforge.server.services.sharing.models.Share
import com.appforge.server.services.sharing.models.ShareAccessMode
import java.time.Instant
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShareRepositoryTest {
    companion object {
        private var postgres: PostgreSQLContainer<Nothing>? = null
    }

    private fun createRepository(): Pair<ShareRepository, ExposedDatabase> {
        if (postgres == null) {
            postgres = runCatching {
                PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:16-alpine")).apply {
                    withDatabaseName("testdb_share_repo")
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
        return ShareRepository(db) to db
    }

    private suspend fun seedUser(db: ExposedDatabase, uid: String, email: String) {
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

    @Test
    fun `create and get share`() = kotlinx.coroutines.runBlocking {
        val (repo, db) = createRepository()
        val ownerId = "u_${System.nanoTime()}"
        seedUser(db, ownerId, "$ownerId@example.com")

        val token = "token_${System.nanoTime()}"
        val share = Share(
            id = token,
            token = token,
            entityId = "e1",
            entityCategory = EntityCategory("document"),
            accessMode = ShareAccessMode.PUBLIC_LINK,
            ownerId = ownerId,
            tokenHash = "hash_${System.nanoTime()}",
            expiresAt = Instant.parse("2030-01-01T00:00:00Z"),
            createdAt = Instant.now(),
            createdBy = ownerId,
            revokedAt = null,
            revokedBy = null,
        )

        val created = repo.create(share)
        assertTrue(created is Resource.Success)

        val fetched = repo.getByTokenHash(share.tokenHash)
        assertTrue(fetched is Resource.Success)
        assertEquals(share.token, fetched.data.token)
        assertEquals(share.ownerId, fetched.data.ownerId)
    }
}

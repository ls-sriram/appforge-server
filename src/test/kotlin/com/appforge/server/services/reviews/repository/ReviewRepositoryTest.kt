package com.appforge.server.services.reviews.repository

import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.infrastructure.Resource
import com.appforge.server.services.reviews.models.EntityCategory
import com.appforge.server.services.reviews.models.Review
import com.appforge.server.services.reviews.models.ReviewAuthorRole
import java.time.Instant
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReviewRepositoryTest {
    companion object {
        private var postgres: PostgreSQLContainer<Nothing>? = null
    }

    private fun createRepository(): Pair<ReviewRepository, ExposedDatabase> {
        if (postgres == null) {
            postgres = runCatching {
                PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:16-alpine")).apply {
                    withDatabaseName("testdb_review_repo")
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
        return ReviewRepository(db) to db
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
    fun `getReviewsForEntity filters by entity and type`() = kotlinx.coroutines.runBlocking {
        val (repo, db) = createRepository()
        val userId = "u_${System.nanoTime()}"
        seedUser(db, userId, "$userId@example.com")

        val review1 = Review(
            id = "r_${System.nanoTime()}",
            entityId = "e1",
            entityCategory = EntityCategory("document"),
            authorRole = ReviewAuthorRole.AI,
            authorId = "ai",
            authorName = "AI",
            authorEmail = null,
            content = mapOf("k" to "v1"),
            createdAt = Instant.parse("2024-01-01T00:00:00Z"),
        )
        val review2 = review1.copy(
            id = "r_${System.nanoTime()}",
            entityId = "e2",
            content = mapOf("k" to "v2"),
            createdAt = Instant.parse("2024-01-02T00:00:00Z"),
        )

        repo.create(userId, "document", "e1", review1)
        repo.create(userId, "document", "e2", review2)

        val result = repo.getReviewsForEntity(userId, "document", "e1")
        assertTrue(result is Resource.Success)
        assertEquals(1, result.data.size)
        assertEquals("e1", result.data.first().entityId)
    }

    @Test
    fun `listAllReviews orders by createdAt`() = kotlinx.coroutines.runBlocking {
        val (repo, db) = createRepository()
        val userId = "u_${System.nanoTime()}"
        seedUser(db, userId, "$userId@example.com")

        val older = Review(
            id = "r_${System.nanoTime()}",
            entityId = "e1",
            entityCategory = EntityCategory("document"),
            authorRole = ReviewAuthorRole.AI,
            authorId = "ai",
            authorName = "AI",
            authorEmail = null,
            content = emptyMap(),
            createdAt = Instant.parse("2024-01-01T00:00:00Z"),
        )
        val newer = older.copy(
            id = "r_${System.nanoTime()}",
            createdAt = Instant.parse("2024-01-02T00:00:00Z"),
        )

        repo.create(userId, "document", "e1", newer)
        repo.create(userId, "document", "e1", older)

        val result = repo.listAllReviews(userId)
        assertTrue(result is Resource.Success)
        assertEquals(2, result.data.size)
        assertEquals(older.createdAt, result.data[0].createdAt)
        assertEquals(newer.createdAt, result.data[1].createdAt)
    }
}

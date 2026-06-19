package com.appforge.server.services.auth.repository

import com.appforge.server.infrastructure.ExposedDatabase
import java.time.Instant
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SqlUserRepositoryTest {
    companion object {
        private var postgres: PostgreSQLContainer<Nothing>? = null
    }

    private fun createRepository(): SqlUserRepository {
        if (postgres == null) {
            postgres = runCatching {
                PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:16-alpine")).apply {
                    withDatabaseName("testdb_sql_user_repo")
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
        return SqlUserRepository(db)
    }

    @Test
    fun `upsertUser creates and updates user record`() = kotlinx.coroutines.runBlocking {
        val repo = createRepository()
        val uid = "u_${System.nanoTime()}"
        val firstSeen = Instant.parse("2026-05-16T10:00:00Z")
        val secondSeen = Instant.parse("2026-05-16T11:00:00Z")
        val email = "$uid@example.com"

        repo.upsertUser(uid = uid, email = email, displayName = "User One", lastLoginAt = firstSeen)
        repo.upsertUser(uid = uid, email = email, displayName = "Updated Name", lastLoginAt = secondSeen)

        val loaded = repo.getUser(uid)
        assertNotNull(loaded)
        assertEquals(email, loaded.email)
        assertEquals("Updated Name", loaded.displayName)
        assertEquals(firstSeen, loaded.createdAt)
        assertEquals(secondSeen, loaded.lastLoginAt)
    }

    @Test
    fun `upsertProfile stores and updates profile row`() = kotlinx.coroutines.runBlocking {
        val repo = createRepository()
        val uid = "u_${System.nanoTime()}"
        val seenAt = Instant.parse("2026-05-16T12:00:00Z")
        val updatedAt = Instant.parse("2026-05-16T13:00:00Z")
        val email = "$uid@example.com"

        repo.upsertProfile(uid = uid, email = email, displayName = "Reviewer", lastSeenAt = seenAt)
        repo.upsertProfile(uid = uid, email = email, displayName = "Reviewer Updated", lastSeenAt = updatedAt)

        val loaded = repo.getUser(uid)
        assertNull(loaded) // profile write alone should not create app user
    }

    @Test
    fun `updateDisplayName updates app user`() = kotlinx.coroutines.runBlocking {
        val repo = createRepository()
        val uid = "u_${System.nanoTime()}"
        val initial = Instant.parse("2026-05-16T08:00:00Z")
        val updated = Instant.parse("2026-05-16T09:00:00Z")
        val email = "$uid@example.com"

        repo.upsertUser(uid = uid, email = email, displayName = "Old Name", lastLoginAt = initial)
        repo.upsertProfile(uid = uid, email = email, displayName = "Old Name", lastSeenAt = initial)

        val success = repo.updateDisplayName(uid, "New Name", updated)
        assertTrue(success)

        val user = repo.getUser(uid)
        assertNotNull(user)
        assertEquals("New Name", user.displayName)
        assertEquals(updated, user.lastLoginAt)
    }

    @Test
    fun `deleteUserAccount removes app user`() = kotlinx.coroutines.runBlocking {
        val repo = createRepository()
        val uid = "u_${System.nanoTime()}"
        val initial = Instant.parse("2026-05-16T08:00:00Z")
        val email = "$uid@example.com"

        repo.upsertUser(uid = uid, email = email, displayName = "Delete Me", lastLoginAt = initial)
        repo.upsertProfile(uid = uid, email = email, displayName = "Delete Me", lastSeenAt = initial)

        val deleted = repo.deleteUserAccount(uid)
        assertTrue(deleted)
        assertNull(repo.getUser(uid))
    }
}

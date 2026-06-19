package com.appforge.server.services.recordings.repository

import com.appforge.server.infrastructure.ExposedDatabase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecordingRepositoryTest {
    companion object {
        private var postgres: PostgreSQLContainer<Nothing>? = null
    }

    private lateinit var db: ExposedDatabase
    private lateinit var repo: SqlRecordingRepository

    @BeforeAll
    fun setUpAll() {
        if (postgres == null) {
            postgres = runCatching {
                PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:16-alpine")).apply {
                    withDatabaseName("testdb_recording_repo")
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
        repo = SqlRecordingRepository(db)
    }

    @AfterAll
    fun tearDownAll() {
        postgres?.stop()
        postgres = null
    }

    @BeforeEach
    fun cleanTables() = runBlocking {
        db.withConnection { conn ->
            conn.prepareStatement("TRUNCATE TABLE recordings, app_users RESTART IDENTITY CASCADE").use { stmt ->
                stmt.executeUpdate()
            }
            Unit
        }
    }

    private suspend fun seedUser(uid: String, email: String) {
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
    fun `create and fetch recording content by id and uid`() = runBlocking {
        val uid = "u_${System.nanoTime()}"
        seedUser(uid, "$uid@example.com")
        val id = "rec_${System.nanoTime()}"
        val bytes = "audio-a".toByteArray()

        val created = repo.create(
            id = id,
            uid = uid,
            audioBytes = bytes,
            contentType = "audio/webm",
            durationSeconds = 15,
        )

        assertEquals(id, created.id)
        assertEquals(uid, created.uid)
        assertEquals("audio/webm", created.contentType)
        assertEquals(bytes.size.toLong(), created.sizeBytes)
        assertEquals(15, created.durationSeconds)

        val loaded = repo.getByIdAndUser(id, uid)
        assertNotNull(loaded)
        assertEquals(bytes.toList(), loaded.audioBytes.toList())
        assertEquals(id, loaded.metadata.id)
    }

    @Test
    fun `listByUser returns newest first and only caller uid`() = runBlocking {
        val userA = "u_${System.nanoTime()}_a"
        val userB = "u_${System.nanoTime()}_b"
        seedUser(userA, "$userA@example.com")
        seedUser(userB, "$userB@example.com")

        repo.create(
            id = "rec_a_1_${System.nanoTime()}",
            uid = userA,
            audioBytes = "one".toByteArray(),
            contentType = "audio/webm",
            durationSeconds = 5,
        )
        Thread.sleep(5)
        val newestId = "rec_a_2_${System.nanoTime()}"
        repo.create(
            id = newestId,
            uid = userA,
            audioBytes = "two".toByteArray(),
            contentType = "audio/webm",
            durationSeconds = 7,
        )
        repo.create(
            id = "rec_b_1_${System.nanoTime()}",
            uid = userB,
            audioBytes = "three".toByteArray(),
            contentType = "audio/webm",
            durationSeconds = 11,
        )

        val list = repo.listByUser(userA, limit = 20)
        assertEquals(2, list.size)
        assertEquals(newestId, list.first().id)
        assertEquals(userA, list.first().uid)
        assertEquals(userA, list.last().uid)
    }

    @Test
    fun `getByIdAndUser returns null for non-owner`() = runBlocking {
        val owner = "u_${System.nanoTime()}_owner"
        val other = "u_${System.nanoTime()}_other"
        seedUser(owner, "$owner@example.com")
        seedUser(other, "$other@example.com")
        val id = "rec_${System.nanoTime()}"
        repo.create(
            id = id,
            uid = owner,
            audioBytes = "secret".toByteArray(),
            contentType = "audio/webm",
            durationSeconds = null,
        )

        assertNull(repo.getByIdAndUser(id, other))
    }
}

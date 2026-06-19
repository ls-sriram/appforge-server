package com.appforge.server.services.uploads.repository

import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.services.uploads.UploadRecord
import com.appforge.server.services.uploads.UploadStatus
import com.appforge.server.services.uploads.UploadType
import java.time.Instant
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
class UploadMetadataRepositoryTest {
    companion object {
        private var postgres: PostgreSQLContainer<Nothing>? = null
    }

    private lateinit var db: ExposedDatabase
    private lateinit var repo: UploadMetadataRepositoryImpl

    @BeforeAll
    fun setUpAll() {
        if (postgres == null) {
            postgres = runCatching {
                PostgreSQLContainer<Nothing>(DockerImageName.parse("postgres:16-alpine")).apply {
                    withDatabaseName("testdb_upload_metadata_repo")
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
        repo = UploadMetadataRepositoryImpl(db)
    }

    @AfterAll
    fun tearDownAll() {
        postgres?.stop()
        postgres = null
    }

    @BeforeEach
    fun cleanTables() {
        runBlocking {
            db.withConnection { conn ->
                conn.prepareStatement("TRUNCATE TABLE upload_records, app_users RESTART IDENTITY CASCADE").use { stmt ->
                    stmt.executeUpdate()
                }
                Unit
            }
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
    fun `markCompleted updates persisted upload metadata`() = runBlocking {
        val uid = "u_${System.nanoTime()}"
        seedUser(uid, "$uid@example.com")
        val uploadId = "up_${System.nanoTime()}"
        val now = Instant.parse("2026-05-20T20:00:00Z").toEpochMilli()
        val expires = Instant.parse("2026-05-20T21:00:00Z").toEpochMilli()
        val objectName = "users/$uid/entities/entity_1/uploads/$uploadId.webm"
        val record = UploadRecord(
            uploadId = uploadId,
            assetId = uploadId,
            uid = uid,
            type = UploadType.AUDIO,
            entityId = "entity_1",
            bucket = "bucket-a",
            objectName = objectName,
            contentType = "audio/webm",
            sizeBytes = 1024,
            status = UploadStatus.PENDING,
            createdAtTimestamp = now,
            expiresAtTimestamp = expires,
        )

        repo.createPending(record)
        repo.markCompleted(
            uploadId = uploadId,
            generation = 1L,
            sizeBytes = 2048L,
            contentType = "audio/mp4",
            completedAtTimestamp = now + 1000L,
            eventTimeEpochSeconds = null,
        )

        val loaded = repo.getByAssetId(uploadId)
        assertNotNull(loaded)
        assertEquals(UploadStatus.COMPLETED, loaded.status)
        assertEquals(2048L, loaded.sizeBytes)
        assertEquals("audio/mp4", loaded.contentType)
        assertEquals(uploadId, loaded.uploadId)
        assertEquals("bucket-a", loaded.bucket)
        assertEquals(objectName, loaded.objectName)
        assertEquals(expires, loaded.expiresAtTimestamp)
    }

    @Test
    fun `getByObjectName returns latest upload with deterministic tie break`() = runBlocking {
        val uid = "u_${System.nanoTime()}"
        seedUser(uid, "$uid@example.com")
        val objectName = "users/$uid/entities/entity_1/uploads/shared.webm"
        val firstId = "up_${System.nanoTime()}_1"
        val secondId = "up_${System.nanoTime()}_2"
        val sameCreatedAt = Instant.parse("2026-05-20T11:00:00Z").toEpochMilli()

        repo.createPending(
            UploadRecord(
                uploadId = firstId,
                assetId = firstId,
                uid = uid,
                type = UploadType.AUDIO,
                entityId = "entity_1",
                bucket = "bucket-a",
                objectName = objectName,
                contentType = "audio/webm",
                sizeBytes = 100,
                status = UploadStatus.PENDING,
                createdAtTimestamp = sameCreatedAt,
                expiresAtTimestamp = Instant.parse("2026-05-20T12:00:00Z").toEpochMilli(),
            )
        )
        repo.createPending(
            UploadRecord(
                uploadId = secondId,
                assetId = secondId,
                uid = uid,
                type = UploadType.AUDIO,
                entityId = "entity_1",
                bucket = "bucket-a",
                objectName = objectName,
                contentType = "audio/webm",
                sizeBytes = 120,
                status = UploadStatus.PENDING,
                createdAtTimestamp = sameCreatedAt,
                expiresAtTimestamp = Instant.parse("2026-05-20T13:00:00Z").toEpochMilli(),
            )
        )

        val loaded = repo.getByObjectName(objectName)
        assertNotNull(loaded)
        assertEquals(secondId, loaded.uploadId)
    }

    @Test
    fun `getByAssetId returns null for missing upload`() = runBlocking {
        assertNull(repo.getByAssetId("missing_upload"))
    }

    @Test
    fun `markCompleted on missing upload is a no-op`() = runBlocking {
        repo.markCompleted(
            uploadId = "missing_upload",
            generation = 1L,
            sizeBytes = 100L,
            contentType = "audio/webm",
            completedAtTimestamp = Instant.now().toEpochMilli(),
            eventTimeEpochSeconds = null,
        )
        assertNull(repo.getByAssetId("missing_upload"))
    }
}

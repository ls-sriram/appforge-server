package com.appforge.server.infrastructure

import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.*

/**
 * Integration tests for [ExposedDatabase] against a real PostgreSQL instance.
 * Uses Testcontainers to spin up a temporary PostgreSQL database.
 *
 * Prerequisites: Docker must be installed and running.
 *
 * Run with: ./gradlew test --tests "ExposedDatabaseIntegrationTest"
 */
@org.junit.jupiter.api.Disabled("Legacy document-store integration tests are obsolete after SQL-only persistence migration.")
class ExposedDatabaseIntegrationTest {

    companion object {
        private val postgres = PostgreSQLContainer<Nothing>(
            DockerImageName.parse("postgres:16-alpine")
        ).apply {
            withDatabaseName("testdb")
            withUsername("test")
            withPassword("test")
            start()
        }

        @JvmStatic
        @org.junit.AfterClass
        fun stopDb() {
            postgres.stop()
        }
    }

    private fun createDatabase(): ExposedDatabase {
        return ExposedDatabase(
            connectionUrl = postgres.jdbcUrl,
            username = postgres.username,
            password = postgres.password,
            poolSize = 2,
        ).also { it.runMigrations() }
    }

    // ─── Create / Get ────────────────────────────────────────────────────

    @Test
    fun `create and get document`() = runBlocking {
        val db = createDatabase()
        val data = mapOf("name" to "Alice", "age" to 30.0, "active" to true)
        db.create("users", "user-1", data)

        val result = db.get("users", "user-1")
        assertTrue(result is Resource.Success)
        assertEquals("Alice", result.data["name"])
        assertEquals(30.0, result.data["age"])
        assertEquals(true, result.data["active"])
    }

    @Test
    fun `get returns error for missing document`() = runBlocking {
        val db = createDatabase()
        val result = db.get("users", "nonexistent")
        assertTrue(result is Resource.Error)
    }

    @Test
    fun `create fails for duplicate id`() = runBlocking {
        val db = createDatabase()
        db.create("users", "user-1", mapOf("name" to "Alice"))
        val result = db.create("users", "user-1", mapOf("name" to "Bob"))
        assertTrue(result is Resource.Error)
    }

    // ─── Update ──────────────────────────────────────────────────────────

    @Test
    fun `update replaces document`() = runBlocking {
        val db = createDatabase()
        db.create("users", "user-1", mapOf("name" to "Alice"))
        db.update("users", "user-1", mapOf("name" to "Bob", "role" to "admin"))

        val result = db.get("users", "user-1")
        assertTrue(result is Resource.Success)
        assertEquals("Bob", result.data["name"])
        assertEquals("admin", result.data["role"])
        assertNull(result.data["age"])
    }

    // ─── Delete ──────────────────────────────────────────────────────────

    @Test
    fun `delete removes document`() = runBlocking {
        val db = createDatabase()
        db.create("users", "user-1", mapOf("name" to "Alice"))
        db.delete("users", "user-1")

        val result = db.get("users", "user-1")
        assertTrue(result is Resource.Error)
    }

    @Test
    fun `delete is idempotent`() = runBlocking {
        val db = createDatabase()
        db.delete("users", "nonexistent")
    }

    // ─── Merge ───────────────────────────────────────────────────────────

    @Test
    fun `merge creates if missing`() = runBlocking {
        val db = createDatabase()
        db.merge("users", "user-1", mapOf("name" to "Alice"))

        val result = db.get("users", "user-1")
        assertTrue(result is Resource.Success)
        assertEquals("Alice", result.data["name"])
    }

    @Test
    fun `merge updates existing document preserving fields`() = runBlocking {
        val db = createDatabase()
        db.create("users", "user-1", mapOf("name" to "Alice", "age" to 30.0))
        db.merge("users", "user-1", mapOf("age" to 31.0, "role" to "admin"))

        val result = db.get("users", "user-1")
        assertTrue(result is Resource.Success)
        assertEquals("Alice", result.data["name"])
        assertEquals(31.0, result.data["age"])
        assertEquals("admin", result.data["role"])
    }

    // ─── Set If Absent ───────────────────────────────────────────────────

    @Test
    fun `setIfAbsent creates when missing`() = runBlocking {
        val db = createDatabase()
        val result = db.setIfAbsent("users", "user-1", mapOf("name" to "Alice"))
        assertTrue(result is Resource.Success)
        assertTrue(result.data)
    }

    @Test
    fun `setIfAbsent returns false when exists`() = runBlocking {
        val db = createDatabase()
        db.create("users", "user-1", mapOf("name" to "Alice"))
        val result = db.setIfAbsent("users", "user-1", mapOf("name" to "Bob"))
        assertTrue(result is Resource.Success)
        assertFalse(result.data)

        val doc = db.get("users", "user-1")
        assertTrue(doc is Resource.Success)
        assertEquals("Alice", doc.data["name"])
    }

    // ─── Find First By Field ─────────────────────────────────────────────

    @Test
    fun `findFirstByField returns matching document`() = runBlocking {
        val db = createDatabase()
        db.create("users", "user-1", mapOf("name" to "Alice", "role" to "admin"))
        db.create("users", "user-2", mapOf("name" to "Bob", "role" to "user"))

        val result = db.findFirstByField("users", "role", "admin")
        assertTrue(result is Resource.Success)
        val found = result.data
        assertNotNull(found)
        assertEquals("Alice", found["name"])
    }

    @Test
    fun `findFirstByField returns null for no match`() = runBlocking {
        val db = createDatabase()
        db.create("users", "user-1", mapOf("name" to "Alice"))

        val result = db.findFirstByField("users", "role", "nonexistent")
        assertTrue(result is Resource.Success)
        assertNull(result.data)
    }

    // ─── Query ───────────────────────────────────────────────────────────

    @Test
    fun `query returns all documents in collection`() = runBlocking {
        val db = createDatabase()
        db.create("users", "user-1", mapOf("name" to "Alice"))
        db.create("users", "user-2", mapOf("name" to "Bob"))
        db.create("other", "other-1", mapOf("name" to "Charlie"))

        val result = db.query("users")
        assertTrue(result is Resource.Success)
        assertEquals(2, result.data.size)
    }

    @Test
    fun `query with filters`() = runBlocking {
        val db = createDatabase()
        db.create("users", "user-1", mapOf("name" to "Alice", "role" to "admin"))
        db.create("users", "user-2", mapOf("name" to "Bob", "role" to "user"))
        db.create("users", "user-3", mapOf("name" to "Charlie", "role" to "admin"))

        val result = db.query("users", DatabaseQuery(filters = mapOf("role" to "admin")))
        assertTrue(result is Resource.Success)
        assertEquals(2, result.data.size)
    }

    @Test
    fun `query with limit`() = runBlocking {
        val db = createDatabase()
        db.create("users", "user-1", mapOf("name" to "Alice"))
        db.create("users", "user-2", mapOf("name" to "Bob"))
        db.create("users", "user-3", mapOf("name" to "Charlie"))

        val result = db.query("users", DatabaseQuery(limit = 2))
        assertTrue(result is Resource.Success)
        assertEquals(2, result.data.size)
    }

    // ─── Transactions ────────────────────────────────────────────────────

    @Test
    fun `transaction executes atomically`() = runBlocking {
        val db = createDatabase()
        val result = db.transaction {
            create("users", "user-1", mapOf("name" to "Alice"))
            create("users", "user-2", mapOf("name" to "Bob"))
            val doc = get("users", "user-1")
            doc!!
        }
        assertTrue(result is Resource.Success)
        assertEquals("Alice", result.data["name"])

        val doc1 = db.get("users", "user-1")
        val doc2 = db.get("users", "user-2")
        assertTrue(doc1 is Resource.Success)
        assertTrue(doc2 is Resource.Success)
    }

    // ─── Collection isolation ────────────────────────────────────────────

    @Test
    fun `collections are isolated from each other`() = runBlocking {
        val db = createDatabase()
        db.create("users", "item-1", mapOf("name" to "Alice"))
        db.create("products", "item-1", mapOf("name" to "Widget"))

        val user = db.get("users", "item-1")
        val product = db.get("products", "item-1")
        assertTrue(user is Resource.Success)
        assertTrue(product is Resource.Success)
        assertEquals("Alice", user.data["name"])
        assertEquals("Widget", product.data["name"])
    }

    // ─── JSONB round-trip ────────────────────────────────────────────────

    @Test
    fun `nested JSON data round-trips correctly`() = runBlocking {
        val db = createDatabase()
        val data = mapOf(
            "name" to "Alice",
            "address" to mapOf(
                "city" to "NYC",
                "coords" to listOf(40.7128, -74.0060),
            ),
            "tags" to listOf("admin", "active"),
            "score" to null,
        )
        db.create("users", "user-nested", data)

        val result = db.get("users", "user-nested")
        assertTrue(result is Resource.Success)
        assertEquals("Alice", result.data["name"])
        assertEquals(mapOf("city" to "NYC", "coords" to listOf(40.7128, -74.0060)), result.data["address"])
        assertEquals(listOf("admin", "active"), result.data["tags"])
        assertNull(result.data["score"])
    }
}

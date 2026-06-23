package com.appforge.server.services.collections

import com.appforge.server.api.CollectionCreateRequest
import com.appforge.server.api.CollectionUpdateRequest
import com.appforge.server.services.auth.AuthResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CollectionServiceTest {

    private val now = Instant.parse("2026-06-01T00:00:00Z")

    // ─── Collection name validation ────────────────────────────────────────

    @Test
    fun `create rejects blank collection name`() = runBlocking {
        val service = CollectionServiceImpl(mockk(relaxed = true))

        val result = service.create("u1", "app1", "   ", CollectionCreateRequest(buildJsonObject {}))

        assertTrue(result is AuthResponse.BadRequest)
    }

    @Test
    fun `create rejects collection name with spaces`() = runBlocking {
        val service = CollectionServiceImpl(mockk(relaxed = true))

        val result = service.create("u1", "app1", "my collection", CollectionCreateRequest(buildJsonObject {}))

        assertTrue(result is AuthResponse.BadRequest)
    }

    @Test
    fun `create rejects collection name longer than 64 characters`() = runBlocking {
        val service = CollectionServiceImpl(mockk(relaxed = true))
        val tooLong = "a".repeat(65)

        val result = service.create("u1", "app1", tooLong, CollectionCreateRequest(buildJsonObject {}))

        assertTrue(result is AuthResponse.BadRequest)
    }

    @Test
    fun `create accepts alphanumeric, hyphens, and underscores`() = runBlocking {
        val repo = mockk<CollectionRepository>()
        coEvery { repo.create(any()) } answers { firstArg<CollectionRecordModel>().copy(createdAt = now, updatedAt = now) }
        val service = CollectionServiceImpl(repo)

        for (name in listOf("expenses", "my-expenses", "my_expenses", "Expenses2026")) {
            val result = service.create("u1", "app1", name, CollectionCreateRequest(buildJsonObject {}))
            assertTrue(result is AuthResponse.Ok, "Expected Ok for collection name '$name'")
        }
    }

    // ─── Create ───────────────────────────────────────────────────────────

    @Test
    fun `create stores record with correct owner and app scope`() = runBlocking {
        val repo = mockk<CollectionRepository>()
        coEvery { repo.create(any()) } answers { firstArg<CollectionRecordModel>().copy(createdAt = now, updatedAt = now) }
        val service = CollectionServiceImpl(repo)

        val result = service.create(
            userId = "u1",
            appId = "budget-app",
            collection = "expenses",
            request = CollectionCreateRequest(data = buildJsonObject { put("amount", 42) }),
        )

        assertTrue(result is AuthResponse.Ok)
        coVerify {
            repo.create(match {
                it.ownerUid == "u1" && it.appId == "budget-app" && it.collection == "expenses"
            })
        }
    }

    @Test
    fun `create preserves the data payload unchanged`() = runBlocking {
        val repo = mockk<CollectionRepository>()
        val data = buildJsonObject { put("amount", 99); put("category", "travel") }
        coEvery { repo.create(any()) } answers { firstArg<CollectionRecordModel>().copy(createdAt = now, updatedAt = now) }
        val service = CollectionServiceImpl(repo)

        val result = service.create("u1", "app1", "expenses", CollectionCreateRequest(data))

        assertTrue(result is AuthResponse.Ok)
        assertEquals(data, result.data.data)
    }

    // ─── List ─────────────────────────────────────────────────────────────

    @Test
    fun `list clamps limit to maximum of 200`() = runBlocking {
        val repo = mockk<CollectionRepository>()
        coEvery { repo.listByOwner(any(), any(), any(), any()) } returns emptyList()
        val service = CollectionServiceImpl(repo)

        service.list("u1", "app1", "expenses", limit = 9999)

        coVerify { repo.listByOwner(any(), any(), any(), 200) }
    }

    @Test
    fun `list clamps limit to minimum of 1`() = runBlocking {
        val repo = mockk<CollectionRepository>()
        coEvery { repo.listByOwner(any(), any(), any(), any()) } returns emptyList()
        val service = CollectionServiceImpl(repo)

        service.list("u1", "app1", "expenses", limit = -5)

        coVerify { repo.listByOwner(any(), any(), any(), 1) }
    }

    @Test
    fun `list returns total matching record count`() = runBlocking {
        val repo = mockk<CollectionRepository>()
        coEvery { repo.listByOwner("app1", "expenses", "u1", any()) } returns
            listOf(stubModel("r1"), stubModel("r2"), stubModel("r3"))
        val service = CollectionServiceImpl(repo)

        val result = service.list("u1", "app1", "expenses", 50)

        assertTrue(result is AuthResponse.Ok)
        assertEquals(3, result.data.total)
        assertEquals(3, result.data.records.size)
    }

    // ─── Get ──────────────────────────────────────────────────────────────

    @Test
    fun `get returns Forbidden when record does not exist`() = runBlocking {
        val repo = mockk<CollectionRepository>()
        coEvery { repo.getByIdAndOwner(any(), any(), any(), any()) } returns null
        val service = CollectionServiceImpl(repo)

        val result = service.get("u1", "app1", "expenses", "missing-id")

        assertTrue(result is AuthResponse.Forbidden)
    }

    @Test
    fun `get returns Ok when record exists`() = runBlocking {
        val repo = mockk<CollectionRepository>()
        val model = stubModel("r1")
        coEvery { repo.getByIdAndOwner("r1", "app1", "expenses", "u1") } returns model
        val service = CollectionServiceImpl(repo)

        val result = service.get("u1", "app1", "expenses", "r1")

        assertTrue(result is AuthResponse.Ok)
        assertEquals("r1", result.data.id)
    }

    // ─── Update ───────────────────────────────────────────────────────────

    @Test
    fun `update returns Forbidden when record does not exist`() = runBlocking {
        val repo = mockk<CollectionRepository>()
        coEvery { repo.getByIdAndOwner(any(), any(), any(), any()) } returns null
        val service = CollectionServiceImpl(repo)

        val result = service.update("u1", "app1", "expenses", "ghost", CollectionUpdateRequest(buildJsonObject {}))

        assertTrue(result is AuthResponse.Forbidden)
    }

    @Test
    fun `update replaces data field and calls repository`() = runBlocking {
        val repo = mockk<CollectionRepository>()
        val existing = stubModel("r1")
        val newData = buildJsonObject { put("amount", 999) }
        coEvery { repo.getByIdAndOwner("r1", "app1", "expenses", "u1") } returns existing
        coEvery { repo.update(any()) } answers { firstArg() }
        val service = CollectionServiceImpl(repo)

        val result = service.update("u1", "app1", "expenses", "r1", CollectionUpdateRequest(newData))

        assertTrue(result is AuthResponse.Ok)
        coVerify { repo.update(match { it.data == newData }) }
    }

    // ─── Delete ───────────────────────────────────────────────────────────

    @Test
    fun `delete returns Forbidden when record does not exist`() = runBlocking {
        val repo = mockk<CollectionRepository>()
        coEvery { repo.deleteByIdAndOwner(any(), any(), any(), any()) } returns false
        val service = CollectionServiceImpl(repo)

        val result = service.delete("u1", "app1", "expenses", "ghost")

        assertTrue(result is AuthResponse.Forbidden)
    }

    @Test
    fun `delete returns Ok on successful deletion`() = runBlocking {
        val repo = mockk<CollectionRepository>()
        coEvery { repo.deleteByIdAndOwner("r1", "app1", "expenses", "u1") } returns true
        val service = CollectionServiceImpl(repo)

        val result = service.delete("u1", "app1", "expenses", "r1")

        assertTrue(result is AuthResponse.Ok)
        assertEquals(true, result.data.success)
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private fun stubModel(id: String) = CollectionRecordModel(
        id = id,
        appId = "app1",
        collection = "expenses",
        ownerUid = "u1",
        data = buildJsonObject { put("stub", true) },
        createdAt = now,
        updatedAt = now,
    )
}

package com.appforge.server.services.reviews.ai

import com.appforge.server.services.documents.DocumentModel
import com.appforge.server.services.documents.repository.DocumentRepository
import com.appforge.server.infrastructure.time.timestampFromEpochMilli
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ContentResolverHardeningTest {
    @Test
    fun `registry fails when resolver type is missing`() = runBlocking {
        val registry = EntityTextContentResolverRegistry(emptyMap())

        assertFailsWith<IllegalArgumentException> {
            registry.resolve("document", "user-1", "entity-1", null)
        }
    }

    @Test
    fun `document resolver fails when document not found`() = runBlocking {
        val repository = object : DocumentRepository {
            override suspend fun upsert(
                id: String,
                ownerUid: String,
                title: String,
                tag: String,
                version: String,
                content: String,
            ): DocumentModel = error("not used")

            override suspend fun getByIdAndOwner(id: String, ownerUid: String): DocumentModel? = null
            override suspend fun listByOwner(ownerUid: String, limit: Int): List<DocumentModel> = emptyList()
            override suspend fun existsByIdAndOwner(id: String, ownerUid: String): Boolean = false
        }
        val resolver = DocumentTextContentResolver(repository)

        assertFailsWith<IllegalStateException> {
            resolver.resolve("user-1", "missing-entity", null)
        }
    }

    @Test
    fun `document resolver returns content when found`() = runBlocking {
        val repository = object : DocumentRepository {
            override suspend fun upsert(
                id: String,
                ownerUid: String,
                title: String,
                tag: String,
                version: String,
                content: String,
            ): DocumentModel = error("not used")

            override suspend fun getByIdAndOwner(id: String, ownerUid: String): DocumentModel? =
                DocumentModel(
                    id = id,
                    ownerUid = ownerUid,
                    title = "t",
                    tag = "document",
                    version = "v1",
                    content = "Document content",
                    contentLength = 16,
                    createdAt = timestampFromEpochMilli(1_700_000_000_000L),
                    updatedAt = timestampFromEpochMilli(1_700_000_000_000L),
                )

            override suspend fun listByOwner(ownerUid: String, limit: Int): List<DocumentModel> = emptyList()
            override suspend fun existsByIdAndOwner(id: String, ownerUid: String): Boolean = true
        }
        val resolver = DocumentTextContentResolver(repository)

        val content = resolver.resolve("user-1", "entity-1", null)
        assertEquals("Document content", content)
    }
}

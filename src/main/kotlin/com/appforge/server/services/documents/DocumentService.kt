package com.appforge.server.services.documents

import com.appforge.server.api.DocumentListResponse
import com.appforge.server.api.DocumentResponse
import com.appforge.server.api.DocumentSaveRequest
import com.appforge.server.infrastructure.time.instantToProtoTimestamp
import com.appforge.server.providers.identifier.IdentifierProvider
import com.appforge.server.services.auth.AuthResponse
import com.appforge.server.services.documents.repository.DocumentRepository

interface DocumentService {
    suspend fun save(userId: String, request: DocumentSaveRequest): AuthResponse<DocumentResponse>
    suspend fun list(userId: String, limit: Int = 20): AuthResponse<DocumentListResponse>
}

class DocumentServiceImpl(
    private val repository: DocumentRepository,
    private val maxContentChars: Int,
) : DocumentService {
    override suspend fun save(userId: String, request: DocumentSaveRequest): AuthResponse<DocumentResponse> {
        val title = request.title.trim()
        val tag = request.tag.trim()
        val version = request.version.trim()
        val content = request.content

        if (title.isBlank()) return AuthResponse.BadRequest("title is required.")
        if (title.length > 160) return AuthResponse.BadRequest("title must be <= 160 characters.")
        if (tag.isBlank()) return AuthResponse.BadRequest("tag is required.")
        if (tag.length > 64) return AuthResponse.BadRequest("tag must be <= 64 characters.")
        if (version.isBlank()) return AuthResponse.BadRequest("version is required.")
        if (version.length > 32) return AuthResponse.BadRequest("version must be <= 32 characters.")
        if (content.length > maxContentChars) {
            return AuthResponse.BadRequest("content must be <= $maxContentChars characters.")
        }

        val id = request.id?.trim()?.takeIf { it.isNotBlank() } ?: IdentifierProvider.newUuid()
        val saved = repository.upsert(
            id = id,
            ownerUid = userId,
            title = title,
            tag = tag,
            version = version,
            content = content,
        )
        return AuthResponse.Ok(saved.toApi())
    }

    override suspend fun list(userId: String, limit: Int): AuthResponse<DocumentListResponse> {
        val normalizedLimit = limit.coerceIn(1, 100)
        val items = repository.listByOwner(userId, normalizedLimit).map { it.toApi() }
        return AuthResponse.Ok(DocumentListResponse(documents = items))
    }
}

private fun DocumentModel.toApi(): DocumentResponse =
    DocumentResponse(
        id = id,
        title = title,
        tag = tag,
        version = version,
        content = content,
        contentLength = contentLength,
        createdAt = instantToProtoTimestamp(createdAt)
            ?: error("documents.createdAt cannot be null"),
        updatedAt = instantToProtoTimestamp(updatedAt)
            ?: error("documents.updatedAt cannot be null"),
    )

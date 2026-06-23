package com.appforge.server.services.collections

import com.appforge.server.api.CollectionCreateRequest
import com.appforge.server.api.CollectionDeleteResponse
import com.appforge.server.api.CollectionListResponse
import com.appforge.server.api.CollectionRecord
import com.appforge.server.api.CollectionUpdateRequest
import com.appforge.server.infrastructure.time.instantToProtoTimestamp
import com.appforge.server.providers.identifier.IdentifierProvider
import com.appforge.server.services.auth.AuthResponse
import java.time.Instant

interface CollectionService {
    suspend fun create(userId: String, appId: String, collection: String, request: CollectionCreateRequest): AuthResponse<CollectionRecord>
    suspend fun list(userId: String, appId: String, collection: String, limit: Int): AuthResponse<CollectionListResponse>
    suspend fun get(userId: String, appId: String, collection: String, id: String): AuthResponse<CollectionRecord>
    suspend fun update(userId: String, appId: String, collection: String, id: String, request: CollectionUpdateRequest): AuthResponse<CollectionRecord>
    suspend fun delete(userId: String, appId: String, collection: String, id: String): AuthResponse<CollectionDeleteResponse>
}

class CollectionServiceImpl(
    private val repository: CollectionRepository,
) : CollectionService {

    override suspend fun create(
        userId: String,
        appId: String,
        collection: String,
        request: CollectionCreateRequest,
    ): AuthResponse<CollectionRecord> {
        val name = collection.trim()
        if (!isValidCollectionName(name)) {
            return AuthResponse.BadRequest("collection name must be 1–64 characters: letters, digits, hyphens, underscores only.")
        }
        val record = CollectionRecordModel(
            id = IdentifierProvider.newUuid(),
            appId = appId,
            collection = name,
            ownerUid = userId,
            data = request.data,
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
        return AuthResponse.Ok(repository.create(record).toApi())
    }

    override suspend fun list(
        userId: String,
        appId: String,
        collection: String,
        limit: Int,
    ): AuthResponse<CollectionListResponse> {
        val normalizedLimit = limit.coerceIn(1, 200)
        val records = repository.listByOwner(appId, collection, userId, normalizedLimit).map { it.toApi() }
        return AuthResponse.Ok(CollectionListResponse(records = records, total = records.size))
    }

    override suspend fun get(
        userId: String,
        appId: String,
        collection: String,
        id: String,
    ): AuthResponse<CollectionRecord> {
        val record = repository.getByIdAndOwner(id, appId, collection, userId)
            ?: return AuthResponse.Forbidden("Record not found.")
        return AuthResponse.Ok(record.toApi())
    }

    override suspend fun update(
        userId: String,
        appId: String,
        collection: String,
        id: String,
        request: CollectionUpdateRequest,
    ): AuthResponse<CollectionRecord> {
        val existing = repository.getByIdAndOwner(id, appId, collection, userId)
            ?: return AuthResponse.Forbidden("Record not found.")
        val updated = existing.copy(data = request.data, updatedAt = Instant.now())
        return AuthResponse.Ok(repository.update(updated).toApi())
    }

    override suspend fun delete(
        userId: String,
        appId: String,
        collection: String,
        id: String,
    ): AuthResponse<CollectionDeleteResponse> {
        val deleted = repository.deleteByIdAndOwner(id, appId, collection, userId)
        if (!deleted) return AuthResponse.Forbidden("Record not found.")
        return AuthResponse.Ok(CollectionDeleteResponse(success = true))
    }

    private fun isValidCollectionName(name: String): Boolean =
        name.isNotBlank() && name.length <= 64 && name.matches(Regex("^[a-zA-Z0-9_-]+$"))
}

private fun CollectionRecordModel.toApi(): CollectionRecord = CollectionRecord(
    id = id,
    collection = collection,
    data = data,
    createdAt = instantToProtoTimestamp(createdAt) ?: error("custom_collections.createdAt cannot be null"),
    updatedAt = instantToProtoTimestamp(updatedAt) ?: error("custom_collections.updatedAt cannot be null"),
)

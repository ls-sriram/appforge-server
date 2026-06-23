package com.appforge.server.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class CollectionCreateRequest(
    val data: JsonObject,
)

@Serializable
data class CollectionUpdateRequest(
    val data: JsonObject,
)

@Serializable
data class CollectionRecord(
    val id: String,
    val collection: String,
    val data: JsonObject,
    val createdAt: ProtoTimestamp,
    val updatedAt: ProtoTimestamp,
)

@Serializable
data class CollectionListResponse(
    val records: List<CollectionRecord>,
    val total: Int,
)

@Serializable
data class CollectionDeleteResponse(
    val success: Boolean,
)

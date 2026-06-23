package com.appforge.server.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Request body for creating a collection record.
 *
 * [data] is an arbitrary JSON object — the server imposes no schema.
 * Structure and validation are entirely the responsibility of the client.
 *
 * See [CollectionRoutes][com.appforge.server.routing.collectionRoutes] for
 * an explanation of when to use the collections API vs. a typed SQL repository.
 */
@Serializable
data class CollectionCreateRequest(
    val data: JsonObject,
)

/**
 * Request body for replacing a collection record's data.
 *
 * The entire [data] object is replaced — there is no partial merge.
 * Send the complete new state of the record.
 */
@Serializable
data class CollectionUpdateRequest(
    val data: JsonObject,
)

/**
 * A single record in a schemaless collection.
 *
 * [data] is the raw JSON object as stored — the server does not interpret
 * or validate its contents. The client owns the schema.
 */
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
    /** Number of records returned. Reflects the applied [limit], not the total in the store. */
    val total: Int,
)

@Serializable
data class CollectionDeleteResponse(
    val success: Boolean,
)

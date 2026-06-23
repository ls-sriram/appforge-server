package com.appforge.server.services.collections

import com.appforge.server.infrastructure.time.AppTimestamp
import kotlinx.serialization.json.JsonObject

data class CollectionRecordModel(
    val id: String,
    val appId: String,
    val collection: String,
    val ownerUid: String,
    val data: JsonObject,
    val createdAt: AppTimestamp,
    val updatedAt: AppTimestamp,
)

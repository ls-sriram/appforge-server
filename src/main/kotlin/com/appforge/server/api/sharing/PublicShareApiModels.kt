package com.appforge.server.api.sharing

import com.appforge.server.api.ProtoTimestamp
import kotlinx.serialization.Serializable

@Serializable
data class PublicEntity(
    val id: String,
    val category: String,
    val title: String? = null,
    val subtitle: String? = null,
    val content: String? = null,
    val question: String? = null,
    val assetUrl: String? = null
)

@Serializable
data class PublicShareMetadata(
    val token: String,
    val entityType: String,
    val entityId: String,
    val accessMode: String,
    val expiresAt: ProtoTimestamp? = null,
    val revokedAt: ProtoTimestamp? = null,
)

@Serializable
data class PublicEntityResponse(
    val share: PublicShareMetadata,
    val entity: PublicEntity
)

package com.appforge.server.api.sharing

import com.appforge.server.api.ProtoTimestamp
import kotlinx.serialization.Serializable

@Serializable
data class CreateShareRequest(
    val entityType: String,
    val entityPath: String? = null
)

@Serializable
data class ShareResponse(
    val id: String,
    val entityType: String,
    val entityId: String,
    val shareUrl: String,
    val expiresAt: ProtoTimestamp,
)

@Serializable
data class ShareSummaryResponse(
    val id: String,
    val entityType: String,
    val entityId: String,
    val shareUrl: String,
    val expiresAt: ProtoTimestamp,
    val revokedAt: ProtoTimestamp? = null,
)

@Serializable
data class SendShareEmailRequest(
    val toEmail: String
)

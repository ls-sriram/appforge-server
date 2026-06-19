package com.appforge.server.services.sharing.models

import com.appforge.server.services.reviews.models.EntityCategory
import com.appforge.server.infrastructure.time.*

data class Share(
    val id: String,
    val token: String,
    val entityId: String,
    val entityCategory: EntityCategory,
    val accessMode: ShareAccessMode = ShareAccessMode.PUBLIC_LINK,
    val ownerId: String,
    val tokenHash: String,
    val expiresAt: AppTimestamp?,
    val createdAt: AppTimestamp,
    val createdBy: String,
    val revokedAt: AppTimestamp? = null,
    val revokedBy: String? = null,
)

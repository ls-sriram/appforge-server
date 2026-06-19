package com.appforge.server.services.reviews.models

import com.appforge.server.infrastructure.time.*

data class Profile(
    val userId: String,
    val displayName: String,
    val email: String,
    val emailNormalized: String,
    val lastSeenAt: AppTimestamp
)

package com.appforge.server.api

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String,
)

@Serializable
data class HealthResponse(
    val status: String,
)

@Serializable
data class WebhookResponse(
    val received: Boolean,
)

@Serializable
data class MlHealthResponse(
    val enabled: Boolean,
)

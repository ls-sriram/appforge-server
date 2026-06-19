package com.appforge.server.api

import kotlinx.serialization.Serializable

@Serializable
data class DocumentSaveRequest(
    val id: String? = null,
    val title: String,
    val tag: String,
    val version: String,
    val content: String,
)

@Serializable
data class DocumentResponse(
    val id: String,
    val title: String,
    val tag: String,
    val version: String,
    val content: String,
    val contentLength: Int,
    val createdAt: ProtoTimestamp,
    val updatedAt: ProtoTimestamp,
)

@Serializable
data class DocumentListResponse(
    val documents: List<DocumentResponse>,
)

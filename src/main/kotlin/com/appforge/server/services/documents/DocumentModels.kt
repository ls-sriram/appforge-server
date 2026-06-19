package com.appforge.server.services.documents

import com.appforge.server.infrastructure.time.AppTimestamp

data class DocumentModel(
    val id: String,
    val ownerUid: String,
    val title: String,
    val tag: String,
    val version: String,
    val content: String,
    val contentLength: Int,
    val createdAt: AppTimestamp,
    val updatedAt: AppTimestamp,
)

data class DocumentSaveInput(
    val id: String?,
    val title: String,
    val tag: String,
    val version: String,
    val content: String,
)

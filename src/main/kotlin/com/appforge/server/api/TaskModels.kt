package com.appforge.server.api

import kotlinx.serialization.Serializable

@Serializable
data class TaskCreateRequest(
    val type: String,
    val title: String,
    val tag: String? = null,
    val description: String? = null,
    val priority: String? = null,
    val assignee: String? = null,
    val dueAt: ProtoTimestamp? = null,
    val metadata: Map<String, String>? = null,
)

@Serializable
data class TaskUpdateRequest(
    val type: String? = null,
    val title: String? = null,
    val status: String? = null,
    val tag: String? = null,
    val description: String? = null,
    val priority: String? = null,
    val assignee: String? = null,
    val dueAt: ProtoTimestamp? = null,
    val completedAt: ProtoTimestamp? = null,
    val metadata: Map<String, String>? = null,
    val clearTag: Boolean = false,
    val clearDescription: Boolean = false,
    val clearPriority: Boolean = false,
    val clearAssignee: Boolean = false,
    val clearDueAt: Boolean = false,
    val clearCompletedAt: Boolean = false,
)

@Serializable
data class TaskCompleteRequest(
    val completedAt: ProtoTimestamp? = null,
)

@Serializable
data class TaskResponse(
    val id: String,
    val type: String,
    val title: String,
    val status: String,
    val tag: String? = null,
    val description: String? = null,
    val priority: String? = null,
    val assignee: String? = null,
    val dueAt: ProtoTimestamp? = null,
    val completedAt: ProtoTimestamp? = null,
    val metadata: Map<String, String> = emptyMap(),
    val createdAt: ProtoTimestamp,
    val updatedAt: ProtoTimestamp,
)

@Serializable
data class TaskListResponse(
    val tasks: List<TaskResponse>,
)

@Serializable
data class TaskDeleteResponse(
    val success: Boolean,
)

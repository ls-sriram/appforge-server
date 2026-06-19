package com.appforge.server.services.tasks

import com.appforge.server.infrastructure.time.AppTimestamp

enum class TaskStatus(val wire: String) {
    OPEN("open"),
    COMPLETED("completed"),
    ARCHIVED("archived");

    companion object {
        fun fromWire(value: String?): TaskStatus? = entries.firstOrNull { it.wire == value }
    }
}

data class TaskModel(
    val id: String,
    val ownerUid: String,
    val type: String,
    val title: String,
    val status: TaskStatus,
    val tag: String?,
    val description: String?,
    val priority: String?,
    val assignee: String?,
    val dueAt: AppTimestamp?,
    val completedAt: AppTimestamp?,
    val metadata: Map<String, String>,
    val createdAt: AppTimestamp,
    val updatedAt: AppTimestamp,
)

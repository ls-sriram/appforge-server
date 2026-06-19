package com.appforge.server.services.tasks

import com.appforge.server.api.TaskCompleteRequest
import com.appforge.server.api.TaskCreateRequest
import com.appforge.server.api.TaskDeleteResponse
import com.appforge.server.api.TaskListResponse
import com.appforge.server.api.TaskResponse
import com.appforge.server.api.TaskUpdateRequest
import com.appforge.server.infrastructure.time.instantToProtoTimestamp
import com.appforge.server.infrastructure.time.protoTimestampToInstant
import com.appforge.server.providers.identifier.IdentifierProvider
import com.appforge.server.services.auth.AuthResponse
import com.appforge.server.services.tasks.repository.TaskRepository
import java.time.Instant

interface TaskService {
    suspend fun create(userId: String, request: TaskCreateRequest): AuthResponse<TaskResponse>
    suspend fun list(userId: String, status: String?, type: String?, tag: String?, limit: Int = 20): AuthResponse<TaskListResponse>
    suspend fun get(userId: String, id: String): AuthResponse<TaskResponse>
    suspend fun update(userId: String, id: String, request: TaskUpdateRequest): AuthResponse<TaskResponse>
    suspend fun complete(userId: String, id: String, request: TaskCompleteRequest): AuthResponse<TaskResponse>
    suspend fun reopen(userId: String, id: String): AuthResponse<TaskResponse>
    suspend fun delete(userId: String, id: String): AuthResponse<TaskDeleteResponse>
}

class TaskServiceImpl(
    private val repository: TaskRepository,
) : TaskService {
    override suspend fun create(userId: String, request: TaskCreateRequest): AuthResponse<TaskResponse> {
        val type = request.type.trim()
        val title = request.title.trim()
        if (type.isBlank()) return AuthResponse.BadRequest("type is required.")
        if (title.isBlank()) return AuthResponse.BadRequest("title is required.")
        if (title.length > 200) return AuthResponse.BadRequest("title must be <= 200 characters.")
        val tag = request.tag?.trim()?.takeIf { it.isNotBlank() }
        if (tag != null && tag.length > 50) return AuthResponse.BadRequest("tag must be <= 50 characters.")

        val task = TaskModel(
            id = IdentifierProvider.newUuid(),
            ownerUid = userId,
            type = type,
            title = title,
            status = TaskStatus.OPEN,
            tag = tag,
            description = request.description?.trim()?.takeIf { it.isNotBlank() },
            priority = request.priority?.trim()?.takeIf { it.isNotBlank() },
            assignee = request.assignee?.trim()?.takeIf { it.isNotBlank() },
            dueAt = protoTimestampToInstant(request.dueAt),
            completedAt = null,
            metadata = request.metadata ?: emptyMap(),
            createdAt = Instant.now(),
            updatedAt = Instant.now(),
        )
        return AuthResponse.Ok(repository.create(task).toApi())
    }

    override suspend fun list(userId: String, status: String?, type: String?, tag: String?, limit: Int): AuthResponse<TaskListResponse> {
        val normalizedLimit = limit.coerceIn(1, 100)
        val normalizedStatus = status?.trim()?.takeIf { it.isNotBlank() }
        if (normalizedStatus != null && TaskStatus.fromWire(normalizedStatus) == null) {
            return AuthResponse.BadRequest("status must be one of open, completed, archived.")
        }
        val items = repository.listByOwner(userId, normalizedStatus, type?.trim(), tag?.trim(), normalizedLimit)
            .map { it.toApi() }
        return AuthResponse.Ok(TaskListResponse(tasks = items))
    }

    override suspend fun get(userId: String, id: String): AuthResponse<TaskResponse> {
        val task = repository.getByIdAndOwner(id, userId) ?: return AuthResponse.Forbidden("Task not found.")
        return AuthResponse.Ok(task.toApi())
    }

    override suspend fun update(userId: String, id: String, request: TaskUpdateRequest): AuthResponse<TaskResponse> {
        val existing = repository.getByIdAndOwner(id, userId) ?: return AuthResponse.Forbidden("Task not found.")
        val nextType = request.type?.trim()?.takeIf { it.isNotBlank() } ?: existing.type
        val nextTitle = request.title?.trim() ?: existing.title
        if (nextTitle.isBlank()) return AuthResponse.BadRequest("title is required.")
        if (nextTitle.length > 200) return AuthResponse.BadRequest("title must be <= 200 characters.")

        val nextStatus = if (request.status != null) {
            TaskStatus.fromWire(request.status.trim()) ?: return AuthResponse.BadRequest("status must be one of open, completed, archived.")
        } else existing.status

        val nextCompletedAt = when {
            request.clearCompletedAt -> null
            request.completedAt != null -> protoTimestampToInstant(request.completedAt)
            nextStatus == TaskStatus.COMPLETED && existing.completedAt == null -> Instant.now()
            nextStatus != TaskStatus.COMPLETED -> null
            else -> existing.completedAt
        }

        val next = existing.copy(
            type = nextType,
            title = nextTitle,
            status = nextStatus,
            tag = when {
                request.clearTag -> null
                request.tag != null -> request.tag.trim().takeIf { it.isNotBlank() }
                else -> existing.tag
            },
            description = when {
                request.clearDescription -> null
                request.description != null -> request.description.trim().takeIf { it.isNotBlank() }
                else -> existing.description
            },
            priority = when {
                request.clearPriority -> null
                request.priority != null -> request.priority.trim().takeIf { it.isNotBlank() }
                else -> existing.priority
            },
            assignee = when {
                request.clearAssignee -> null
                request.assignee != null -> request.assignee.trim().takeIf { it.isNotBlank() }
                else -> existing.assignee
            },
            dueAt = when {
                request.clearDueAt -> null
                request.dueAt != null -> protoTimestampToInstant(request.dueAt)
                else -> existing.dueAt
            },
            completedAt = nextCompletedAt,
            metadata = request.metadata ?: existing.metadata,
        )

        return AuthResponse.Ok(repository.update(next).toApi())
    }

    override suspend fun complete(userId: String, id: String, request: TaskCompleteRequest): AuthResponse<TaskResponse> {
        val existing = repository.getByIdAndOwner(id, userId) ?: return AuthResponse.Forbidden("Task not found.")
        val completedAt = protoTimestampToInstant(request.completedAt) ?: Instant.now()
        val updated = existing.copy(status = TaskStatus.COMPLETED, completedAt = completedAt)
        return AuthResponse.Ok(repository.update(updated).toApi())
    }

    override suspend fun reopen(userId: String, id: String): AuthResponse<TaskResponse> {
        val existing = repository.getByIdAndOwner(id, userId) ?: return AuthResponse.Forbidden("Task not found.")
        val updated = existing.copy(status = TaskStatus.OPEN, completedAt = null)
        return AuthResponse.Ok(repository.update(updated).toApi())
    }

    override suspend fun delete(userId: String, id: String): AuthResponse<TaskDeleteResponse> {
        val deleted = repository.deleteByIdAndOwner(id, userId)
        if (!deleted) return AuthResponse.Forbidden("Task not found.")
        return AuthResponse.Ok(TaskDeleteResponse(success = true))
    }
}

private fun TaskModel.toApi(): TaskResponse = TaskResponse(
    id = id,
    type = type,
    title = title,
    status = status.wire,
    tag = tag,
    description = description,
    priority = priority,
    assignee = assignee,
    dueAt = instantToProtoTimestamp(dueAt),
    completedAt = instantToProtoTimestamp(completedAt),
    metadata = metadata,
    createdAt = instantToProtoTimestamp(createdAt) ?: error("tasks.createdAt cannot be null"),
    updatedAt = instantToProtoTimestamp(updatedAt) ?: error("tasks.updatedAt cannot be null"),
)

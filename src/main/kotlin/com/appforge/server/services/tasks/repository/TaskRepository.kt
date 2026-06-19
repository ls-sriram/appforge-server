package com.appforge.server.services.tasks.repository

import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.infrastructure.sql.NamedSql
import com.appforge.server.infrastructure.time.getAppTimestamp
import com.appforge.server.infrastructure.time.setInstant
import com.appforge.server.providers.transaction.SqlTransactionProvider
import com.appforge.server.providers.transaction.TransactionProvider
import com.appforge.server.services.tasks.TaskModel
import com.appforge.server.services.tasks.TaskStatus
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface TaskRepository {
    suspend fun create(task: TaskModel): TaskModel
    suspend fun getByIdAndOwner(id: String, ownerUid: String): TaskModel?
    suspend fun listByOwner(ownerUid: String, status: String?, type: String?, tag: String?, limit: Int): List<TaskModel>
    suspend fun update(task: TaskModel): TaskModel
    suspend fun deleteByIdAndOwner(id: String, ownerUid: String): Boolean
}

class SqlTaskRepository(
    sqlDatabase: ExposedDatabase,
) : TaskRepository {
    private val sql = NamedSql.fromResource(
        resourcePath = "com/appforge/server/services/tasks/tasks.sql",
        classLoader = SqlTaskRepository::class.java.classLoader,
    )
    private val tx: TransactionProvider = SqlTransactionProvider(sqlDatabase)
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun create(task: TaskModel): TaskModel {
        tx.write { conn ->
            conn.prepareStatement(sql.query("tasks.insert")).use { stmt ->
                stmt.setString(1, task.id)
                stmt.setString(2, task.ownerUid)
                stmt.setString(3, task.type)
                stmt.setString(4, task.title)
                stmt.setString(5, task.status.wire)
                stmt.setString(6, task.tag)
                stmt.setString(7, task.description)
                stmt.setString(8, task.priority)
                stmt.setString(9, task.assignee)
                stmt.setInstant(10, task.dueAt)
                stmt.setInstant(11, task.completedAt)
                stmt.setString(12, json.encodeToString(task.metadata))
                stmt.executeUpdate()
            }
        }
        return getByIdAndOwner(task.id, task.ownerUid) ?: error("Task not found after insert: ${task.id}")
    }

    override suspend fun getByIdAndOwner(id: String, ownerUid: String): TaskModel? {
        return tx.read { conn ->
            conn.prepareStatement(sql.query("tasks.select_by_id_and_owner")).use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, ownerUid)
                stmt.executeQuery().use { rs ->
                    if (!rs.next()) return@read null
                    fromResultSet(rs)
                }
            }
        }
    }

    override suspend fun listByOwner(ownerUid: String, status: String?, type: String?, tag: String?, limit: Int): List<TaskModel> {
        return tx.read { conn ->
            conn.prepareStatement(sql.query("tasks.list_by_owner")).use { stmt ->
                stmt.setString(1, ownerUid)
                stmt.setString(2, status)
                stmt.setString(3, status)
                stmt.setString(4, type)
                stmt.setString(5, type)
                stmt.setString(6, tag)
                stmt.setString(7, tag)
                stmt.setInt(8, limit)
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) add(fromResultSet(rs))
                    }
                }
            }
        }
    }

    override suspend fun update(task: TaskModel): TaskModel {
        tx.write { conn ->
            conn.prepareStatement(sql.query("tasks.update_fields")).use { stmt ->
                stmt.setString(1, task.type)
                stmt.setString(2, task.title)
                stmt.setString(3, task.status.wire)
                stmt.setString(4, task.tag)
                stmt.setString(5, task.description)
                stmt.setString(6, task.priority)
                stmt.setString(7, task.assignee)
                stmt.setInstant(8, task.dueAt)
                stmt.setInstant(9, task.completedAt)
                stmt.setString(10, json.encodeToString(task.metadata))
                stmt.setString(11, task.id)
                stmt.setString(12, task.ownerUid)
                stmt.executeUpdate()
            }
        }
        return getByIdAndOwner(task.id, task.ownerUid) ?: error("Task not found after update: ${task.id}")
    }

    override suspend fun deleteByIdAndOwner(id: String, ownerUid: String): Boolean {
        return tx.write { conn ->
            conn.prepareStatement(sql.query("tasks.delete_by_id_and_owner")).use { stmt ->
                stmt.setString(1, id)
                stmt.setString(2, ownerUid)
                stmt.executeUpdate() > 0
            }
        }
    }

    private fun fromResultSet(rs: java.sql.ResultSet): TaskModel {
        val status = TaskStatus.fromWire(rs.getString("status")) ?: TaskStatus.OPEN
        val metadataJson = rs.getString("metadata_json")
        val metadata = runCatching { json.decodeFromString<Map<String, String>>(metadataJson) }.getOrElse { emptyMap() }
        return TaskModel(
            id = rs.getString("id"),
            ownerUid = rs.getString("owner_uid"),
            type = rs.getString("type"),
            title = rs.getString("title"),
            status = status,
            tag = rs.getString("tag"),
            description = rs.getString("description"),
            priority = rs.getString("priority"),
            assignee = rs.getString("assignee"),
            dueAt = rs.getAppTimestamp("due_at"),
            completedAt = rs.getAppTimestamp("completed_at"),
            metadata = metadata,
            createdAt = rs.getAppTimestamp("created_at") ?: error("tasks.created_at is null"),
            updatedAt = rs.getAppTimestamp("updated_at") ?: error("tasks.updated_at is null"),
        )
    }
}

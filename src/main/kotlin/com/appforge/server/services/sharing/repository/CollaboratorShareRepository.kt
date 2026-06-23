package com.appforge.server.services.sharing.repository

import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.infrastructure.Resource
import com.appforge.server.infrastructure.time.AppTimestamp
import com.appforge.server.infrastructure.time.getAppTimestamp
import com.appforge.server.infrastructure.time.setInstant
import com.appforge.server.providers.transaction.SqlTransactionProvider
import com.appforge.server.providers.transaction.TransactionProvider
import com.appforge.server.services.reviews.models.EntityCategory
import com.appforge.server.services.sharing.models.CollaboratorEntityShare
import java.time.Instant
import java.util.Locale

interface CollaboratorShareRepositoryApi {
    suspend fun create(share: CollaboratorEntityShare): Resource<CollaboratorEntityShare>
    suspend fun findActiveByOwnerEntityCollaborator(
        ownerId: String,
        entityCategory: EntityCategory,
        entityId: String,
        collaboratorEmailNormalized: String,
    ): Resource<CollaboratorEntityShare?>
    suspend fun listActiveByOwnerEntity(
        ownerId: String,
        entityCategory: EntityCategory,
        entityId: String,
        limit: Int = 100,
    ): Resource<List<CollaboratorEntityShare>>
    suspend fun revokeByIdAndOwner(
        shareId: String,
        ownerId: String,
        revokedAt: AppTimestamp,
        revokedBy: String,
    ): Resource<Unit>
    suspend fun listActiveForCollaborator(
        collaboratorEmailNormalized: String,
        limit: Int = 100,
    ): Resource<List<CollaboratorEntityShare>>
    suspend fun getActiveForCollaborator(
        shareId: String,
        collaboratorEmailNormalized: String,
    ): Resource<CollaboratorEntityShare?>
}

class CollaboratorShareRepository(
    sqlDatabase: ExposedDatabase,
) : CollaboratorShareRepositoryApi {
    private val tx: TransactionProvider = SqlTransactionProvider(sqlDatabase)

    override suspend fun create(share: CollaboratorEntityShare): Resource<CollaboratorEntityShare> {
        return try {
            tx.write { conn ->
                conn.prepareStatement(
                    """
                    INSERT INTO collaborator_entity_shares (
                        id, owner_uid, entity_type, entity_id, collaborator_email, collaborator_email_normalized,
                        created_by, expires_at, created_at, revoked_at, revoked_by
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setString(1, share.id)
                    stmt.setString(2, share.ownerId)
                    stmt.setString(3, share.entityCategory.value)
                    stmt.setString(4, share.entityId)
                    stmt.setString(5, share.collaboratorEmail)
                    stmt.setString(6, share.collaboratorEmailNormalized)
                    stmt.setString(7, share.createdBy)
                    stmt.setInstant(8, share.expiresAt)
                    stmt.setInstant(9, share.createdAt)
                    stmt.setInstant(10, share.revokedAt)
                    stmt.setString(11, share.revokedBy)
                    stmt.executeUpdate()
                }
            }
            Resource.Success(share)
        } catch (e: Exception) {
            Resource.Error(e)
        }
    }

    override suspend fun findActiveByOwnerEntityCollaborator(
        ownerId: String,
        entityCategory: EntityCategory,
        entityId: String,
        collaboratorEmailNormalized: String,
    ): Resource<CollaboratorEntityShare?> = selectOne(
        """
        SELECT id, owner_uid, entity_type, entity_id, collaborator_email, collaborator_email_normalized, created_by, expires_at, created_at, revoked_at, revoked_by
        FROM collaborator_entity_shares
        WHERE owner_uid = ?
          AND entity_type = ?
          AND entity_id = ?
          AND collaborator_email_normalized = ?
          AND revoked_at IS NULL
          AND (expires_at IS NULL OR expires_at > NOW())
        ORDER BY created_at DESC
        LIMIT 1
        """.trimIndent(),
        listOf(ownerId, entityCategory.value, entityId, collaboratorEmailNormalized),
    )

    override suspend fun listActiveByOwnerEntity(
        ownerId: String,
        entityCategory: EntityCategory,
        entityId: String,
        limit: Int,
    ): Resource<List<CollaboratorEntityShare>> = selectMany(
        """
        SELECT id, owner_uid, entity_type, entity_id, collaborator_email, collaborator_email_normalized, created_by, expires_at, created_at, revoked_at, revoked_by
        FROM collaborator_entity_shares
        WHERE owner_uid = ?
          AND entity_type = ?
          AND entity_id = ?
          AND revoked_at IS NULL
          AND (expires_at IS NULL OR expires_at > NOW())
        ORDER BY created_at DESC
        LIMIT ?
        """.trimIndent(),
        listOf(ownerId, entityCategory.value, entityId, limit.coerceIn(1, 500)),
    )

    override suspend fun revokeByIdAndOwner(
        shareId: String,
        ownerId: String,
        revokedAt: AppTimestamp,
        revokedBy: String,
    ): Resource<Unit> {
        return try {
            val rows = tx.write { conn ->
                conn.prepareStatement(
                    """
                    UPDATE collaborator_entity_shares
                    SET revoked_at = ?, revoked_by = ?
                    WHERE id = ?
                      AND owner_uid = ?
                      AND revoked_at IS NULL
                    """.trimIndent()
                ).use { stmt ->
                    stmt.setInstant(1, revokedAt)
                    stmt.setString(2, revokedBy)
                    stmt.setString(3, shareId)
                    stmt.setString(4, ownerId)
                    stmt.executeUpdate()
                }
            }
            if (rows == 0) Resource.Error(Exception("Unauthorized")) else Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e)
        }
    }

    override suspend fun listActiveForCollaborator(
        collaboratorEmailNormalized: String,
        limit: Int,
    ): Resource<List<CollaboratorEntityShare>> = selectMany(
        """
        SELECT id, owner_uid, entity_type, entity_id, collaborator_email, collaborator_email_normalized, created_by, expires_at, created_at, revoked_at, revoked_by
        FROM collaborator_entity_shares
        WHERE collaborator_email_normalized = ?
          AND revoked_at IS NULL
          AND (expires_at IS NULL OR expires_at > NOW())
        ORDER BY created_at DESC
        LIMIT ?
        """.trimIndent(),
        listOf(normalizeEmail(collaboratorEmailNormalized), limit.coerceIn(1, 500)),
    )

    override suspend fun getActiveForCollaborator(
        shareId: String,
        collaboratorEmailNormalized: String,
    ): Resource<CollaboratorEntityShare?> = selectOne(
        """
        SELECT id, owner_uid, entity_type, entity_id, collaborator_email, collaborator_email_normalized, created_by, expires_at, created_at, revoked_at, revoked_by
        FROM collaborator_entity_shares
        WHERE id = ?
          AND collaborator_email_normalized = ?
          AND revoked_at IS NULL
          AND (expires_at IS NULL OR expires_at > NOW())
        LIMIT 1
        """.trimIndent(),
        listOf(shareId, normalizeEmail(collaboratorEmailNormalized)),
    )

    private suspend fun selectOne(sql: String, params: List<Any>): Resource<CollaboratorEntityShare?> {
        return try {
            val result = tx.read { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    bind(stmt, params)
                    stmt.executeQuery().use { rs ->
                        if (!rs.next()) null else rs.toCollaboratorShare()
                    }
                }
            }
            Resource.Success(result)
        } catch (e: Exception) {
            Resource.Error(e)
        }
    }

    private suspend fun selectMany(sql: String, params: List<Any>): Resource<List<CollaboratorEntityShare>> {
        return try {
            val result = tx.read { conn ->
                conn.prepareStatement(sql).use { stmt ->
                    bind(stmt, params)
                    stmt.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) add(rs.toCollaboratorShare())
                        }
                    }
                }
            }
            Resource.Success(result)
        } catch (e: Exception) {
            Resource.Error(e)
        }
    }

    private fun bind(stmt: java.sql.PreparedStatement, params: List<Any>) {
        params.forEachIndexed { index, value ->
            when (value) {
                is String -> stmt.setString(index + 1, value)
                is Int -> stmt.setInt(index + 1, value)
                else -> error("Unsupported parameter: ${value::class.java.name}")
            }
        }
    }
}

private fun java.sql.ResultSet.toCollaboratorShare(): CollaboratorEntityShare =
    CollaboratorEntityShare(
        id = getString("id"),
        ownerId = getString("owner_uid"),
        entityCategory = EntityCategory.fromWire(getString("entity_type")),
        entityId = getString("entity_id"),
        collaboratorEmail = getString("collaborator_email"),
        collaboratorEmailNormalized = getString("collaborator_email_normalized"),
        createdBy = getString("created_by"),
        expiresAt = getAppTimestamp("expires_at"),
        createdAt = getAppTimestamp("created_at") ?: error("collaborator_entity_shares.created_at is null"),
        revokedAt = getAppTimestamp("revoked_at"),
        revokedBy = getString("revoked_by"),
    )

private fun normalizeEmail(value: String): String =
    value.trim().lowercase(Locale.US)

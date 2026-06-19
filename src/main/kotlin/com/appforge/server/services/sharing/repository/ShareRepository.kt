package com.appforge.server.services.sharing.repository

import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.infrastructure.Resource
import com.appforge.server.infrastructure.sql.NamedSql
import com.appforge.server.infrastructure.time.AppTimestamp
import com.appforge.server.infrastructure.time.getAppTimestamp
import com.appforge.server.infrastructure.time.setInstant
import com.appforge.server.providers.transaction.SqlTransactionProvider
import com.appforge.server.providers.transaction.TransactionProvider
import com.appforge.server.services.reviews.models.EntityCategory
import com.appforge.server.services.sharing.models.Share
import com.appforge.server.services.sharing.models.ShareAccessMode
import java.sql.Types

interface ShareRepositoryApi {
    suspend fun create(share: Share): Resource<Share>
    suspend fun getByTokenHash(tokenHash: String): Resource<Share>
    suspend fun findActivePublicByEntity(ownerId: String, entityCategory: EntityCategory, entityId: String): Resource<Share?>
    suspend fun listActiveByOwner(ownerId: String, limit: Int = 200): Resource<List<Share>>
    suspend fun listActiveByOwnerEntity(ownerId: String, entityCategory: EntityCategory, entityId: String, limit: Int = 20): Resource<List<Share>>
    suspend fun revokeByTokenHash(ownerId: String, tokenHash: String, revokedAt: AppTimestamp, revokedBy: String): Resource<Unit>
}

class ShareRepository(
    sqlDatabase: ExposedDatabase,
) : ShareRepositoryApi {
    private val sql = NamedSql.fromResource(
        resourcePath = "com/appforge/server/services/sharing/sharing.sql",
        classLoader = ShareRepository::class.java.classLoader,
    )
    private val transactionProvider: TransactionProvider = SqlTransactionProvider(sqlDatabase)

    override suspend fun create(share: Share): Resource<Share> {
        return try {
            transactionProvider.write { conn ->
                conn.prepareStatement(sql.query("sharing.insert_entity_share")).use { stmt ->
                    stmt.setString(1, share.id)
                    stmt.setString(2, share.ownerId)
                    stmt.setString(3, share.entityCategory.value)
                    stmt.setString(4, share.entityId)
                    stmt.setString(5, share.accessMode.wire)
                    stmt.setString(6, share.tokenHash)
                    stmt.setInstant(7, share.expiresAt)
                    stmt.setInstant(8, share.createdAt)
                    stmt.setString(9, share.createdBy)
                    stmt.setInstant(10, share.revokedAt)
                    if (share.revokedBy == null) stmt.setNull(11, Types.VARCHAR) else stmt.setString(11, share.revokedBy)
                    stmt.executeUpdate()
                }
            }
            Resource.Success(share)
        } catch (e: Exception) {
            Resource.Error(e)
        }
    }

    override suspend fun getByTokenHash(tokenHash: String): Resource<Share> {
        return try {
            val item = transactionProvider.read { conn ->
                conn.prepareStatement(sql.query("sharing.select_entity_share_by_token_hash")).use { stmt ->
                    stmt.setString(1, tokenHash)
                    stmt.executeQuery().use { rs ->
                        if (!rs.next()) null else rs.toShare()
                    }
                }
            }
            if (item == null) Resource.Error(Exception("Share not found")) else Resource.Success(item)
        } catch (e: Exception) {
            Resource.Error(e)
        }
    }

    override suspend fun findActivePublicByEntity(ownerId: String, entityCategory: EntityCategory, entityId: String): Resource<Share?> {
        return try {
            val item = transactionProvider.read { conn ->
                conn.prepareStatement(sql.query("sharing.select_active_public_by_entity")).use { stmt ->
                    stmt.setString(1, ownerId)
                    stmt.setString(2, entityCategory.value)
                    stmt.setString(3, entityId)
                    stmt.executeQuery().use { rs ->
                        if (!rs.next()) null else rs.toShare()
                    }
                }
            }
            Resource.Success(item)
        } catch (e: Exception) {
            Resource.Error(e)
        }
    }

    override suspend fun listActiveByOwner(ownerId: String, limit: Int): Resource<List<Share>> {
        return try {
            val items = transactionProvider.read { conn ->
                conn.prepareStatement(sql.query("sharing.list_active_by_owner")).use { stmt ->
                    stmt.setString(1, ownerId)
                    stmt.setInt(2, limit)
                    stmt.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) add(rs.toShare())
                        }
                    }
                }
            }
            Resource.Success(items)
        } catch (e: Exception) {
            Resource.Error(e)
        }
    }

    override suspend fun listActiveByOwnerEntity(ownerId: String, entityCategory: EntityCategory, entityId: String, limit: Int): Resource<List<Share>> {
        return try {
            val items = transactionProvider.read { conn ->
                conn.prepareStatement(sql.query("sharing.list_active_by_owner_entity")).use { stmt ->
                    stmt.setString(1, ownerId)
                    stmt.setString(2, entityCategory.value)
                    stmt.setString(3, entityId)
                    stmt.setInt(4, limit)
                    stmt.executeQuery().use { rs ->
                        buildList {
                            while (rs.next()) add(rs.toShare())
                        }
                    }
                }
            }
            Resource.Success(items)
        } catch (e: Exception) {
            Resource.Error(e)
        }
    }

    override suspend fun revokeByTokenHash(ownerId: String, tokenHash: String, revokedAt: AppTimestamp, revokedBy: String): Resource<Unit> {
        return try {
            val rows = transactionProvider.write { conn ->
                conn.prepareStatement(sql.query("sharing.revoke_by_token_hash_and_owner")).use { stmt ->
                    stmt.setInstant(1, revokedAt)
                    stmt.setString(2, revokedBy)
                    stmt.setString(3, tokenHash)
                    stmt.setString(4, ownerId)
                    stmt.executeUpdate()
                }
            }
            if (rows == 0) Resource.Error(Exception("Unauthorized")) else Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Error(e)
        }
    }
}

private fun java.sql.ResultSet.toShare(): Share {
    val id = getString("id")
    return Share(
        id = id,
        token = id,
        entityId = getString("entity_id"),
        entityCategory = EntityCategory.fromWire(getString("entity_type")),
        accessMode = ShareAccessMode.fromWire(getString("access_mode")),
        ownerId = getString("owner_uid"),
        tokenHash = getString("token_hash"),
        expiresAt = getAppTimestamp("expires_at"),
        createdAt = getAppTimestamp("created_at") ?: error("entity_shares.created_at is null"),
        createdBy = getString("created_by"),
        revokedAt = getAppTimestamp("revoked_at"),
        revokedBy = getString("revoked_by"),
    )
}

package com.appforge.server.services.auth.repository

import com.appforge.server.infrastructure.Database
import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.infrastructure.sql.NamedSql
import com.appforge.server.infrastructure.time.setInstant
import com.appforge.server.infrastructure.time.*
import com.appforge.server.providers.transaction.SqlTransactionProvider
import com.appforge.server.providers.transaction.TransactionProvider

interface UserRepositoryApi {
    suspend fun upsertUser(uid: String, email: String, displayName: String?, lastLoginAt: AppTimestamp)
    suspend fun upsertProfile(uid: String, email: String, displayName: String?, lastSeenAt: AppTimestamp)
    suspend fun getUser(uid: String): AppUserRecord?
    suspend fun updateDisplayName(uid: String, displayName: String, updatedAt: AppTimestamp): Boolean
    suspend fun deleteUserAccount(uid: String): Boolean
}

data class AppUserRecord(
    val uid: String,
    val email: String,
    val displayName: String?,
    val createdAt: AppTimestamp?,
    val lastLoginAt: AppTimestamp?,
)

class SqlUserRepository(
    database: Database,
) : UserRepositoryApi {
    private val sql = NamedSql.fromResource(
        resourcePath = "com/appforge/server/services/auth/auth.sql",
        classLoader = SqlUserRepository::class.java.classLoader,
    )
    private val transactionProvider: TransactionProvider =
        SqlTransactionProvider(database as? ExposedDatabase ?: error("SqlUserRepository requires ExposedDatabase"))

    override suspend fun upsertUser(uid: String, email: String, displayName: String?, lastLoginAt: AppTimestamp) {
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isBlank()) return
        transactionProvider.write { conn ->
                conn.prepareStatement(
                    sql.query("user.select_uid_by_email_normalized")
                ).use { stmt ->
                    stmt.setString(1, normalizedEmail)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            val existingUid = rs.getString("uid")
                            if (existingUid != uid) {
                                throw IllegalArgumentException(
                                    "An account with this email already exists. Please log in."
                                )
                            }
                        }
                    }
                }
                conn.prepareStatement(
                    sql.query("user.upsert_app_user")
                ).use { stmt ->
                    stmt.setString(1, uid)
                    stmt.setString(2, email)
                    stmt.setString(3, normalizedEmail)
                    stmt.setString(4, displayName)
                    stmt.setInstant(5, lastLoginAt)
                    stmt.setInstant(6, lastLoginAt)
                    stmt.executeUpdate()
                }
        }
    }

    override suspend fun upsertProfile(uid: String, email: String, displayName: String?, lastSeenAt: AppTimestamp) {
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isBlank()) return

        val fallbackName = normalizedEmail.substringBefore("@").ifBlank { normalizedEmail }
        transactionProvider.write { conn ->
                conn.prepareStatement(
                    sql.query("user.upsert_profile")
                ).use { stmt ->
                    stmt.setString(1, uid)
                    stmt.setString(2, displayName ?: fallbackName)
                    stmt.setString(3, email)
                    stmt.setString(4, normalizedEmail)
                    stmt.setInstant(5, lastSeenAt)
                    stmt.setInstant(6, lastSeenAt)
                    stmt.setInstant(7, lastSeenAt)
                    stmt.executeUpdate()
                }
        }
    }

    override suspend fun getUser(uid: String): AppUserRecord? {
        return transactionProvider.read { conn ->
                conn.prepareStatement(
                    sql.query("user.select_user_by_uid")
                ).use { stmt ->
                    stmt.setString(1, uid)
                    stmt.executeQuery().use { rs ->
                        if (!rs.next()) return@read null
                        AppUserRecord(
                            uid = rs.getString("uid"),
                            email = rs.getString("email"),
                            displayName = rs.getString("display_name"),
                            createdAt = rs.getAppTimestamp("created_at"),
                            lastLoginAt = rs.getAppTimestamp("last_login_at"),
                        )
                    }
                }
        }
    }

    override suspend fun updateDisplayName(uid: String, displayName: String, updatedAt: AppTimestamp): Boolean {
        getUser(uid) ?: return false
        transactionProvider.write { conn ->
                conn.prepareStatement(
                    sql.query("user.update_app_user_display_name")
                ).use { stmt ->
                    stmt.setString(1, displayName)
                    stmt.setInstant(2, updatedAt)
                    stmt.setString(3, uid)
                    stmt.executeUpdate()
                }
                conn.prepareStatement(
                    sql.query("user.update_profile_display_name")
                ).use { stmt ->
                    stmt.setString(1, displayName)
                    stmt.setInstant(2, updatedAt)
                    stmt.setInstant(3, updatedAt)
                    stmt.setString(4, uid)
                    stmt.executeUpdate()
                }
        }
        return true
    }

    override suspend fun deleteUserAccount(uid: String): Boolean {
        val existing = getUser(uid) ?: return false
        transactionProvider.write { conn ->
                conn.prepareStatement(sql.query("user.delete_app_user")).use { stmt ->
                    stmt.setString(1, uid)
                    stmt.executeUpdate()
                }
                conn.prepareStatement(sql.query("user.delete_profile")).use { stmt ->
                    stmt.setString(1, uid)
                    stmt.executeUpdate()
                }
        }
        return existing.uid == uid
    }
}

package com.appforge.server.services.reviews.repository

import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.infrastructure.Resource
import com.appforge.server.infrastructure.sql.NamedSql
import com.appforge.server.infrastructure.time.setInstant
import com.appforge.server.providers.transaction.SqlTransactionProvider
import com.appforge.server.providers.transaction.TransactionProvider
import com.appforge.server.services.reviews.models.Profile
import java.time.Clock
import com.appforge.server.infrastructure.time.*

interface ProfileRepositoryApi {
    suspend fun upsert(profile: Profile): Resource<String>
    suspend fun get(userId: String): Resource<Profile>
    suspend fun getByEmail(email: String): Resource<Profile?>
}

class ProfileRepository(
    sqlDatabase: ExposedDatabase,
    private val clock: Clock = Clock.systemUTC(),
) : ProfileRepositoryApi {
    private val sql = NamedSql.fromResource(
        resourcePath = "com/appforge/server/services/reviews/reviews.sql",
        classLoader = ProfileRepository::class.java.classLoader,
    )
    private val transactionProvider: TransactionProvider = SqlTransactionProvider(sqlDatabase)

    override suspend fun upsert(profile: Profile): Resource<String> {
        return try {
            transactionProvider.write { conn ->
                conn.prepareStatement(
                    sql.query("reviews.upsert_profile")
                ).use { stmt ->
                    stmt.setString(1, profile.userId)
                    stmt.setString(2, profile.displayName)
                    stmt.setString(3, profile.email)
                    stmt.setString(4, profile.emailNormalized)
                    stmt.setInstant(5, profile.lastSeenAt)
                    stmt.executeUpdate()
                }
            }
            Resource.Success(profile.userId)
        } catch (e: Exception) {
            Resource.Error(e)
        }
    }

    override suspend fun get(userId: String): Resource<Profile> {
        return try {
            val profile = transactionProvider.read { conn ->
                conn.prepareStatement(
                    sql.query("reviews.select_profile_by_id")
                ).use { stmt ->
                    stmt.setString(1, userId)
                    stmt.executeQuery().use { rs ->
                        if (!rs.next()) {
                            null
                        } else {
                            Profile(
                                userId = rs.getString("user_id"),
                                displayName = rs.getString("display_name")
                                    ?: error("profiles.display_name is null"),
                                email = rs.getString("email"),
                                emailNormalized = rs.getString("email_normalized"),
                                lastSeenAt = rs.getAppTimestamp("last_seen_at")
                                    ?: error("profiles.last_seen_at is null"),
                            )
                        }
                    }
                }
            }
            if (profile == null) Resource.Error(Exception("Document not found: profiles/$userId")) else Resource.Success(profile)
        } catch (e: Exception) {
            Resource.Error(e)
        }
    }

    override suspend fun getByEmail(email: String): Resource<Profile?> {
        return try {
            val normalized = email.lowercase().trim()
            val profile = transactionProvider.read { conn ->
                conn.prepareStatement(
                    sql.query("reviews.select_profile_by_email")
                ).use { stmt ->
                    stmt.setString(1, normalized)
                    stmt.executeQuery().use { rs ->
                        if (!rs.next()) {
                            null
                        } else {
                            Profile(
                                userId = rs.getString("user_id"),
                                displayName = rs.getString("display_name")
                                    ?: error("profiles.display_name is null"),
                                email = rs.getString("email"),
                                emailNormalized = rs.getString("email_normalized"),
                                lastSeenAt = rs.getAppTimestamp("last_seen_at")
                                    ?: error("profiles.last_seen_at is null"),
                            )
                        }
                    }
                }
            }
            Resource.Success(profile)
        } catch (e: Exception) {
            Resource.Error(e)
        }
    }
}

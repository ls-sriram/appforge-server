package com.appforge.server.services.earlyaccess.repository

import com.appforge.server.infrastructure.ExposedDatabase
import com.appforge.server.infrastructure.sql.NamedSql
import com.appforge.server.providers.time.TimestampProvider
import com.appforge.server.providers.time.UtcTimestampProvider
import org.slf4j.LoggerFactory
import java.sql.Timestamp

class EarlyAccessRepository(
    private val sqlDatabase: ExposedDatabase,
    private val timestampProvider: TimestampProvider = UtcTimestampProvider,
) {
    private val logger = LoggerFactory.getLogger(EarlyAccessRepository::class.java)
    private val sql = NamedSql.fromResource(
        resourcePath = "com/appforge/server/services/auth/auth.sql",
        classLoader = EarlyAccessRepository::class.java.classLoader,
    )

    /**
     * Checks if an email is approved for early access.
     */
    suspend fun isEmailApproved(email: String): Boolean {
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isBlank()) return false

        return try {
            run {
                val status = sqlDatabase.withConnection { conn ->
                    conn.prepareStatement(
                        sql.query("early_access.select_status_by_email")
                    ).use { stmt ->
                        stmt.setString(1, normalizedEmail)
                        stmt.executeQuery().use { rs -> if (rs.next()) rs.getString("status") else null }
                    }
                }
                val isApproved = status == "approved"
                logger.info("Early access check for {}: {}", normalizedEmail, if (isApproved) "APPROVED" else "PENDING ($status)")
                return isApproved
            }
        } catch (e: Exception) {
            logger.error("Error checking early access for $normalizedEmail", e)
            false
        }
    }

    /**
     * Adds an email to the waitlist.
     */
    suspend fun joinWaitlist(email: String): Boolean {
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isBlank()) return false

        return try {
            run {
                val exists = sqlDatabase.withConnection { conn ->
                    conn.prepareStatement(
                        sql.query("early_access.exists_email")
                    ).use { stmt ->
                        stmt.setString(1, normalizedEmail)
                        stmt.executeQuery().use { rs -> rs.next() }
                    }
                }
                if (exists) {
                    logger.info("Email {} already exists in early access system. Skipping write.", normalizedEmail)
                    return true
                }

                sqlDatabase.withConnection { conn ->
                    conn.prepareStatement(
                        sql.query("early_access.insert_waitlist")
                    ).use { stmt ->
                        stmt.setString(1, normalizedEmail)
                        stmt.executeUpdate()
                    }
                }
                logger.info("Successfully added {} to the waitlist", normalizedEmail)
                return true
            }
        } catch (e: Exception) {
            logger.error("Error joining waitlist for $normalizedEmail", e)
            false
        }
    }

    data class ApprovalResult(
        val normalizedEmail: String,
        val previousStatus: String?,
        val wasCreated: Boolean,
        val wasUpdated: Boolean
    )

    /**
     * Approves an email for early access, creating the entry if it doesn't exist.
     */
    suspend fun approveAccess(email: String): ApprovalResult? {
        val normalizedEmail = email.trim().lowercase()
        if (normalizedEmail.isBlank()) return null

        return try {
            run {
                val previousStatus = sqlDatabase.withConnection { conn ->
                    conn.prepareStatement(
                        sql.query("early_access.select_status_by_email")
                    ).use { stmt ->
                        stmt.setString(1, normalizedEmail)
                        stmt.executeQuery().use { rs -> if (rs.next()) rs.getString("status") else null }
                    }
                }
                val wasCreated = previousStatus == null
                val wasUpdated = previousStatus != "approved"
                val approvedAt = if (wasUpdated) timestampProvider.now() else null

                sqlDatabase.withConnection { conn ->
                    conn.prepareStatement(
                        sql.query("early_access.upsert_approval")
                    ).use { stmt ->
                        stmt.setString(1, normalizedEmail)
                        if (approvedAt == null) {
                            stmt.setNull(2, java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                        } else {
                            stmt.setTimestamp(2, Timestamp.from(approvedAt))
                        }
                        stmt.executeUpdate()
                    }
                }
                logger.info(
                    "Approved early access for {} (previous status: {}, created: {})",
                    normalizedEmail,
                    previousStatus ?: "none",
                    wasCreated
                )
                return ApprovalResult(
                    normalizedEmail = normalizedEmail,
                    previousStatus = previousStatus,
                    wasCreated = wasCreated,
                    wasUpdated = wasUpdated
                )
            }
        } catch (e: Exception) {
            logger.error("Error approving early access for $normalizedEmail", e)
            null
        }
    }
}

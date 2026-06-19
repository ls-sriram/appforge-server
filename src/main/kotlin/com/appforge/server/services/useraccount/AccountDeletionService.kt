package com.appforge.server.services.useraccount

import com.appforge.server.providers.time.TimestampProvider
import com.appforge.server.providers.time.UtcTimestampProvider
import com.appforge.server.services.auth.repository.UserRepositoryApi
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import java.util.UUID
import org.slf4j.LoggerFactory

data class AccountDeletionResult(
    val success: Boolean,
)

class AccountDeletionService(
    private val userRepository: UserRepositoryApi,
    private val firebaseAuth: FirebaseAuth,
    private val auditRepository: AccountDeletionAuditRepositoryApi,
    private val timestampProvider: TimestampProvider = UtcTimestampProvider,
) {
    private val logger = LoggerFactory.getLogger(AccountDeletionService::class.java)

    suspend fun deleteAccount(userId: String): AccountDeletionResult {
        val now = timestampProvider.now()
        audit("account_delete_requested", "ok", userId, null)

        val sqlDeleted = userRepository.deleteUserAccount(userId)
        audit(
            event = "account_delete_sql",
            status = if (sqlDeleted) "ok" else "not_found",
            userId = userId,
            detail = null,
        )

        val firebaseStatus = deleteFirebaseUser(userId)
        audit(
            event = "account_delete_firebase",
            status = firebaseStatus.first,
            userId = userId,
            detail = firebaseStatus.second,
        )

        val success = firebaseStatus.first == "ok" || firebaseStatus.first == "not_found"
        if (success) {
            audit("account_delete_completed", "ok", userId, null)
            return AccountDeletionResult(success = true)
        }

        logger.warn(
            "Account deletion partial failure for user {}: firebaseStatus={} detail={}",
            userId,
            firebaseStatus.first,
            firebaseStatus.second,
        )
        audit("account_delete_completed", "error", userId, firebaseStatus.second)
        return AccountDeletionResult(success = false)
    }

    private suspend fun audit(event: String, status: String, userId: String, detail: String?) {
        auditRepository.record(
            AccountDeletionAuditRecord(
                id = UUID.randomUUID().toString(),
                userId = userId,
                event = event,
                status = status,
                detail = detail,
                createdAt = timestampProvider.now(),
            )
        )
    }

    private fun deleteFirebaseUser(userId: String): Pair<String, String?> {
        return try {
            firebaseAuth.revokeRefreshTokens(userId)
            firebaseAuth.deleteUser(userId)
            "ok" to null
        } catch (error: FirebaseAuthException) {
            val code = error.authErrorCode?.name ?: "UNKNOWN"
            if (code == "USER_NOT_FOUND") {
                "not_found" to code
            } else {
                "error" to code
            }
        } catch (error: Exception) {
            "error" to (error.message ?: error::class.java.simpleName)
        }
    }
}
